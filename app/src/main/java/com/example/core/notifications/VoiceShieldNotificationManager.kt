package com.example.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.database.VoiceMessageEntity
import com.example.domain.model.RecordingStatus
import java.io.File

/**
 * Manages Android Notifications for VoiceShield AI.
 * Strictly enforces:
 * - Title: "New Voice Message"
 * - Body: "Someone left a message from +91XXXXXXXXXX"
 * - Actions: Listen, Open message, Delete (with confirmation)
 * - NEVER sends a successful-message notification when no actual recording file exists.
 */
class VoiceShieldNotificationManager(
    private val context: Context
) {
    companion object {
        const val CHANNEL_VOICE_MESSAGES = "voiceshield_voice_messages_channel"
        const val CHANNEL_CALL_SCREENING = "voiceshield_call_screening_channel"

        const val ACTION_LISTEN_MESSAGE = "com.example.voiceshield.ACTION_LISTEN_MESSAGE"
        const val ACTION_OPEN_MESSAGE = "com.example.voiceshield.ACTION_OPEN_MESSAGE"
        const val ACTION_DELETE_MESSAGE = "com.example.voiceshield.ACTION_DELETE_MESSAGE"

        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_AUTO_PLAY = "extra_auto_play"
        const val EXTRA_CONFIRM_DELETE = "extra_confirm_delete"
    }

    init {
        createNotificationChannels()
    }

    fun createNotificationChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val messagesChannel = NotificationChannel(
            CHANNEL_VOICE_MESSAGES,
            context.getString(R.string.notification_channel_messages_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_messages_desc)
            enableVibration(true)
        }

        val screeningChannel = NotificationChannel(
            CHANNEL_CALL_SCREENING,
            context.getString(R.string.notification_channel_screening_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_screening_desc)
        }

        manager.createNotificationChannel(messagesChannel)
        manager.createNotificationChannel(screeningChannel)
    }

    /**
     * Validates whether a message qualifies for a "New Voice Message" notification.
     * Returns false if no actual encrypted recording file exists on disk.
     */
    fun canSendVoiceMessageNotification(
        message: VoiceMessageEntity,
        notificationsEnabledInSettings: Boolean
    ): Boolean {
        if (!notificationsEnabledInSettings) return false
        if (message.recordingStatus != RecordingStatus.RECORDED_ENCRYPTED.name) return false
        val path = message.encryptedFilePath ?: return false
        val file = File(path)
        return file.exists() && file.length() > 16
    }

    /**
     * Builds and dispatches the "New Voice Message" notification with Listen, Open, and Delete actions.
     */
    fun sendNewVoiceMessageNotification(
        message: VoiceMessageEntity,
        notificationsEnabledInSettings: Boolean
    ): Boolean {
        if (!canSendVoiceMessageNotification(message, notificationsEnabledInSettings)) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }

        val callerDisplay = message.callerNumber.ifBlank { "+91Unknown" }
        val title = "New Voice Message"
        val body = "Someone left a message from $callerDisplay"

        val listenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MESSAGE_ID, message.id)
            putExtra(EXTRA_AUTO_PLAY, true)
        }
        val listenPendingIntent = PendingIntent.getActivity(
            context,
            message.id.hashCode() + 1,
            listenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MESSAGE_ID, message.id)
            putExtra(EXTRA_AUTO_PLAY, false)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            message.id.hashCode() + 2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MESSAGE_ID, message.id)
            putExtra(EXTRA_CONFIRM_DELETE, true)
        }
        val deletePendingIntent = PendingIntent.getActivity(
            context,
            message.id.hashCode() + 3,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_VOICE_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_voicemail)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append(body)
                        if (!message.transcription.isNullOrBlank()) {
                            append("\n")
                            append(message.transcription)
                        }
                    }
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_media_play,
                context.getString(R.string.notification_action_listen),
                listenPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                context.getString(R.string.notification_action_open),
                openPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_delete,
                context.getString(R.string.notification_action_delete),
                deletePendingIntent
            )
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(message.id.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(VoiceShieldNotificationManager.EXTRA_MESSAGE_ID) ?: return
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(VoiceShieldNotificationManager.EXTRA_MESSAGE_ID, messageId)
            when (intent.action) {
                VoiceShieldNotificationManager.ACTION_LISTEN_MESSAGE ->
                    putExtra(VoiceShieldNotificationManager.EXTRA_AUTO_PLAY, true)
                VoiceShieldNotificationManager.ACTION_DELETE_MESSAGE ->
                    putExtra(VoiceShieldNotificationManager.EXTRA_CONFIRM_DELETE, true)
            }
        }
        context.startActivity(launchIntent)
    }
}

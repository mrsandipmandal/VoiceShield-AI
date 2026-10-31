package com.example.telecom.sip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Service keeping the SIP User Agent listener and active AI Voice Receptionist
 * RTP session alive reliably during operation.
 *
 * Uses `FOREGROUND_SERVICE_TYPE_DATA_SYNC` and calls `promoteToForeground` in both
 * `onCreate()` and `onStartCommand()` so Android 12–16 (`targetSdk = 36`) never throws
 * `ForegroundServiceDidNotStartInTimeException` or permission-gated FGS `SecurityException`.
 */
class SipReceptionistForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "voiceshield_sip_service_channel"
        private const val NOTIFICATION_ID = 4102

        @Volatile
        private var isRunning = false

        fun startServiceIfNeeded(context: Context) {
            if (isRunning) return
            try {
                val intent = Intent(context, SipReceptionistForegroundService::class.java)
                // Use startService so Android does not arm the strict 5s startForegroundService crash timer
                context.startService(intent)
            } catch (_: Exception) {
                // In background-restricted or Robolectric test contexts, SipUserAgentClient still runs in process
            }
        }

        fun stopService(context: Context) {
            try {
                isRunning = false
                context.stopService(Intent(context, SipReceptionistForegroundService::class.java))
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        promoteToForeground("SIP AI Receptionist Active")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        createChannel()
        promoteToForeground("SIP AI Receptionist Ready for Incoming Calls")
        return START_STICKY
    }

    private fun promoteToForeground(statusText: String) {
        val notification = buildForegroundNotification(statusText)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VoiceShield SIP AI Receptionist",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains local SIP/VoIP registration and automatic AI receptionist readiness"
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("VoiceShield AI — Automatic Receptionist")
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}

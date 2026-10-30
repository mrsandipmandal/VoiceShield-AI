package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.notifications.VoiceShieldNotificationManager
import com.example.domain.model.CallerRuleType
import com.example.feature.callscreening.CallProtectionScreen
import com.example.feature.callscreening.CallerRulesScreen
import com.example.feature.callscreening.SpamCallHistoryScreen
import com.example.feature.common.VoiceShieldScreen
import com.example.feature.common.VoiceShieldViewModel
import com.example.feature.dashboard.DashboardScreen
import com.example.feature.messages.VoiceMessageInboxScreen
import com.example.feature.receptionist.VoiceReceptionistScreen
import com.example.feature.settings.PermissionsAndCompatibilityScreen
import com.example.feature.settings.PrivacyAndDataScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.voicestudio.ModelManagerScreen
import com.example.feature.voicestudio.TtsVoiceStudioScreen
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.VoiceShieldTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VoiceShieldViewModel by viewModels {
        val app = application as VoiceShieldApplication
        VoiceShieldViewModel.Factory(app.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val settings by viewModel.settingsState.collectAsStateWithLifecycle()
            VoiceShieldTheme(darkTheme = settings.darkThemeEnabled) {
                VoiceShieldMainApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDeviceCapabilities()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val messageId = intent.getStringExtra(VoiceShieldNotificationManager.EXTRA_MESSAGE_ID)
        val autoPlay = intent.getBooleanExtra(VoiceShieldNotificationManager.EXTRA_AUTO_PLAY, false)
        val confirmDelete = intent.getBooleanExtra(VoiceShieldNotificationManager.EXTRA_CONFIRM_DELETE, false)
        if (!messageId.isNullOrBlank()) {
            viewModel.handleNotificationDeepLink(
                messageId = messageId,
                autoPlay = autoPlay,
                confirmDelete = confirmDelete
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceShieldMainApp(viewModel: VoiceShieldViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val messages by viewModel.messagesState.collectAsStateWithLifecycle()
    val screenedCalls by viewModel.screenedCallsState.collectAsStateWithLifecycle()
    val callerRules by viewModel.callerRulesState.collectAsStateWithLifecycle()
    val greetingScripts by viewModel.greetingScriptsState.collectAsStateWithLifecycle()
    val voiceModels by viewModel.voiceModelsState.collectAsStateWithLifecycle()
    val receptionistSession by viewModel.receptionistSessionState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilitiesState.collectAsStateWithLifecycle()
    val activeTelecomCall by viewModel.activeTelecomCall.collectAsState()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val highlightedMsgId by viewModel.highlightedMessageId.collectAsStateWithLifecycle()
    val pendingDeleteMsgId by viewModel.pendingDeleteConfirmationMessageId.collectAsStateWithLifecycle()

    var showAllScreensSheet by remember { mutableStateOf(false) }

    // BackHandler for all secondary screens to return cleanly to Dashboard
    BackHandler(enabled = currentScreen != VoiceShieldScreen.DASHBOARD) {
        viewModel.navigateTo(VoiceShieldScreen.DASHBOARD)
    }

    val unreadCount = messages.count { !it.isRead }

    if (showAllScreensSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAllScreensSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "All 12 VoiceShield AI Screens",
                    style = MaterialTheme.typography.headlineMedium,
                    color = EmeraldShield,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    items(VoiceShieldScreen.entries) { screen ->
                        val isCurrent = screen == currentScreen
                        Surface(
                            color = if (isCurrent) EmeraldShield.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.navigateTo(screen)
                                    showAllScreensSheet = false
                                }
                                .testTag("drawer_item_${screen.route}")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = screen.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isCurrent) EmeraldShield else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = screen.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isCurrent) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EmeraldShield
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = EmeraldShield,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = currentScreen.title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentScreen.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (currentScreen != VoiceShieldScreen.DASHBOARD) {
                        IconButton(
                            onClick = { viewModel.navigateTo(VoiceShieldScreen.DASHBOARD) },
                            modifier = Modifier.testTag("top_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Dashboard"
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { showAllScreensSheet = true },
                            modifier = Modifier.testTag("open_all_screens_drawer_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open All 12 Screens Menu"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.updateSettings { it.copy(darkThemeEnabled = !it.darkThemeEnabled) }
                        },
                        modifier = Modifier.testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (settings.darkThemeEnabled) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Dark/Light Theme"
                        )
                    }
                    IconButton(
                        onClick = { showAllScreensSheet = true },
                        modifier = Modifier.testTag("top_menu_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "All 12 Screens"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentScreen == VoiceShieldScreen.DASHBOARD,
                    onClick = { viewModel.navigateTo(VoiceShieldScreen.DASHBOARD) },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    modifier = Modifier.testTag("bottom_nav_dashboard")
                )
                NavigationBarItem(
                    selected = currentScreen == VoiceShieldScreen.VOICE_RECEPTIONIST,
                    onClick = { viewModel.navigateTo(VoiceShieldScreen.VOICE_RECEPTIONIST) },
                    icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = "Receptionist") },
                    label = { Text("Receptionist") },
                    modifier = Modifier.testTag("bottom_nav_receptionist")
                )
                NavigationBarItem(
                    selected = currentScreen == VoiceShieldScreen.TTS_VOICE_STUDIO,
                    onClick = { viewModel.navigateTo(VoiceShieldScreen.TTS_VOICE_STUDIO) },
                    icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Voice Studio") },
                    label = { Text("Studio") },
                    modifier = Modifier.testTag("bottom_nav_studio")
                )
                NavigationBarItem(
                    selected = currentScreen == VoiceShieldScreen.VOICE_MESSAGE_INBOX,
                    onClick = { viewModel.navigateTo(VoiceShieldScreen.VOICE_MESSAGE_INBOX) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge { Text(unreadCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Voicemail, contentDescription = "Voice Messages")
                        }
                    },
                    label = { Text("Messages") },
                    modifier = Modifier.testTag("bottom_nav_messages")
                )
                NavigationBarItem(
                    selected = currentScreen == VoiceShieldScreen.SETTINGS,
                    onClick = { viewModel.navigateTo(VoiceShieldScreen.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("bottom_nav_settings")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = !statusBanner.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyanTelemetry.copy(alpha = 0.18f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusBanner.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearStatusBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentScreen) {
                    VoiceShieldScreen.DASHBOARD -> DashboardScreen(
                        settings = settings,
                        screenedCalls = screenedCalls,
                        messages = messages,
                        voiceModels = voiceModels,
                        capabilities = capabilities,
                        onToggleProtection = { enabled ->
                            viewModel.updateSettings { it.copy(protectionEnabled = enabled) }
                        },
                        onNavigate = { viewModel.navigateTo(it) },
                        onQuickSimulateCall = {
                            viewModel.navigateTo(VoiceShieldScreen.VOICE_RECEPTIONIST)
                            viewModel.triggerSimulatedIncomingCall(
                                callerNumber = "+919831099887",
                                callerName = null,
                                simulateConsent = true,
                                callerSpeechHint = "নমস্কার, আমি কুরিয়ার সার্ভিস থেকে বলছি। আপনার একটি রেজিস্টার্ড পার্সেল এসেছে।"
                            )
                        },
                        onPlayMessage = { msg ->
                            viewModel.navigateTo(VoiceShieldScreen.VOICE_MESSAGE_INBOX)
                            viewModel.playOrPauseMessage(msg)
                        }
                    )

                    VoiceShieldScreen.CALL_PROTECTION -> CallProtectionScreen(
                        settings = settings,
                        capabilities = capabilities,
                        activeCall = activeTelecomCall,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onRefreshCapabilities = { viewModel.refreshDeviceCapabilities() },
                        onShowBanner = { viewModel.showStatusBanner(it) }
                    )

                    VoiceShieldScreen.VOICE_RECEPTIONIST -> VoiceReceptionistScreen(
                        settings = settings,
                        scripts = greetingScripts,
                        sessionState = receptionistSession,
                        isTtsSpeaking = isTtsSpeaking,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onSaveScript = { viewModel.saveGreetingScript(it) },
                        onDeleteScript = { viewModel.deleteGreetingScript(it) },
                        onPreviewTts = { viewModel.previewTts(it) },
                        onStopTts = { viewModel.stopTtsPreview() },
                        onTriggerSimulatedCall = { num, name, consent, hint ->
                            viewModel.triggerSimulatedIncomingCall(num, name, consent, hint)
                        },
                        onFinishRecordingEarly = { viewModel.finishActiveRecordingEarly() },
                        onCancelSession = { viewModel.stopReceptionistSession() }
                    )

                    VoiceShieldScreen.TTS_VOICE_STUDIO -> TtsVoiceStudioScreen(
                        settings = settings,
                        voiceModels = voiceModels,
                        isSpeaking = isTtsSpeaking,
                        onSelectMood = { viewModel.applyVoiceMoodPreset(it) },
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onPreviewVoice = { viewModel.previewTts(it) },
                        onStopPreview = { viewModel.stopTtsPreview() }
                    )

                    VoiceShieldScreen.VOICE_MESSAGE_INBOX -> VoiceMessageInboxScreen(
                        messages = messages,
                        playbackState = playbackState,
                        highlightedMessageId = highlightedMsgId,
                        pendingDeleteConfirmMessageId = pendingDeleteMsgId,
                        onPlayOrPause = { viewModel.playOrPauseMessage(it) },
                        onSeek = { msg, frac -> viewModel.seekMessagePlayback(msg, frac) },
                        onChangeSpeed = { viewModel.changePlaybackSpeed(it) },
                        onToggleImportant = { viewModel.toggleMessageImportant(it) },
                        onTranscribeOffline = { viewModel.transcribeMessageOffline(it) },
                        onExportAudio = { viewModel.exportMessageAudio(it) },
                        onDeleteMessage = { viewModel.deleteMessage(it) },
                        onDeleteAllMessages = { viewModel.deleteAllMessages() },
                        onDismissDeleteConfirmation = { viewModel.dismissDeleteConfirmation() }
                    )

                    VoiceShieldScreen.SPAM_CALL_HISTORY -> SpamCallHistoryScreen(
                        screenedCalls = screenedCalls,
                        onAllowNumber = { num, name ->
                            viewModel.addCallerRule(num, name, CallerRuleType.ALLOWLIST, "Approved from Call History", 0)
                        },
                        onBlockNumber = { num, notes ->
                            viewModel.addCallerRule(num, "Blocked Caller", CallerRuleType.BLOCKLIST, notes, 100)
                        },
                        onClearHistory = { viewModel.clearScreeningHistory() }
                    )

                    VoiceShieldScreen.CONTACTS_ALLOWLIST -> CallerRulesScreen(
                        isAllowlistMode = true,
                        rules = callerRules,
                        onAddRule = { num, name, type, notes, weight ->
                            viewModel.addCallerRule(num, name, type, notes, weight)
                        },
                        onDeleteRule = { viewModel.deleteCallerRule(it) }
                    )

                    VoiceShieldScreen.BLOCKLIST -> CallerRulesScreen(
                        isAllowlistMode = false,
                        rules = callerRules,
                        onAddRule = { num, name, type, notes, weight ->
                            viewModel.addCallerRule(num, name, type, notes, weight)
                        },
                        onDeleteRule = { viewModel.deleteCallerRule(it) }
                    )

                    VoiceShieldScreen.MODEL_MANAGER -> ModelManagerScreen(
                        models = voiceModels,
                        activeModelId = settings.voiceModel,
                        onDownloadAndVerify = { viewModel.downloadAndVerifyModel(it) },
                        onActivateModel = { viewModel.activateVoiceModel(it) },
                        onDeleteModel = { viewModel.deleteVoiceModelFile(it) },
                        onImportCustomModel = { name, lang -> viewModel.importCustomVoiceModel(name, lang) },
                        onPreviewModel = { model ->
                            viewModel.previewTts(settings.greetingText)
                        }
                    )

                    VoiceShieldScreen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onResetDefaults = { viewModel.resetSettingsToDefaults() }
                    )

                    VoiceShieldScreen.PERMISSIONS_COMPATIBILITY -> PermissionsAndCompatibilityScreen(
                        capabilities = capabilities,
                        sipArchitectureText = viewModel.repository.capabilityManager.getSipVoipArchitectureExplanation(),
                        onRefreshCapabilities = { viewModel.refreshDeviceCapabilities() }
                    )

                    VoiceShieldScreen.PRIVACY_DATA -> PrivacyAndDataScreen(
                        settings = settings,
                        totalMessagesCount = messages.size,
                        totalScreenedCallsCount = screenedCalls.size,
                        onRunRetentionCleanup = { viewModel.runExpiredRetentionCleanup() },
                        onDeleteAllRecordings = { viewModel.deleteAllMessages() },
                        onClearCallHistory = { viewModel.clearScreeningHistory() }
                    )
                }
            }
        }
    }
}

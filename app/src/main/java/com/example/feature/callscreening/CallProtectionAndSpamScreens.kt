package com.example.feature.callscreening

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.telecom.Call
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.database.CallerRuleEntity
import com.example.core.database.ScreenedCallEntity
import com.example.domain.model.CallerRuleType
import com.example.domain.model.CapabilitySupportStatus
import com.example.domain.model.DeviceAudioCapabilities
import com.example.domain.model.FallbackCallAction
import com.example.domain.model.SpamClassification
import com.example.domain.model.UnknownNumberAction
import com.example.domain.model.VoiceShieldSettings
import com.example.feature.common.CapabilityStatusBadge
import com.example.feature.common.SectionHeader
import com.example.telecom.incall.ActiveTelecomCallState
import com.example.telecom.incall.VoiceShieldInCallService
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonSpam
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldShield
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen 2: Call Protection + RoleManager (Call Screening & Default Dialer) + Integrated Dial Pad & InCall UI.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallProtectionScreen(
    settings: VoiceShieldSettings,
    capabilities: DeviceAudioCapabilities,
    activeCall: ActiveTelecomCallState,
    onUpdateSettings: ((VoiceShieldSettings) -> VoiceShieldSettings) -> Unit,
    onRefreshCapabilities: () -> Unit,
    onShowBanner: (String) -> Unit
) {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    var dialPadNumber by remember { mutableStateOf("") }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshCapabilities()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("call_protection_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active In-Call UI Banner (shown when Default Dialer InCallService has a ringing/ongoing call)
        if (activeCall.hasActiveCall) {
            InCallControlCard(activeCall = activeCall)
        }

        SectionHeader(
            title = "Android Telecom Roles",
            subtitle = "Request official CallScreeningService and optional Default Dialer roles"
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Call Screening Service Role", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Screens unknown numbers for spam within Android's 5s deadline without replacing your dialer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CapabilityStatusBadge(status = capabilities.callScreeningSupported)
                }

                Button(
                    onClick = {
                        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                            roleLauncher.launch(intent)
                        } else {
                            onShowBanner("RoleManager.ROLE_CALL_SCREENING request dispatched.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("request_call_screening_role_btn")
                ) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (capabilities.isDefaultCallScreener) "Call Screening Role Active" else "Request Call Screening Role")
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Optional Default Dialer Role", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Enables InCallService UI, incoming/ongoing call controls, and programmatic call answer/disconnect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CapabilityStatusBadge(status = capabilities.callAnswerDisconnectSupported)
                }

                OutlinedButton(
                    onClick = {
                        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                            roleLauncher.launch(intent)
                        } else {
                            onShowBanner("RoleManager.ROLE_DIALER request dispatched.")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("request_dialer_role_btn")
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (capabilities.isDefaultDialer) "Default Phone App Active" else "Request Default Phone App Role")
                }
            }
        }

        // Unknown Caller Policy & Spam Threshold
        SectionHeader(
            title = "Unknown Caller Policy",
            subtitle = "Choose how unknown numbers below the spam threshold are handled"
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnknownNumberAction.entries.forEach { action ->
                        FilterChip(
                            selected = settings.unknownNumberAction == action,
                            onClick = { onUpdateSettings { it.copy(unknownNumberAction = action) } },
                            label = { Text(action.displayName) },
                            modifier = Modifier.testTag("unknown_action_${action.name}")
                        )
                    }
                }
                Text(
                    text = settings.unknownNumberAction.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = EmeraldShield
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bypass Receptionist for Saved Contacts", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Always allow numbers saved in your phonebook to ring normally",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.knownContactsBypass,
                        onCheckedChange = { checked -> onUpdateSettings { it.copy(knownContactsBypass = checked) } }
                    )
                }

                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Spam Auto-Block Threshold", style = MaterialTheme.typography.titleSmall)
                        Text("${settings.spamThreshold} / 100", style = MaterialTheme.typography.labelLarge, color = CrimsonSpam)
                    }
                    Slider(
                        value = settings.spamThreshold.toFloat(),
                        onValueChange = { v -> onUpdateSettings { it.copy(spamThreshold = v.toInt()) } },
                        valueRange = 25f..95f
                    )
                    Text(
                        "Emergency numbers (112, 911, 100, 108) and Allowlisted contacts ALWAYS bypass screening.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text("Hardware Limitation Fallback Action", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FallbackCallAction.entries.forEach { fallback ->
                        FilterChip(
                            selected = settings.fallbackCallAction == fallback,
                            onClick = { onUpdateSettings { it.copy(fallbackCallAction = fallback) } },
                            label = { Text(fallback.displayName) }
                        )
                    }
                }
            }
        }

        // Built-In Default Dialer Dial Pad
        SectionHeader(
            title = "Built-In Dialer & Call Controls",
            subtitle = "Required dial pad for Default Phone Application role compliance"
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = dialPadNumber,
                    onValueChange = { dialPadNumber = it },
                    label = { Text("Enter Phone Number") },
                    placeholder = { Text("+91 98300 00000") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Dialpad, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialpad_input")
                )
                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#")
                )
                keypadRows.forEach { rowDigits ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowDigits.forEach { digit ->
                            OutlinedButton(
                                onClick = { dialPadNumber += digit },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(digit, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            if (dialPadNumber.isNotEmpty()) {
                                dialPadNumber = dialPadNumber.dropLast(1)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Backspace")
                    }
                    Button(
                        onClick = {
                            if (dialPadNumber.isNotBlank()) {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialPadNumber"))
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                        modifier = Modifier
                            .weight(2f)
                            .testTag("dialpad_call_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Place Call")
                        Spacer(Modifier.width(8.dp))
                        Text("Dial Number")
                    }
                }
            }
        }
    }
}

@Composable
fun InCallControlCard(activeCall: ActiveTelecomCallState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CrimsonSpam.copy(alpha = 0.14f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, EmeraldShield, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = activeCall.callStateLabel.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldShield
                    )
                    Text(
                        text = activeCall.callerNumber,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                if (activeCall.isEmergencyCall) {
                    Text("EMERGENCY CALL", color = CrimsonSpam, fontWeight = FontWeight.Bold)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (activeCall.callState == Call.STATE_RINGING) {
                    Button(
                        onClick = { VoiceShieldInCallService.answerActiveCall() },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Answer")
                        Spacer(Modifier.width(6.dp))
                        Text("Answer")
                    }
                }
                OutlinedButton(
                    onClick = { VoiceShieldInCallService.toggleMute(!activeCall.isMuted) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.MicOff, contentDescription = "Mute")
                    Spacer(Modifier.width(4.dp))
                    Text(if (activeCall.isMuted) "Unmute" else "Mute")
                }
                OutlinedButton(
                    onClick = { VoiceShieldInCallService.toggleSpeakerphone(!activeCall.isSpeakerphoneOn) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speaker")
                    Spacer(Modifier.width(4.dp))
                    Text(if (activeCall.isSpeakerphoneOn) "Earpiece" else "Speaker")
                }
                Button(
                    onClick = { VoiceShieldInCallService.disconnectActiveCall() },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonSpam),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End Call")
                    Spacer(Modifier.width(4.dp))
                    Text("End")
                }
            }
        }
    }
}

/**
 * Screen 6: Spam Call History — Screened calls, spam scores, STIR/SHAKEN attestation, and manual overrides.
 */
@Composable
fun SpamCallHistoryScreen(
    screenedCalls: List<ScreenedCallEntity>,
    onAllowNumber: (String, String) -> Unit,
    onBlockNumber: (String, String) -> Unit,
    onClearHistory: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("spam_history_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Screened & Spam Call Log",
            subtitle = "Real-time spam scores, STIR/SHAKEN verification, and manual overrides",
            trailingContent = {
                if (screenedCalls.isNotEmpty()) {
                    OutlinedButton(onClick = onClearHistory) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        )

        if (screenedCalls.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "No screened calls recorded yet. Incoming calls screened by CallScreeningService or the Simulator appear here.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(screenedCalls, key = { it.id }) { call ->
                    val scoreColor = when {
                        call.spamScore >= 65 -> CrimsonSpam
                        call.spamScore >= 40 -> AmberWarning
                        else -> EmeraldShield
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = call.contactName ?: call.callerNumber,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${call.callerNumber} • ${dateFormat.format(Date(call.timestampMillis))}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(scoreColor.copy(alpha = 0.16f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Spam ${call.spamScore}/100",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = scoreColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = call.reasonSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "STIR/SHAKEN: ${call.verificationStatus} • ${call.handlingStatus}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanTelemetry
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { onAllowNumber(call.callerNumber, call.contactName ?: "Manual Override") }
                                    ) {
                                        Text("Allow", style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(
                                        onClick = { onBlockNumber(call.callerNumber, "Manual Block from History") }
                                    ) {
                                        Text("Block", style = MaterialTheme.typography.labelSmall, color = CrimsonSpam)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 7 (Contacts & Allowlist) and Screen 8 (Blocklist & Reported Spam).
 */
@Composable
fun CallerRulesScreen(
    isAllowlistMode: Boolean,
    rules: List<CallerRuleEntity>,
    onAddRule: (String, String, CallerRuleType, String, Int) -> Unit,
    onDeleteRule: (Long) -> Unit
) {
    var phoneInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var isUserReportSpam by remember { mutableStateOf(false) }

    val filteredRules = remember(rules, isAllowlistMode) {
        if (isAllowlistMode) {
            rules.filter { it.ruleType == CallerRuleType.ALLOWLIST.name }
        } else {
            rules.filter {
                it.ruleType == CallerRuleType.BLOCKLIST.name ||
                    it.ruleType == CallerRuleType.USER_REPORTED_SPAM.name
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag(if (isAllowlistMode) "allowlist_screen" else "blocklist_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = if (isAllowlistMode) "Contacts & Allowlist" else "Blocklist & Reported Spam",
            subtitle = if (isAllowlistMode) {
                "Approved numbers always bypass spam screening and the AI receptionist"
            } else {
                "Blocked numbers are rejected immediately; reported spam boosts local spam scores"
            }
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+919830011223") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rule_phone_input")
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Label / Name") },
                        placeholder = { Text(if (isAllowlistMode) "Doctor / Family" else "Loan Telemarketer") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rule_name_input")
                    )
                }
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Reason / Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isAllowlistMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = !isUserReportSpam,
                            onClick = { isUserReportSpam = false },
                            label = { Text("Hard Block (Score 100)") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = isUserReportSpam,
                            onClick = { isUserReportSpam = true },
                            label = { Text("Report Spam Heuristic (+75)") }
                        )
                    }
                }
                Button(
                    onClick = {
                        if (phoneInput.isNotBlank()) {
                            val type = when {
                                isAllowlistMode -> CallerRuleType.ALLOWLIST
                                isUserReportSpam -> CallerRuleType.USER_REPORTED_SPAM
                                else -> CallerRuleType.BLOCKLIST
                            }
                            onAddRule(phoneInput, nameInput, type, notesInput, if (isUserReportSpam) 75 else 100)
                            phoneInput = ""
                            nameInput = ""
                            notesInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAllowlistMode) EmeraldShield else CrimsonSpam
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_rule_submit_button")
                ) {
                    Icon(
                        imageVector = if (isAllowlistMode) Icons.Default.PersonAdd else Icons.Default.Block,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isAllowlistMode) "Add to Allowlist" else "Add to Blocklist / Spam Database")
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredRules, key = { it.id }) { rule ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = when (rule.ruleType) {
                                    CallerRuleType.ALLOWLIST.name -> Icons.Default.VerifiedUser
                                    CallerRuleType.USER_REPORTED_SPAM.name -> Icons.Default.Report
                                    else -> Icons.Default.Block
                                },
                                contentDescription = null,
                                tint = if (isAllowlistMode) EmeraldShield else CrimsonSpam
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(rule.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${rule.phoneNumber} • ${rule.ruleType}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyanTelemetry
                                )
                                if (rule.notes.isNotBlank()) {
                                    Text(
                                        text = rule.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onDeleteRule(rule.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove Rule",
                                tint = CrimsonSpam
                            )
                        }
                    }
                }
            }
        }
    }
}

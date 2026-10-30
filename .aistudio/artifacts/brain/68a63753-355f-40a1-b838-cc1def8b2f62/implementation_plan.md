# VoiceShield AI — Offline AI Call Screening & Voice Receptionist

VoiceShield AI is a privacy-first, offline-capable Android call screening and automated voice receptionist application that shields users from spam and unknown callers while capturing legitimate caller messages with explicit disclosure, hardware capability transparency, and AES-GCM encryption at rest.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX decisions incorporate your confirmed preferences and Android platform security boundaries.

- **Confirmed Decision 1 — Hybrid Offline TTS Architecture**: Uses a local **Sherpa-ONNX Voice Model Manager** (supporting offline `.onnx` VITS/Piper model download, local file import, SHA-256 verification, and JNI/ONNX inference pipeline) paired with an **Android `TextToSpeech` Offline Engine Fallback** so Bengali (`bn-IN`), English (`en-IN`), and Hindi (`hi-IN`) greetings work immediately out-of-the-box before large `.onnx` models are imported.
- **Confirmed Decision 2 — Dual-Mode Telecom & Interactive Call Simulator**: Implements real Android `CallScreeningService`, `InCallService`, and `RoleManager` default dialer integration alongside an **Interactive Call & Receptionist Simulator** that lets users test screening policies, 12-mood TTS greeting playback, caller consent prompts, real microphone audio capture, silence detection, and encrypted recording on any Android device or emulator.
- **Confirmed Decision 3 — Cyber-Security Shield Material 3 Aesthetic**: Applies a high-contrast security-oriented theme featuring Deep Obsidian Navy surfaces, Emerald Shield accents for active protection and verified capabilities, Amber warnings for limited hardware routing, and Crimson alerts for high-risk spam and blocked calls, supporting both Dark and Light modes.
- **Platform Hardware Transparency**: Ordinary non-rooted Android phones restrict third-party apps from injecting TTS audio directly into the cellular uplink stream (`VOICE_CALL`) or recording the remote cellular downlink without OEM/system privileges. A dedicated `CallAudioCapabilityManager` inspects and reports each capability (`Supported`, `Limited`, `Unsupported`) honestly, never fakes a cellular recording, and documents the optional SIP/VoIP PBX bridge workflow.

---

## 1. Overview & Core Concept

- **What It Does**: Screens incoming phone calls in real time against local contacts, allowlists, blocklists, repeated-call heuristics, and STIR/SHAKEN verification status. When configured as the default dialer or tested via the built-in receptionist flow, it answers unknown calls after a configurable delay, plays a customizable multi-language TTS greeting with recording disclosure, records caller messages when legal and hardware-supported, encrypts audio via Android Keystore (`AES/GCM/NoPadding`), and triggers actionable rich notifications.
- **Target Audience / Persona**: Privacy-conscious Android users in India and worldwide who receive frequent unknown, delivery, or spam calls and want an offline, zero-cloud voice receptionist in Bengali, Hindi, or English.
- **Key Value**: 100% on-device processing with zero cloud uploads, complete transparency regarding Android cellular audio hardware constraints, expressive 12-mood greeting customization, and full user ownership of encrypted recordings and screening rules.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Dashboard & Live Protection Command Center**: Displays an interactive protection shield toggle, real-time counters (Total Screened, Spam Blocked, Unknown Handled, Saved Voice Messages), a preview card of the latest voice message, active local TTS model readiness, and a 5-point Device Call-Audio Compatibility badge strip, plus a quick-launch button for the **Interactive Call Receptionist Simulator** and full navigation across all 12 screens.
2. **Real-Time Call Screening & Receptionist Execution**:
   - When an incoming call arrives (via Android `CallScreeningService` or the Interactive Call Simulator), the engine checks Contacts -> Allowlist -> Blocklist -> Spam Heuristics -> Working Hours -> Unknown Number Action within Android's strict 5-second response deadline.
   - If routed to the **Voice Receptionist**, the call is answered after `answerDelaySeconds`, the selected greeting script (Unknown vs. Known, Short vs. Long, with mandatory recording disclosure) is synthesized using the selected Voice Mood preset, and caller consent is verified before recording begins.
   - Recording stops automatically upon reaching `maxRecordingDuration` or `silenceTimeout`, encrypts the audio payload using Android Keystore, stores metadata in Room, and dispatches a `New Voice Message` notification with `Listen`, `Open Message`, and `Delete` actions.
3. **TTS Voice Studio & 12-Mood Prosody Control**: Users select from 12 voice moods (*Professional, Polite, Friendly, Calm, Serious, Firm, Cheerful, Empathetic, Neutral, Urgent, Soft, Strict*), fine-tune speech speed, pitch, volume, pause duration, and sentence emphasis, clearly see whether emotional delivery is approximated via prosody controls or model tokens, and audition live offline TTS output.
4. **Encrypted Voice Message Inbox**: Users browse, search, and filter messages (All, Unread, Important, Spam, Unsupported Audio), decrypt and play back audio with live waveform/progress scrubbing and variable playback speed (`0.75x` – `2.0x`), run local offline transcription, export decrypted audio via explicit user action, or delete individual/all recordings.

### Visual Identity & Theme
- **Aesthetic Direction**: **Cyber-Security Shield** — authoritative, precision-engineered telecom telemetry interface with subtle radial shield gradients, layered tonal cards, and crisp status badges (`Supported`, `Limited`, `Unsupported`).
- **Color Palette & Mood**:
  - *Dark Posture (Default)*: Background `#070D19` (Obsidian Navy), Surface `#0F172A` (Slate Navy), Elevated Card `#1E293B`, Primary `#10B981` (Emerald Shield), Secondary `#06B6D4` (Cyan Telemetry), Warning/Limited `#F59E0B` (Amber Signal), Error/Spam `#EF4444` (Crimson Alert).
  - *Light Posture*: Background `#F8FAFC`, Surface `#FFFFFF`, Elevated Card `#F1F5F9`, Primary `#059669`, Secondary `#0284C7`, Warning `#D97706`, Error `#DC2626`.
- **Typography & Hierarchy**:
  - *Display & Headlines*: **Space Grotesk** (bundled locally via `font-util`) for technical headers, telemetry counters, and shield status banners.
  - *Body & Scripts*: **Plus Jakarta Sans** for clean legibility across multi-language greeting editors (Bengali, Hindi, English) and settings tables.
  - *Monospace Telemetry*: **JetBrains Mono** for phone numbers, durations, SHA-256 model checksums, and spam score readouts.
- **Component Styling & Layout**:
  - Adaptive navigation pattern featuring a bottom `NavigationBar` for core hubs plus an organized **All 12 Modules Drawer / Hub Sheet** so every single screen (*Dashboard, Call Protection, Voice Receptionist, TTS Voice Studio, Voice Message Inbox, Spam Call History, Contacts & Allowlist, Blocklist, Model Manager, Settings, Permissions & Device Compatibility, Privacy & Data Management*) plus the *Default Dialer / In-Call UI* is 1 tap away.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Sherpa-ONNX Local Model Engine with Android Offline TTS Fallback**
  - *Chosen Approach*: Provide a dedicated `SherpaOnnxTtsEngine` and `ModelManager` that downloads, imports from device storage, checksum-verifies, and loads local `.onnx` voice models on-demand while seamlessly falling back to Android's on-device `TextToSpeech` engine when no `.onnx` file is loaded in memory.
  - *Why*: Voice models for Bengali (`bn-IN`), Hindi (`hi-IN`), and English (`en-IN`) range from 30 MB to 80 MB each. Bundling all three inside the APK bloats install size, whereas a hybrid architecture guarantees instant offline speech synthesis on first launch while supporting true local Sherpa-ONNX model management.
- **Decision 2: Honest Cellular Call Audio Capability Detection & Fallback**
  - *Chosen Approach*: Build `CallAudioCapabilityManager` using public `AudioManager`, `MediaRecorder.AudioSource`, and `TelecomManager` APIs to independently evaluate 5 distinct capabilities: (1) Call Screening, (2) Call Answer/Disconnect, (3) Local TTS Generation, (4) TTS Uplink Transmission to Cellular Caller, and (5) Remote Cellular Downlink Recording.
  - *Why*: Standard Android OS security blocks third-party apps from capturing `AudioSource.VOICE_DOWNLINK` or routing media output directly into the cellular uplink stream. Reporting `Unsupported` or `Limited` (e.g., Acoustic Speakerphone Coupling or Local Simulator Mode) prevents fake empty recordings and guides power users toward the documented SIP/VoIP Asterisk/FreeSWITCH PBX architecture when full server-side call audio injection is desired.
- **Decision 3: Hardware-Backed AES-GCM Recording Encryption**
  - *Chosen Approach*: Generate a 256-bit AES master key inside the Android Keystore (`KeyProperties.PURPOSE_ENCRYPT` / `PURPOSE_DECRYPT`) and encrypt every recorded `.wav` / `.m4a` voice message at rest, decrypting into a transient memory stream only during active MediaPlayer/AudioTrack playback or user-initiated export.
  - *Why*: Ensures private voice messages cannot be extracted by other apps or forensic file inspection, satisfying strict offline privacy requirements.

---

## 4. Technical Architecture & Data Strategy

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                   PRESENTATION LAYER (Jetpack Compose + M3)                   │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────┐ ┌───────────────────────┐  │
│  │  Dashboard  │ │ Call Protect │ │ Receptionist │ │   TTS Voice Studio    │  │
│  ├─────────────┤ ├──────────────┤ ├──────────────┤ ├───────────────────────┤  │
│  │Voice Inbox  │ │ Spam History │ │  Allowlist   │ │       Blocklist       │  │
│  ├─────────────┤ ├──────────────┤ ├──────────────┤ ├───────────────────────┤  │
│  │Model Manager│ │24+ Settings  │ │Compat Matrix │ │ Privacy & In-Call UI  │  │
│  └──────┬──────┘ └──────┬───────┘ └──────┬───────┘ └───────────┬───────────┘  │
└─────────┼───────────────┼────────────────┼─────────────────────┼──────────────┘
          │               │                │                     │
          ▼               ▼                ▼                     ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                     VIEWMODEL & DOMAIN USE-CASE LAYER                         │
│  ┌────────────────────────┐ ┌───────────────────────┐ ┌────────────────────┐  │
│  │ EvaluateIncomingCallUC │ │ RunReceptionistFlowUC │ │ SynthesizeMoodTtsUC│  │
│  └───────────┬────────────┘ └───────────┬───────────┘ └─────────┬──────────┘  │
└──────────────┼──────────────────────────┼───────────────────────┼─────────────┘
               │                          │                       │
               ▼                          ▼                       ▼
┌────────────────────────────┐ ┌──────────────────────┐ ┌───────────────────────┐
│   TELECOM & AUDIO ENGINE   │ │    DATA & STORAGE    │ │    LOCAL AI ENGINE    │
│ ┌────────────────────────┐ │ │ ┌──────────────────┐ │ │ ┌───────────────────┐ │
│ │VoiceShieldScreeningSvc │ │ │ │  Room Database   │ │ │ │SherpaOnnxTtsEngine│ │
│ ├────────────────────────┤ │ │ │ - VoiceMessages  │ │ │ ├───────────────────┤ │
│ │VoiceShieldInCallService│ │ │ │ - CallHistory    │ │ │ │AndroidOfflineTts  │ │
│ ├────────────────────────┤ │ │ │ - ContactRules   │ │ │ ├───────────────────┤ │
│ │CallAudioCapabilityMgr  │ │ │ │ - GreetingScripts│ │ │ │LocalSttTranscriber│ │
│ ├────────────────────────┤ │ │ │ - TtsVoiceModels │ │ │ ├───────────────────┤ │
│ │ReceptionistSessionMgr  │ │ │ ├──────────────────┤ │ │ │12-Mood ProsodyDSP │ │
│ └────────────────────────┘ │ │ │DataStore (24 Cfg)│ │ │ └───────────────────┘ │
│                            │ │ ├──────────────────┤ │ └───────────────────────┘
│                            │ │ │Keystore AES-GCM  │ │
│                            │ │ └──────────────────┘ │
└────────────────────────────┘ └──────────────────────┘
```

### Data Model & State
- **Room Database (`VoiceShieldDatabase`)**:
  - `VoiceMessageEntity`: `id`, `callerNumber`, `contactName`, `callDateMillis`, `callDurationSec`, `messageDurationSec`, `encryptedFilePath`, `recordingStatus` (`RECORDED_ENCRYPTED`, `UNSUPPORTED_CELLULAR_AUDIO`, `NO_CONSENT`, `SILENCE_TIMEOUT`), `consentStatus`, `transcription`, `spamScore`, `spamClassification`, `callHandlingStatus`, `audioCapabilityStatus`, `isRead`, `isImportant`.
  - `ScreenedCallLogEntity`: `id`, `phoneNumber`, `contactName`, `timestamp`, `spamScore`, `decision` (`ALLOWED`, `SILENCED`, `REJECTED`, `ROUTED_TO_RECEPTIONIST`), `reason`, `verificationStatus`.
  - `CallerRuleEntity`: `id`, `phoneNumber`, `displayName`, `ruleType` (`ALLOWLIST`, `BLOCKLIST`, `USER_REPORTED_SPAM`), `notes`, `createdAt`.
  - `GreetingScriptEntity`: `id`, `title`, `languageCode` (`bn-IN`, `en-IN`, `hi-IN`), `scriptText`, `closingText`, `callerTarget` (`UNKNOWN_CALLERS`, `KNOWN_CALLERS`, `ALL`), `lengthMode` (`SHORT`, `LONG`), `includesDisclosure`, `isDefault`.
  - `VoiceModelEntity`: `id`, `name`, `languageCode`, `architecture` (`SHERPA_ONNX_VITS`, `SHERPA_ONNX_PIPER`, `SYSTEM_OFFLINE_TTS`), `fileSizeMb`, `sha256Checksum`, `localFilePath`, `status` (`NOT_DOWNLOADED`, `VERIFIED_READY`, `CORRUPTED`, `ACTIVE`), `supportsNativeEmotion`.
- **Preferences DataStore (`VoiceShieldSettingsRepository`)**:
  - Persists all 24 required variables (`autoAnswerEnabled`, `answerDelaySeconds`, `greetingEnabled`, `greetingText`, `language`, `voiceModel`, `voiceMood`, `speechRate`, `speechPitch`, `speechVolume`, `maxRecordingDuration`, `silenceTimeout`, `maxCallDuration`, `repeatGreetingCount`, `recordingDisclosureEnabled`, `callerConsentRequired`, `knownContactsBypass`, `unknownNumberAction`, `spamThreshold`, `notificationEnabled`, `messageAutoDeleteDays`, `workingHoursEnabled`, `fallbackCallAction`, `maximumStoredMessages`) plus theme mode (`DARK`, `LIGHT`, `SYSTEM`), pause duration (`pauseMs`), and sentence emphasis (`emphasisLevel`).

### Interactive Component & State Mapping
- **Call Screening & Spam Engine (`SpamScoringEngine`)**: Combines contact lookup, allowlist/blocklist matching, repeated call burst detection (e.g., multiple calls within 5 minutes bypass silence if configured or flag robo-dialers), prefix/pattern heuristics, and Android `Connection.VERIFICATION_STATUS_*` into a normalized `0–100` spam score compared against `spamThreshold`.
- **Receptionist Session & Simulator (`ReceptionistSessionManager`)**: Executes a deterministic state machine (`IDLE` -> `RINGING_DELAY` -> `ANSWERING` -> `PLAYING_GREETING` -> `AWAITING_CONSENT` -> `RECORDING_MESSAGE` -> `PLAYING_CLOSING` -> `ENCRYPTING_AND_SAVING` -> `COMPLETED_NOTIFIED` or `FALLBACK_UNSUPPORTED_AUDIO`). When run on standard cellular hardware without downlink capture support, it explicitly records the capability status as `UNSUPPORTED_CELLULAR_AUDIO` and applies `fallbackCallAction` without creating a fake audio file. When run in **Interactive Simulator / Acoustic Mode**, it captures real PCM audio from the device microphone, monitors RMS amplitude for silence timeout, encrypts the resulting WAV file via `KeystoreCryptoManager`, saves the record to Room, and posts the Android notification.
- **Comprehensive JVM & Robolectric Test Suite**: Covers contact matching, spam score calculation, call screening decision deadlines, all 24 settings persistence, TTS model verification and synthesis fallback, capability detection, encrypted message storage/deletion, notification building, and call lifecycle disconnect safety.

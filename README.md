# VoiceShield AI — Offline AI Call Screening & Voice Receptionist

**VoiceShield AI** is a privacy-first, 100% offline Android call screening and AI voice receptionist application built with Kotlin, Jetpack Compose (Material 3), Room Database, Jetpack DataStore, Android Telecom Framework (`CallScreeningService` & `InCallService`), local Sherpa-ONNX / Android Offline TTS, and Android Keystore `AES-256-GCM` encryption at rest.

---

## 1. Key Capabilities & Honest Android Hardware Transparency

### What Works on Standard Non-Rooted Android 10+ (API 29+)
1. **`CallScreeningService` (`RoleManager.ROLE_CALL_SCREENING`) — Supported**:
   - Automatically screens incoming phone numbers against Saved Contacts, Allowlist, Blocklist, User-Reported Spam, Repeated Call Bursts, and Carrier STIR/SHAKEN verification status within Android's strict 5-second response deadline.
   - Allows, silences, or rejects calls without replacing the user's default dialer if desired.
   - Emergency numbers (`112`, `911`, `100`, `101`, `102`, `108`, `999`) are **never** blocked or delayed.
2. **Default Dialer (`RoleManager.ROLE_DIALER`) & `InCallService` — Supported**:
   - Optional role request to become the default phone app, unlocking programmatic `acceptRingingCall()` / `Call.answer()`, `Call.disconnect()`, Mute/Speakerphone routing, and a built-in Dial Pad.
3. **Offline Expressive TTS & 12 Voice Moods — Supported**:
   - Local Sherpa-ONNX model management (`.onnx` download/provisioning, custom file import, SHA-256 checksum verification, on-demand loading) paired with Android's on-device `TextToSpeech` offline synthesizer for **Bengali (`bn-IN`)**, **English (`en-IN`)**, and **Hindi (`hi-IN`)**.
   - Includes 12 Voice Mood presets (*Professional, Polite, Friendly, Calm, Serious, Firm, Cheerful, Empathetic, Neutral, Urgent, Soft, Strict*) with prosody DSP controls (Speed, Pitch, Volume, Inter-Sentence Pauses, Sentence Emphasis).
4. **Cellular Uplink TTS Injection & Remote Cellular Downlink Recording — Unsupported on Ordinary Android**:
   - Standard Android 10+ security isolates the cellular baseband modem (`VOICE_UPLINK` and `VOICE_DOWNLINK`). Ordinary apps cannot inject TTS directly into the cellular uplink or capture remote cellular audio without `CAPTURE_AUDIO_OUTPUT` (a system/OEM signature permission).
   - **VoiceShield AI never fakes a cellular recording**: `CallAudioCapabilityManager` reports these capabilities honestly as `Unsupported`, logs an explicit `UNSUPPORTED_CELLULAR_AUDIO` status when cellular recording is blocked, and provides both an **Interactive Call & Receptionist Simulator** (using live device microphone + speaker) and an optional **SIP/VoIP PBX Conditional Call Forwarding architecture**.

---

## 2. Required Permissions Explanations

| Permission / Role | Why It Is Required |
|---|---|
| `RoleManager.ROLE_CALL_SCREENING` | Allows `VoiceShieldCallScreeningService` to inspect incoming numbers and silence/reject spam calls. |
| `RoleManager.ROLE_DIALER` *(Optional)* | Enables `VoiceShieldInCallService` to display in-call UI and automatically answer/disconnect calls. |
| `READ_CONTACTS` *(Optional)* | Allows known contacts in the user's phonebook to bypass the AI receptionist (`knownContactsBypass`). |
| `RECORD_AUDIO` | Captures caller audio in the Interactive Receptionist Simulator, VoIP/SIP bridge, or Acoustic Speaker mode. |
| `READ_PHONE_STATE` & `ANSWER_PHONE_CALLS` | Detects call states and supports programmatic auto-answer after `answerDelaySeconds`. |
| `POST_NOTIFICATIONS` | Dispatches actionable `"New Voice Message"` notifications (`Listen`, `Open Message`, `Delete`). |

---

## 3. Local Sherpa-ONNX Voice Model Installation Guide

1. Open **VoiceShield AI** and navigate to **Model Manager** (via the top-left menu or Dashboard grid).
2. Choose a supported regional voice model:
   - **Bengali (India / West Bengal)**: `vits-piper-bn_IN-arjun-medium` or `vits-piper-bn_IN-mitra-low`
   - **English (India)**: `vits-piper-en_IN-kavya-medium`
   - **Hindi (India)**: `vits-piper-hi_IN-rohan-medium`
3. Tap **Download & Verify** (or use **Import & Verify** to import a custom `.onnx` model bundle from device storage).
4. The app computes the **SHA-256 checksum** before marking the model `Verified & Ready`. Tap **Load Model** to activate it on demand.

---

## 4. Physical Android Device Testing Instructions

1. **Grant Roles & Permissions**:
   - Open **Permissions & Compatibility** -> tap **Grant / Refresh Runtime Permissions**.
   - Open **Call Protection** -> tap **Request Call Screening Role** (and optionally **Request Default Phone App Role**).
2. **Test End-to-End Receptionist Flow (Any Device)**:
   - Open **Voice Receptionist** (or tap **Test AI Receptionist** on the Dashboard).
   - Enter any test number (e.g., `+919831099887`), toggle **Simulate Caller Recording Consent Granted**, and tap **Simulate Incoming Call**.
   - Speak into your phone's microphone while Stage 6 (*Recording Caller Voice Message*) is active, or pause speaking to test automatic `silenceTimeout` detection.
   - Verify the `"New Voice Message"` notification arrives and play back the AES-256-GCM encrypted recording in **Voice Message Inbox**.

---

## 5. Build, Unit Tests & APK Generation Instructions

- **Run Local JVM & Robolectric Tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Assemble Debug APK**:
  ```bash
  gradle :app:assembleDebug
  ```
  Output APK: `app/build/outputs/apk/debug/app-debug.apk`

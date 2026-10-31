# VoiceShield AI — Simplified Universal Shield & Default Dialer

A streamlined, reliable offline AI voice receptionist that automatically answers all incoming phone calls when the Shield is active, speaks to callers using on-device multilingual TTS (Bengali, English, Hindi), and records voice messages locally using Android's Default Dialer architecture.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following design decisions were confirmed during the interactive interview:
>
> - **Universal Shield Protection**: When the master Shield toggle is ON, the AI receptionist applies universally to **all incoming calls** (no complicated bypass rules or separate unknown-only gates).
> - **Dynamic Ring Delay Slider (1–30 seconds)**: Users can adjust exactly how many seconds the phone rings before the AI receptionist automatically picks up the call, using a responsive slider right on the home dashboard.
> - **Default Dialer In Settings**: Default Phone App role activation is kept cleanly inside Settings/Options without aggressive popups, allowing the app's `InCallService` to handle incoming calls, answer programmatically, play audio to the caller, and capture voice messages.
> - **Simplified UX**: Complex VoIP/SIP configurations are simplified into the background; the core interface focuses on Shield toggle, Ring delay slider, Language selection (Bengali, English, Hindi), and Recorded Messages.

---

## 1. Overview & Core Concept

- **What It Does**: VoiceShield AI acts as your personal automated receptionist. When you turn on the Shield, every incoming phone call rings for your chosen duration (1 to 30 seconds). If not picked up manually, the app automatically answers via Android Telecom's Default Dialer service, speaks a friendly greeting in Bengali, English, or Hindi, asks the caller to leave a message, records their response, and saves it securely to your on-device message inbox.
- **Target Audience / Persona**: Busy professionals, students, drivers, and anyone who wants automated call answering without relying on cloud servers, cellular carrier voicemail fees, or complex PBX setups.
- **Key Value**: Zero cloud dependency, 100% on-device TTS privacy, universal call coverage, and full call control through Android's official Default Dialer framework.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **One-Tap Protection**: User opens the app and toggles the prominent **Shield Active** switch. A clear subtitle confirms: *"Active for all incoming calls"*.
2. **Dynamic Ring Delay Adjustment**: Directly below the Shield, a slider labeled **"Answer Call After Ringing"** displays the exact delay in seconds (e.g., `5s`, `10s`, `25s`) with range `1s – 30s`.
3. **Language & Voice Quick-Select**: A compact chip selector lets the user choose between **Bengali (বাংলা)**, **English**, and **Hindi (हिन्दी)** with a quick "Preview Voice" button.
4. **Incoming Call Auto-Answer**: When a call arrives:
   - The device rings for the configured duration (e.g. 5 seconds).
   - VoiceShield's Default Dialer `InCallService` automatically answers.
   - The offline TTS speaks the greeting to the caller.
   - The caller leaves their voice message, which is recorded.
   - The call hangs up automatically when the caller finishes or reaches the time limit.
5. **Message Playback & Notification**: The user receives an instant notification with caller ID, timestamp, and a direct "Listen" button that opens the message with waveform audio playback.

### Visual Identity & Theme

- **Aesthetic Direction**: Cyber-Security Shield — clean, dark navy canvas with vibrant emerald green active indicators and cyan telemetry accents.
- **Color Tokens**:
  - Primary Accent: Emerald Shield (`#00E676` / `#10B981`) for active state and safety.
  - Background: Deep Navy Surface (`#0A1128` / `#0F172A`) for high contrast and battery savings.
  - Warning/Spam: Amber (`#F59E0B`) and Crimson (`#EF4444`).
- **Typography & Layout**:
  - Crisp, modern typography with clear hierarchy (Headline bold for status, readable body text).
  - Generous 16dp edge padding and large touch targets (minimum 48dp).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Universal Call Handling vs. Segmented Screening**
  - *Chosen Approach*: When Shield is ON, all incoming calls trigger the auto-receptionist after the set ring delay.
  - *Why*: Eliminates user confusion about why certain calls were or weren't answered; provides simple, predictable behavior.
- **Decision 2: Android Default Dialer (`ROLE_DIALER`) for Cellular Calls**
  - *Chosen Approach*: Use Android's official `InCallService` with `RoleManager.ROLE_DIALER` as the primary calling integration.
  - *Why*: Standard Android security policy restricts background apps from answering phone calls or accessing call audio unless the app holds the Default Phone App role. Implementing `InCallService` provides the required system hooks.
- **Decision 3: Integrated 1–30s Dynamic Slider**
  - *Chosen Approach*: Place the ring delay slider directly on the dashboard screen rather than burying it deep in settings.
  - *Why*: Ring delay is the single most important user preference when adjusting automated call answering.

---

## 4. Technical Architecture & Data Strategy

### System Component Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                      Android OS Telecom                      │
│        (Incoming Cellular Call / Connection Service)         │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│          VoiceShieldInCallService (Default Dialer)           │
│  - Detects STATE_RINGING                                     │
│  - Starts ring delay countdown (1s - 30s)                    │
│  - Executes Call.answer() when timer expires                 │
│  - Routes audio to TTS Player & Audio Recorder               │
│  - Executes Call.disconnect() when message is complete       │
└──────────────────────────────┬───────────────────────────────┘
                               │
        ┌──────────────────────┴──────────────────────┐
        ▼                                             ▼
┌─────────────────────────────┐         ┌─────────────────────────────┐
│       LocalTtsEngine        │         │   EncryptedAudioRecorder    │
│  - Offline Bengali Voice    │         │  - Captures caller audio    │
│  - Offline English Voice    │         │  - AES-256-GCM encryption   │
│  - Offline Hindi Voice      │         │  - Saves to app storage     │
└──────────────┬──────────────┘         └──────────────┬──────────────┘
               │                                       │
               └───────────────────┬───────────────────┘
                                   │
                                   ▼
┌──────────────────────────────────────────────────────────────┐
│                    VoiceShieldRepository                     │
│  - SettingsDataStore (Shield ON/OFF, Ring Delay 1-30s)       │
│  - Room Database (VoiceMessageEntity, ScreenedCallEntity)    │
│  - VoiceShieldNotificationManager (Listen Deep-link)         │
└──────────────────────────────────────────────────────────────┘
```

### Data Model & Settings Additions

- `VoiceShieldSettings`:
  - `protectionEnabled: Boolean` (Master Shield toggle)
  - `applyToAllCalls: Boolean = true` (Universal rule)
  - `answerDelaySeconds: Int` (1 to 30 seconds slider value)
  - `language: VoiceLanguage` (BENGALI_IN, ENGLISH_IN, HINDI_IN)
  - `maxRecordingDuration: Int` (e.g. 60 seconds)
  - `silenceTimeout: Int` (e.g. 5 seconds)

### Verification & Testing Plan

1. **Unit Tests**: Verify that `answerDelaySeconds` correctly clamps between 1 and 30, and `applyToAllCalls` routes all incoming calls to the receptionist.
2. **Robolectric Tests**: Test `InCallService` lifecycle states (`STATE_RINGING` -> auto-answer timer -> `STATE_ACTIVE` -> TTS playback -> recording completion -> `STATE_DISCONNECTED`).
3. **UI Verification**: Validate smooth slider interaction, Shield master toggle state persistence in DataStore, and one-tap Default Phone App request in Settings.

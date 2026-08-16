# VocoNexus

**VocoNexus** is a production-grade, free, offline-first, privacy-focused, long-form Android Text-to-Speech (TTS) application.

Designed for multi-hour audio narration (books, articles, research papers, scripts), VocoNexus operates 100% on-device without cloud API dependencies.

---

## Technical Architecture

```text
                    VocoNexus
                        │
        ┌───────────────┼────────────────┐
        │               │                │
     UI Layer      Application Layer   Navigation
(Compose / M3)   (ViewModels / Flows) (Jetpack Nav)
        │               │                │
        └───────────────┼────────────────┘
                        │
                  Domain Layer
             (Planner / Speech Control)
                        │
        ┌───────────────┼────────────────┐
        │               │                │
   TTS System       Project System    Audio System
(Kokoro / Sherpa)     (Room DB)        (Media3)
        │               │                │
        └───────────────┼────────────────┘
                        │
                Android Platform
         (Storage / Foreground Service / JNI)
```

---

## Core Capabilities

### 1. Offline-First Privacy Guarantee
- 100% on-device processing. Source scripts, preprocessed text, and generated audio assets never leave the device.
- Diagnostic export tool explicitly strips all script text before formatting crash reports.

### 2. Large Script Editing & Preprocessing
- Supports massive scripts (books, documents, multi-part scripts).
- Direct import of plain text (`.txt`) and SubRip subtitle (`.srt`) files.
- SRT parser automatically strips timestamps and index numbers, preserving speech text.
- Rule-based sentence segmenter with model-aware token estimation and chunk planning.

### 3. Speech & Pronunciation Control
- **Pronunciation Precedence**: Hierarchical rule resolution (`DOCUMENT` scope > `PROJECT` scope > `GLOBAL` scope).
- **Recursion Safety**: Built-in cycle detection prevents infinite loops or stack overflow errors when rules reference each other.
- **Speech Instructions**: Fine-grained pause insertion, emphasis, rate/pitch adjustments, and symbol expansion without altering the source script.
- **Multilingual Voice Routing**: Multi-speaker mapping and language routing (e.g. English, Hindi, Spanish).

### 4. Long-Form TTS Generation Engine
- Persistent generation queue backed by Room database.
- Foreground service execution with notification updates and Android crash recovery.
- Atomic audio generation: Temporary file creation $\rightarrow$ checksum validation $\rightarrow$ atomic commit to prevent corrupted WAV assets.
- Fingerprint caching: Unchanged chunks retain generated audio; editing a chunk invalidates only affected fingerprints.

### 5. Audio Library, Playback & Export
- Integrated Media3 ExoPlayer audio library with playback speed controls (0.5× to 3.0×).
- Selection-based multi-part WAV audio combining with zero quality loss.
- Flexible audio export to external storage via Storage Access Framework (SAF).

### 6. Model & Asset Management
- Resumable HTTP Range catalog downloads.
- Archive Security Sanitizer: Protection against Zip Slip path traversal and decompressed archive size ceilings (4 GB limit).
- Model requirement evaluation: Hardware profiling, RAM checking, thermal pressure monitoring, and low-storage warnings.

### 7. UX & Accessibility
- Material 3 design with System, Light, and Dark themes.
- Predictive back navigation across all application routes.
- Screen reader accessibility (TalkBack), minimum touch targets, and high-contrast UI elements.

---

## System Requirements & Prerequisites

- **Minimum Android Version**: Android 8.0 (API Level 26)
- **Target Android Version**: Android 15 (API Level 35)
- **Build Environment**: JDK 21, Android SDK 35, Gradle 8.x

---

## Building VocoNexus

### 1. Compile Debug APK
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew assembleDebug
```

### 2. Run Complete Unit & Stress Test Suite (98 Test Suites)
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew testDebugUnitTest --stacktrace
```

### 3. Build Production Release APK
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew assembleRelease
```

---

## Licensing & Asset Attributions

- **VocoNexus Codebase**: Open-source native Kotlin application.
- **Media3 / Room / Jetpack**: Apache License 2.0 (Google LLC).
- **Kokoro & Sherpa-ONNX Foundation**: Apache License 2.0 / MIT License.
- **Third-Party Notices**: Accessible directly within the application settings screen.

---

## Technical Disclaimer

VocoNexus imposes no artificial application-level limit on script length or total generation duration, subject to device hardware, model context capabilities, Android background execution rules, processing time, and available disk storage.

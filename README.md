# VoxBridge — Speak Without Speaking

<p align="center">
  <b>A Voice Assistance &amp; Cross-Language Communication Bridge for Speech-Impaired Individuals and Travelers</b>
</p>

---

## 📌 Project Overview

**VoxBridge** is a native Android application built using Java and Android Views to empower individuals with speech difficulties to communicate effortlessly through real-time text-to-speech and quick-phrase cards. Additionally, VoxBridge incorporates on-device multilingual machine translation (powered by **Google ML Kit**) to bridge language barriers for travelers and multilingual communication without reliance on expensive cloud APIs or subscriptions.

---

## ✨ Features

- **🗣️ Real-Time Text-to-Speech (TTS):**
  - Type any custom sentence or message and play it aloud instantly.
  - Large, high-contrast, accessible **Speak**, **Stop**, and **Clear** controls.
  - Granular **Speech Rate Control** ($0.5\times$ Slow to $2.0\times$ Very Fast) with real-time feedback.
  - Clean lifecycle management ensuring battery efficiency and proper resource disposal.

- **⚡ Accessible Quick Phrases:**
  - One-tap accessible cards with large touch targets ($\ge 52\,\text{dp}$) designed for users with motor or dexterity challenges.
  - Includes essential emergency and daily communication phrases:
    - *"I need help."*
    - *"I need water."*
    - *"Please call my family."*
    - *"I cannot speak. Please read this message."*
    - *"Where is the nearest hospital?"*
    - *"Thank you."*
    - *"Yes."*
    - *"No."*
    - *"Please speak slowly."*
    - *"I do not understand."*

- **🌐 On-Device Multilingual Machine Translation:**
  - Powered by **Google ML Kit Translate**.
  - Supports 10 international languages:
    - English (`en`)
    - Hindi (`hi`)
    - Spanish (`es`)
    - French (`fr`)
    - German (`de`)
    - Chinese (`zh`)
    - Japanese (`ja`)
    - Arabic (`ar`)
    - Russian (`ru`)
    - Portuguese (`pt`)
  - **100% Offline After Model Download:** Requires internet only on the very first translation to download the lightweight model (~$30\,\text{MB}$). Subsequent translations operate completely offline.
  - **Target-Language Voice Output:** Speak translated messages aloud using the corresponding native voice engine if installed on the device.

- **🎨 Modern & Accessible UI:**
  - Deep Navy (`#1B2A4A`) and Vibrant Teal (`#00838F`) palette.
  - High-contrast typography and subtle elevation.
  - Live character counter.
  - Responsive, scrollable interface fitting all screen sizes.
  - Edge-to-edge system window insets support.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Java (Android SDK)
- **UI Toolkit:** Native Android Views, XML Layouts, Material Design 3
- **Minimum SDK:** API 26 (Android 8.0 Oreo)
- **Target SDK:** API 36
- **Speech Engine:** Android Native `android.speech.tts.TextToSpeech`
- **Translation Engine:** Google ML Kit Translation (`com.google.mlkit:translate:17.0.3`)
- **Build System:** Gradle 9.4.1 / Android Gradle Plugin 9.2.1

---

## 📂 Project Structure

```
app/src/main/
├── AndroidManifest.xml          # TTS queries & network permissions
├── java/com/example/voxbridge/
│   ├── MainActivity.java        # UI controller & user interactions
│   ├── TtsManager.java          # Lifecycle-aware TTS & multilingual speech
│   └── TranslationManager.java  # ML Kit model management & on-device translation
└── res/
    ├── drawable/                # High-contrast vectors & custom ripple shapes
    ├── layout/
    │   ├── activity_main.xml    # Responsive main application screen
    │   └── item_quick_phrase.xml# Accessible card item for quick phrases
    └── values/
        ├── colors.xml           # Teal, Navy, and accessible status colors
        ├── strings.xml          # Multilingual labels & predefined phrases
        └── themes.xml           # Material 3 theme definitions
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat or newer
- Android SDK with API 26+
- Java Development Kit (JDK 17 or JDK 21)

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/KiyotakaAyanakoji/Vox-Bridge.git
   ```
2. Open the project in Android Studio.
3. Build the project using the Gradle wrapper:
   ```bash
   ./gradlew assembleDebug
   ```
4. Run on an Android device or emulator running API 26+.

---

## 📜 License

This project is developed as an academic B.Tech mini-project for accessibility and assistive mobile technology.

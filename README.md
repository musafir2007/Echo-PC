# 🎧 Echo Media Player

Echo is a premium, high-fidelity media player built with **Kotlin Multiplatform** and **Compose Desktop**. It combines a sleek, modern UI with bit-perfect audio processing and a modular extension system.

![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-purple.svg)
![Compose](https://img.shields.io/badge/compose-multiplatform-orange.svg)

## ✨ Features

### 🔊 Audiophile-Grade Playback
- **Bit-Perfect Audio**: Uses WASAPI Exclusive mode to bypass the system mixer and prevent resampling.
- **Audio Effects**: 
  - **11-Segment Bass Boost**: Precision low-end enhancement.
  - **Playback Speed**: Adjust from 0.5x to 2.0x with real-time scaling.
  - **Pitch Control**: Optional "Vinyl Mode" to shift pitch alongside speed.
- **Gapless Playback**: Intelligent caching for seamless transitions between tracks.

### 🎨 Premium Visuals
- **Dynamic Backgrounds**: Apple Music-style animated blurred backgrounds that drift and shift based on album art.
- **Fluid Navigation**: Sidebar-based navigation with full keyboard support (Backspace to go back).
- **OLED Mode**: Pure black theme support for high-contrast displays.

### 🧩 Modular Extension System
- **.eapk Support**: Install third-party extensions for streaming, lyrics, or metadata.
- **Dynamic Loading**: Install extensions directly from a URL or a local file.
- **Hybrid Library**: Seamlessly browse local files and streamed content in a single search interface.

### 📂 Smart Library Management
- **Persistent Cache**: Your library loads instantly on startup using a high-performance JSON cache.
- **Background Sync**: Automatically monitors your selected folders for new media.
- **Auto-Artist Discovery**: Intelligent fallback systems use album art to generate artist profiles.

## 🚀 Getting Started

### Prerequisites
- **JDK 17** or higher.
- **VLC Media Player** installed (required for the VLCJ engine).

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/Echo.git
   ```
2. Open the project in **Android Studio** or **IntelliJ IDEA**.
3. Run the application:
   ```bash
   ./gradlew run
   ```

## 🛠 Tech Stack
- **Language**: Kotlin
- **UI Framework**: Compose Multiplatform
- **Audio Engine**: VLCJ (libVLC)
- **Metadata**: JAudioTagger
- **Serialization**: KotlinX Serialization

---
Built with ❤️

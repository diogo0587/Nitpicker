# Nitpicker 🖼️🎬

[中文](README_zh.md) | **English**

**Nitpicker** is a powerful Android application designed not just as a media browser, but as a **Next-Generation Local Material Hub with Offline AI**. 

Whether you are fetching media from online sources or managing massive local photo/video collections, Nitpicker automatically analyzes, indexes, and organizes your visual assets entirely on-device without internet reliance or privacy concerns.

## ✨ Core Features

### 🧠 On-Device AI Material Management (New!)
*   **100% Offline Image Tagging**: Integrates Google’s bundled **ML Kit Image Labeling**, automatically detecting 400+ consumer-level entities (e.g., *Food, Indoor, Nature, Pets*) in your local albums without network requests.
*   **Storage Access Framework (SAF) Integration**: Add entire local directories as your "Material Base." The app gains persistable permissions and reads files purely by URI—**Zero data copying, Zero extra storage taken.**
*   **Smart Background Indexing**: Powered by Android `WorkManager`, your massive local albums are processed and indexed in the background. Tag counts and metadata are saved to a blazing-fast `Room` SQLite database.
*   **Auto-Organized Home UI**: Say goodbye to a messy gallery! The Home Screen dynamically generates collections based on your top 7 most frequent AI tags, plus an "All Tags" cloud for immediate semantic-like search.

### 🌐 Online Media Browser & Downloader
*   Browse online albums and media lists efficiently.
*   Filter files quickly by content type (Image / Video).
*   **Batch Caching**: Multi-select support to asynchronously cache/download online materials to your local storage.
*   Built-in Download Manager to track concurrent task progress and history.

### 🎬 Advanced Media Viewing
*   **Built-in Video Player**: Robust media playback with automatic playlist queuing and state-saving (remember where you left off).
*   **Gesture-Rich Image Viewer**: Supports pinch-to-zoom, pan, double-tap zoom, and seamless horizontal swiping for immersive photo viewing.

---

## 🏗️ Technical Architecture Highlight
Nitpicker demonstrates modern Android development standards:
*   **100% Kotlin & Jetpack Compose**: Fully declarative UI with structured Navigation.
*   **Coroutines & Flow**: Reactive architecture ensuring a junk-free UI while handling heavy database `StateFlow` connections.
*   **Hilt / Dagger**: Dependency Injection across ViewModels, Repositories, and WorkManager.
*   **SQLite Defense Strategies**: Advanced logic using `INSTR()` matching to correctly prune orphaned database tags when users externally delete files/folders (resolving the infamous URL-encoded `%` wildcard bug).

---

## 🚀 Installation

You can download the latest `app-release.apk` directly from the **[GitHub Releases](https://github.com/d3intran/Nitpicker/releases)** page.

1.  Navigate to the **[Releases page](https://github.com/d3intran/Nitpicker/releases)**. 
2.  Download the `Nitpicker.apk` file from the latest release. 
3.  On your Android device, ensure "Install from unknown sources" is enabled.
4.  Locate the downloaded APK file and tap to install.

---

## 🤝 Contributing

Contributions of all kinds are welcome!

*   If you encounter a bug or have a feature suggestion, please open an [Issue](https://github.com/d3intran/Nitpicker/issues).
*   Or email me with a detailed description: `d3intran@gmail.com`

**To contribute code:**
1.  Fork this repository.
2.  Create a new branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your changes (`git commit -m 'Add AmazingFeature'`).
4.  Push to the branch (`git push origin feature/AmazingFeature`).
5.  Open a Pull Request.

---

## 📄 License & Disclaimer

*   **License**: This project is licensed under the [MIT License](LICENSE).
*   **Disclaimer**: This application is built solely for educational purposes and learning Android APIs. It does not actively store server file information. All online content is retrieved from public APIs, and the author assumes no responsibility for third-party media content.

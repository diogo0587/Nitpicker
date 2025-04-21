# Nitpicker 🖼️🎬

[中文](README_zh.md) | **English**

Nitpicker is an Android application for browsing, viewing, and downloading images and videos from **[bunkr-albums](https://bunkr-albums.io) **  /onlyfans/fantia/fansly...

## 📄 Disclaimer
  * This software is intended solely for learning and exchanging knowledge about Android APIs. It does not store any file information. All content originates from the internet, and the author is not responsible for any of the content.

---

## ✨ Features

*   Browse online albums and file lists
*   Filter files by type (Image/Video)
*   Supports multi-selection for batch caching (downloading)
*   Built-in download manager to view caching progress and history
*   Built-in video player with playlist support and state saving
*   Built-in image viewer with gesture zoom/pan and swipe navigation

## 🚀 Installation

You can download the latest `app-release.apk` file directly from the **[GitHub Releases](https://github.com/d3intran/Nitpicker/releases)** page.

1.  Go to the **[Releases page](https://github.com/d3intran/Nitpicker/releases)**. 
2.  Download the `Nitpicker.apk` file from the latest release. 
3.  On your Android device, you might need to enable "Install from unknown sources" to install the APK.
4.  Locate the downloaded APK file and tap to install.

## 🛠️ Building from Source

If you want to build this application yourself:

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/d3intran/Nitpicker.git
    cd Nitpicker
    ```
2.  **Open the project:** Use the latest stable version of Android Studio to open the project.
3.  **Sync Gradle:** Wait for Android Studio to complete Gradle sync and download dependencies.
4.  **Build APK:**
    *   (Optional) To build a Release version, ensure you have configured signing keys according to the [Android Signing documentation](https://developer.android.com/studio/publish/app-signing#sign-apk). Configure key information in `local.properties` or environment variables and modify `signingConfigs` in `app/build.gradle`.
    *   In the Android Studio menu, select `Build` > `Build Bundle(s) / APK(s)` > `Build APK(s)`.
    *   The generated APK file will be located in the `app/build/outputs/apk/release/` directory. <!-- Corrected standard output path -->

## 🤝 Contributing

Contributions of all kinds are welcome!
If you find a bug or have a feature suggestion, please feel free to create an [Issue](https://github.com/d3intran/Nitpicker/issues). <!-- TODO: Replace with your actual Issues page URL -->

Alternatively, you can email me with a detailed description of the problem:
d3intran@gmail.com

If you want to contribute code:

1.  Fork this repository.
2.  Create a new branch (`git checkout -b feature/YourFeature`).
3.  Make your changes and commit them (`git commit -m 'Add some feature'`).
4.  Push your branch to your forked repository (`git push origin feature/YourFeature`).
5.  Create a Pull Request.

## 📄 License

This project is licensed under the [MIT License](LICENSE). Please see the `LICENSE` file for details.

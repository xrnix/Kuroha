<div align="center">

<img src="metadata/en-US/images/icon.png" width="120" alt="Kuroha Logo"/>

# Kuroha — Android

### A modern, open-source Manga, Manhwa, and Manhua reader for Android.

Kuroha is an open-source reader built with native Material 3 Expressive UI, an in-process catalog of over 1,000 manga sources requiring zero external extension installs, offline chapter downloads, Google Drive library synchronization, and progress scrobbling.

<p>
  <img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Gradle-8.13-02303A?style=for-the-badge&logo=gradle&logoColor=white" alt="Gradle"/>
  <img src="https://img.shields.io/badge/Android-8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Jetpack_Compose-1.5-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"/>
  <img src="https://img.shields.io/badge/Material_3_Expressive-6750A4?style=for-the-badge&logo=materialdesign&logoColor=white" alt="Material 3"/>
  <img src="https://img.shields.io/badge/Hilt-2.57-2C2D72?style=for-the-badge&logo=dagger&logoColor=white" alt="Hilt"/>
  <img src="https://img.shields.io/badge/Room-2.8-003B57?style=for-the-badge&logo=sqlite&logoColor=white" alt="Room"/>
  <img src="https://img.shields.io/badge/OkHttp-5.3-00A98F?style=for-the-badge&logo=square&logoColor=white" alt="OkHttp"/>
  <img src="https://img.shields.io/badge/Coil-3.4-FF6C37?style=for-the-badge&logo=coil&logoColor=white" alt="Coil"/>
</p>

[![License: GPL v3](https://img.shields.io/github/license/xrnix/Kuroha?color=blue)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/xrnix/Kuroha?label=download&color=0ae448)](https://github.com/xrnix/Kuroha/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/xrnix/Kuroha/total?color=9d95ff)](https://github.com/xrnix/Kuroha/releases)
[![Stars](https://img.shields.io/github/stars/xrnix/Kuroha?style=social)](https://github.com/xrnix/Kuroha/stargazers)

[![Download APK](https://img.shields.io/badge/Download-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/xrnix/Kuroha/releases/latest)

**Open Source · No Ads · No Telemetry · Zero External APK Plugins Required**

</div>

<p align="center">
  <img src="metadata/en-US/images/phoneScreenshots/1.png" width="220" alt="Home Screen"/>
  <img src="metadata/en-US/images/phoneScreenshots/2.png" width="220" alt="Manga Details"/>
  <img src="metadata/en-US/images/phoneScreenshots/3.png" width="220" alt="Reader View"/>
</p>

---

## Table of Contents

- [About](#about)
- [Key Features](#key-features)
  - [Material 3 Expressive UI](#material-3-expressive-ui)
  - [Source Architecture](#1000-built-in-sources-zero-apk-installations)
  - [Reader Engine](#reading-experience--engine)
  - [Downloads & Local Storage](#downloads--offline-reading)
  - [Library & History Management](#library-management--progress-tracking)
  - [Cloud Sync & Trackers](#cloud-sync--tracker-scrobbling)
  - [Privacy & Networking](#network-proxy--privacy-controls)
- [Screenshots](#screenshots)
- [Technical Architecture](#technical-architecture)
- [Codebase Navigation](#where-things-live)
- [Building from Source](#building-from-source)
- [Contributing](#contributing)
- [Acknowledgements](#acknowledgements)
- [License & Disclaimer](#license--disclaimer)

---

## About

**Kuroha** is an open-source Android manga, manhwa, and manhua reader designed for speed, stability, and adherence to Android's Material 3 Expressive guidelines. 

Unlike traditional readers that depend on separate third-party extension APK installations, Kuroha features a built-in parser engine with direct support for over 1,000 sources out of the box. It manages your library locally with Room database persistence, synchronizes state via Google Drive, tracks your reading milestones on community platforms (AniList, MyAnimeList, Shikimori, Kitsu), and downloads chapters in standard archive formats (CBZ) for offline reading.

---

## Key Features

### Material 3 Expressive UI
- **Design System**: Built on Android's latest Material 3 Expressive components, featuring expressive card containers, rounded shapes, and fluid motion schemes.
- **Dynamic Theming**: Full support for Android 12+ dynamic color extraction (Material You) alongside custom color palettes and high-contrast AMOLED pure dark mode.
- **Edge-to-Edge Experience**: Complete edge-to-edge layout handling with dynamic window insets across navigation bars and status bars.
- **Adaptive Navigation**: Floating bottom bar and pill navigation designed for one-handed reachability on phone screens and optimized split layouts for tablets.
- **Expressive Dialogs & Menus**: Contextual overflow menus with Material glyphs and expressive dialog cards for chapter downloads and action confirmations.

### 1,000+ Built-In Sources (Zero APK Installations)
- **Direct Native Parsers**: Access over 1,000 content sources natively in-process without requiring secondary APK installations, sideloading, or unknown source permissions.
- **Sources Catalog**: Search, browse, and filter sources by language, content rating, and operational status.
- **Source Presets & Pinning**: Pin frequently accessed sources and group sources into custom presets for quick switching.
- **Failover & Mirrors**: Automatic domain fallback and configurable mirror URLs for sites that update domains frequently.
- **Per-Source Preferences**: Individual domain overrides, custom User-Agent strings, cookie management, and Cloudflare clearance handling.

### Reading Experience & Engine
- **Multiple Layout Modes**:
  - Continuous Webtoon scroll with smooth vertical transitions.
  - Standard Left-to-Right (LTR) and Right-to-Left (RTL) paged reading.
  - Vertical paged layout.
- **Subsampling Scale Image View (SSIV)**: High-resolution image tiling engine to render large, uncompressed manga spreads without out-of-memory errors.
- **Navigation Controls**: Configurable tap zones, hardware volume key navigation, and gestures.
- **Visual Enhancements**: Reader zoom lock, automatic page split options for double-page spreads, brightness override, and custom color filters for night reading.

### Downloads & Offline Reading
- **Background Downloads**: Dedicated background download manager powered by AndroidX WorkManager, providing pause, resume, cancel, and queue reordering.
- **Custom Storage Directories**: Choose internal storage or external SD card locations for downloaded content.
- **Archive Formats**: Save chapters as standard CBZ archives or individual image directories.
- **Local Storage Reader**: Open and read existing CBZ and ZIP archives stored on your device directly in Kuroha.

### Library Management & Progress Tracking
- **Smart Progress Chips**: Cover badges clearly indicate completion percentage and read status at a glance.
- **Custom Categories**: Organize your collection into customizable folders and shelves.
- **Reading History & Bookmarks**: Automatic tracking of reading progress across chapters, with individual page bookmarks.
- **Incognito Mode**: Read without updating history or tracking state when privacy is required.

### Cloud Sync & Tracker Scrobbling
- **Google Drive Sync**: Synchronize favorites, custom categories, reading history, and progress seamlessly across devices using your personal Google Drive account.
- **Community Trackers**: Automatic scrobbling to AniList, MyAnimeList, Kitsu, and Shikimori.
- **Discord Rich Presence**: Display current reading activity on Discord via KizzyRPC.

### Network, Proxy & Privacy Controls
- **In-App Proxy**: Configure custom HTTP, HTTPS, and SOCKS5 proxies directly in the app.
- **DNS-over-HTTPS (DoH)**: Built-in DoH providers (Cloudflare, Google, AdGuard, etc.) to circumvent DNS-level blocks without a system-wide VPN.
- **Biometric Security**: Protect access to your library with fingerprint, face unlock, or device PIN.
- **Secure Screen**: Option to block screenshots and prevent app preview snapshots in Android's recent apps switcher.

---

## Screenshots

<div align="center">
  <h3>Phone Experience</h3>
  <table>
    <tr>
      <td align="center"><b>Library & Progress</b></td>
      <td align="center"><b>Manga Overview</b></td>
      <td align="center"><b>M3 Expressive Reader</b></td>
    </tr>
    <tr>
      <td><img src="metadata/en-US/images/phoneScreenshots/1.png" width="240" alt="Home & Library"/></td>
      <td><img src="metadata/en-US/images/phoneScreenshots/2.png" width="240" alt="Details View"/></td>
      <td><img src="metadata/en-US/images/phoneScreenshots/3.png" width="240" alt="Reader View"/></td>
    </tr>
    <tr>
      <td align="center"><b>Explore & Discovery</b></td>
      <td align="center"><b>Downloads Manager</b></td>
      <td align="center"><b>Settings & Theming</b></td>
    </tr>
    <tr>
      <td><img src="metadata/en-US/images/phoneScreenshots/4.png" width="240" alt="Explore & Discovery"/></td>
      <td><img src="metadata/en-US/images/phoneScreenshots/5.png" width="240" alt="Downloads Manager"/></td>
      <td><img src="metadata/en-US/images/phoneScreenshots/6.png" width="240" alt="Settings & Theming"/></td>
    </tr>
  </table>
</div>

<br/>

<div align="center">
  <h3>Tablet & Large Screen Optimization</h3>
  <table>
    <tr>
      <td align="center"><b>Adaptive Navigation Rail & Library Grid</b></td>
      <td align="center"><b>Dual-Pane Manga Details & Chapters</b></td>
    </tr>
    <tr>
      <td><img src="metadata/en-US/images/tenInchScreenshots/1.png" width="420" alt="Adaptive Navigation Rail & Library Grid"/></td>
      <td><img src="metadata/en-US/images/tenInchScreenshots/2.png" width="420" alt="Dual-Pane Manga Details"/></td>
    </tr>
    <tr>
      <td align="center"><b>Full-Screen Landscape Reader & Controls</b></td>
      <td align="center"><b>Explore & Discovery Carousels</b></td>
    </tr>
    <tr>
      <td><img src="metadata/en-US/images/tenInchScreenshots/3.png" width="420" alt="Landscape Reader & Controls"/></td>
      <td><img src="metadata/en-US/images/tenInchScreenshots/4.png" width="420" alt="Explore & Discovery Carousels"/></td>
    </tr>
    <tr>
      <td align="center"><b>Sources & Catalogs Sheet</b></td>
      <td align="center"><b>Downloads Manager</b></td>
    </tr>
    <tr>
      <td><img src="metadata/en-US/images/tenInchScreenshots/5.png" width="420" alt="Sources & Catalogs Sheet"/></td>
      <td><img src="metadata/en-US/images/tenInchScreenshots/6.png" width="420" alt="Downloads Manager"/></td>
    </tr>
    <tr>
      <td align="center" colspan="2"><b>Material 3 Dual-Pane Settings</b></td>
    </tr>
    <tr>
      <td align="center" colspan="2"><img src="metadata/en-US/images/tenInchScreenshots/7.png" width="420" alt="Material 3 Dual-Pane Settings"/></td>
    </tr>
  </table>
</div>

---

## Technical Architecture

Kuroha is engineered on modern Android Jetpack libraries and standard reactive patterns:

```
┌────────────────────────────────────────────────────────┐
│                      UI Layer                          │
│   Jetpack Compose (M3 Expressive) + Material (MDC)     │
│   Hilt ViewModels · Navigation · StateFlow Observables │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                    Domain & Engine                     │
│   In-Process Parsers Engine · Scrobblers · WorkManager │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                    Data & Storage                      │
│   Room Database (SQLite) · DataStore · OkHttp 5 · Coil │
│   Local CBZ/ZIP Filesystem · Google Drive Sync API     │
└────────────────────────────────────────────────────────┘
```

| Component | Library / Framework | Purpose |
|---|---|---|
| **Language** | Kotlin 2.2 | Primary application language |
| **UI Framework** | Jetpack Compose + Compose Material 3 (1.5) | Expressive UI, dynamic shapes, and settings screens |
| **Design System** | Google Material Design Components (MDC 1.14) | Base themes, custom views, and list adapters |
| **Dependency Injection** | Dagger Hilt 2.57 | Scoped dependency injection across activities and workers |
| **Local Database** | Room 2.8 | SQLite persistence for library, history, and source metadata |
| **Networking** | OkHttp 5.3 + DNS-over-HTTPS | Network requests, cookies, and TLS configuration |
| **Image Loading** | Coil 3.4 + SubsamplingScaleImageView | Cover caching, animated GIF/WebP, and large page rendering |
| **Image Decoders** | libavif + JXL Coder | Native AVIF and JPEG XL format decoding |
| **Background Tasks** | AndroidX WorkManager 2.10 | Chapter downloads, sync jobs, and periodic updates |
| **Tracking** | KizzyRPC | Discord Rich Presence |

---

## Where Things Live

The codebase is organized in `app/src/main/kotlin/org/koitharu/kotatsu/` by functional responsibility:

```
org/koitharu/kotatsu/
├── details/          # Manga details overview, chapters list, and metadata display
├── reader/           # Paged and webtoon reader canvases, controls, and gesture decoders
├── download/         # Download service, download queue UI, and worker implementation
├── local/            # Local storage CBZ/ZIP scanner and file management
├── sourcescore/      # Source interfaces, parser glue, and source configurations
├── discover/         # Sources catalog, recommendations, and source exploration
├── explore/          # Exploration hub and discover feeds
├── search/           # Multi-source global search and source-specific querying
├── filter/           # Genre, tag, status, and sort filters
├── favourites/       # Library favorites view and category shelves
├── history/          # Reading history timeline and resume points
├── bookmarks/        # Page-level bookmarks management
├── sync/             # Google Drive backup, restore, and continuous synchronization
├── scrobbling/       # Trackers integration (AniList, MyAnimeList, Kitsu, Shikimori)
├── tracker/          # Tracker binding and status sync
├── settings/         # Compose-based app and source settings
├── main/             # Host activities, navigation controllers, and bottom bar
└── core/             # Base classes, Room DAOs, networking, and common utilities
```

---

## Building from Source

### Prerequisites
- **JDK**: Java Development Kit 17 or Java 21 (configured in `JAVA_HOME`)
- **Android SDK**: Android SDK Build-Tools 36+ with `compileSdk 37` installed
- **Git**: Working Git environment

### Build Commands

1. **Clone the repository**:
   ```bash
   git clone https://github.com/xrnix/Kuroha.git
   cd Kuroha
   ```

2. **Assemble Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   The compiled APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

3. **Run Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

4. **Install on connected device or emulator**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Contributing

Contributions from the community are welcome.

### Submitting Improvements
1. **Issues**: Open an issue to report reproducible bugs, suggest feature improvements, or discuss architectural changes.
2. **Pull Requests**:
   - Keep pull requests focused on a single issue or feature.
   - Follow existing Kotlin conventions and architectural separation in the repository.
   - Ensure the application builds cleanly with `./gradlew assembleDebug` and passes `./gradlew testDebugUnitTest`.
3. **Translations**:
   - UI strings are located in `app/src/main/res/values/strings.xml` and corresponding locale directories (`values-<locale>/`).
   - PRs adding or correcting locale translations are appreciated.

---

## Acknowledgements

- Built upon the open-source foundations established by [Kotatsu](https://github.com/KotatsuApp/Kotatsu) and its contributors.
- Uses [SubsamplingScaleImageView](https://github.com/davemorrissey/subsampling-scale-image-view) for high-resolution canvas rendering.
- Integrates community tracker APIs provided by [AniList](https://anilist.co), [MyAnimeList](https://myanimelist.net), [Kitsu](https://kitsu.io), and [Shikimori](https://shikimori.one).

---

## License & Disclaimer

Distributed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for details.

```
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

> **Disclaimer**: Kuroha does not host, store, or own any manga, comic, or image content. Kuroha is an independent client that parses publicly accessible internet sources according to user direction. All intellectual property belongs to their respective copyright holders.

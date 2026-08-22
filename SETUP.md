# Dropdesk - Social Media Video Downloader

## Quick Start

### 1. Jalankan Server Backend

```bash
# Install dependencies
pnpm install

# Build API server
pnpm --filter api-server build

# Start server (port 5000)
PORT=5000 node --enable-source-maps artifacts/api-server/dist/index.mjs
```

### 2. Install FFmpeg (untuk Premium Video+Audio)

FFmpeg diperlukan untuk merge video + audio (144p–1080p).

**Windows:**
```bash
pip install imageio-ffmpeg
# FFmpeg akan terinstall di path Python
```

**Atau download manual:**
- https://www.gyan.dev/ffmpeg/builds/
- Extract ke folder, tambahkan ke PATH

### 3. Buka Web App

Buka browser: `http://localhost:5000`

### 4. Install APK Android

**Download APK:**
- GitHub Actions: buka tab "Actions" → klik workflow terakhir → download artifact "Dropdesk-debug-apk"
- Atau build manual: lihat di bawah

**Install ke Android:**
```bash
adb install Dropdesk.apk
```

**Setup di Android:**
1. Buka Dropdesk app
2. Masukkan server URL: `http://IP_KOMPUTER_ANDA:5000`
3. Klik "Connect & Start"

**Cari IP komputer:**
- Windows: `ipconfig` → cari IPv4 Address
- Mac/Linux: `ifconfig` → cari inet

---

## Build APK Manual

### Prerequisites
- Node.js 20+
- Java 17 (JDK)
- Android SDK

### Steps

```bash
# 1. Install dependencies
pnpm install

# 2. Build API server
pnpm --filter api-server build

# 3. Build frontend
cd artifacts/social-downloader
PORT=5090 BASE_PATH=/ pnpm vite build

# 4. Sync Capacitor
npx cap sync android

# 5. Build APK
cd android
./gradlew assembleDebug
```

APK akan ada di: `artifacts/social-downloader/android/app/build/outputs/apk/debug/app-debug.apk`

---

## Build APK via GitHub (tanpa install apapun)

1. Push repo ke GitHub
2. Buka tab "Actions" di GitHub
3. Klik workflow "Build Dropdesk APK"
4. Klik "Run workflow"
5. Tunggu selesai (~5-10 menit)
6. Download APK dari tab "Artifacts"

---

## Fitur

| Platform | Video | Audio | Login? |
|----------|-------|-------|--------|
| YouTube | ✅ 144p–2160p | ✅ M4A, WebM | Tidak |
| YouTube Premium | ✅ 144p–1080p (merged) | ✅ | Tidak |
| TikTok | ✅ SD | ✅ MP3 | Tidak |
| Facebook | ✅ SD, HD | - | Tidak |
| Instagram | ✅ | ✅ | Ya (cookies) |
| Threads | ✅ | ✅ | Ya (cookies) |

### Instagram & Threads
Membutuhkan cookies dari browser:
1. Login Instagram di Chrome/Edge
2. Buka DevTools (F12) → Application → Cookies
3. Copy isi cookies
4. Paste ke input "Instagram Cookies" di app

---

## Architecture

```
┌─────────────────┐     ┌──────────────────┐
│   Android APK   │────▶│   API Server     │
│   (WebView)     │     │   (Node.js)      │
│                 │     │                  │
│  Dropdesk UI    │     │  yt-dlp          │
│  (React/Vite)   │     │  tikwm API       │
│                 │     │  ffmpeg          │
└─────────────────┘     └──────────────────┘
```

- **APK**: WebView wrapper yang load UI dari bundled assets
- **Server**: Node.js API yang handle inspect + download
- **Koneksi**: HTTP ke server (perlu 1 device/computer yang jalan server)

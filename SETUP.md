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


---

## Release Signing & Rotasi Kunci (Penting!)

**Kunci release TIDAK boleh disimpan di repo.** Sebelumnya `snapsave.keystore`
(dengan password `snapsave123`) ter-commit di git — siapa pun dengan akses repo
bisa menandatangani APK "resmi", yang langsung menembus lapisan anti-mod
(`SecurityGuard` memercayai sertifikat persis ini). Kunci itu sudah di-untrack
dan sekarang dikonfigurasi via env var / `local.properties` saja.

### Konfigurasi signing (build.gradle.kts membaca otomatis)

Prioritas: **env var → local.properties** (`android-app/local.properties`, file
ini sudah di-`.gitignore`).

**Lokal (dev):** tambahkan ke `android-app/local.properties`:
```properties
release.keystore.path=app/snapsave.keystore   # relatif ke android-app/ (seperti sdk.dir) atau absolut
release.keystore.password=...
release.key.alias=snapsave
release.key.password=...
```

**CI (GitHub Actions):** set 4 secrets di Settings → Secrets and variables →
Actions. Workflow release **otomatis skip** selama secret belum diisi (hanya
artifact debug yang dihasilkan):
- `RELEASE_KEYSTORE_BASE64` — isi file keystore dalam base64:
  ```bash
  base64 -w0 snapsave.keystore   # Linux/Mac
  certutil -encode snapsave.keystore tmp.b64   # Windows (hapus header CERTIFICATE BASE64)
  ```
- `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`

Tanpa konfigurasi, `./gradlew assembleRelease` **gagal dengan pesan jelas**
(dicegah mengeluarkan APK release tanpa tanda tangan yang diam-diam menonaktifkan
cek integritas).

### Rotasi kunci (jika terlanjur bocor / ingin ganti)

1. Generate keystore baru:
   ```bash
   keytool -genkeypair -v -keystore tubenime.keystore -alias tubenime      -keyalg RSA -keysize 2048 -validity 10000 -storepass <pass> -keypass <pass>
   ```
2. Update `local.properties` (lokal) dan GitHub Secrets (CI) dengan file/password
   baru.
3. **Tidak perlu edit kode**: `SecurityGuard.RELEASE_CERT_SHA256_HEX` kini diambil
   dari `BuildConfig.RELEASE_CERT_SHA256_HEX` yang di-generate `build.gradle.kts`
   langsung dari keystore yang dikonfigurasi — sidik jari selalu sinkron dengan
   kunci yang benar-benar dipakai menandatangani.
4. Verifikasi sidik jari APK hasil build:
   ```bash
   keytool -list -v -keystore <keystore> -alias <alias>
   # bandingkan SHA256 cert dengan nilai RELEASE_CERT_SHA256_HEX di
   # app/build/generated/source/buildConfig/release/com/tubenime/app/BuildConfig.java
   ```
5. Setelah mengganti kunci, **update semua perangkat yang menginstal build lama**
   (upgrade APK harus ditandatangani kunci yang sama).

# TubeNime — Install, Activate, Troubleshoot

TubeNime is distributed **outside Google Play** because YouTube's Terms of
Service forbid downloading their content, and Google Play has a strict
intellectual-property policy. The APK is signed with a long-lived release
certificate; the in-app **SecurityGuard** will mark any repackaged build as
`MODIFIED` and disable premium. If you downloaded TubeNime from anywhere
other than the official GitHub Releases page below, you are using an
unsupported build.

Official downloads: <https://github.com/rainkidz/dropdesk/releases/latest>

---

## 1. Install the APK

### 1.1 Enable "Install unknown apps"

Android blocks sideloaded APKs by default. You must allow your **browser** (or
file manager) to install them.

| Vendor        | Path                                                                                       |
| ------------- | ------------------------------------------------------------------------------------------ |
| Stock Android | Settings → **Apps** → **Special access** → **Install unknown apps** → pick browser → ON   |
| Samsung OneUI | Settings → **Biometrics and security** → **Install unknown apps**                          |
| Xiaomi/MIUI   | Settings → **Privacy** → **Special permissions** → **Install unknown apps**                |
| Oppo/ColorOS  | Settings → **Security** → **Install unknown apps**                                           |
| Vivo          | Settings → **Security & privacy** → **Install unknown apps**                               |
| Realme        | Settings → **Additional settings** → **Install unknown apps**                               |

### 1.2 Install

1. Open the downloaded `app-release.apk` (or `tubenime-release.apk`).
2. Tap **Install**. Wait for the confirmation.
3. Tap **Open** when done, or find **TubeNime** in your launcher.

> Tip: keep the APK file in case you need to reinstall later — installing
> the same signed APK over an existing install preserves your premium state.

---

## 2. Verify the build (recommended)

To make sure you have the genuine, signed APK and not a tampered copy, verify
the SHA-256 of the downloaded file:

```bash
sha256sum app-release.apk
```

Compare the hash against the value listed in the GitHub release notes. If they
differ, **do not install** — you have a modified APK that will be marked as
`MODIFIED` by the in-app guard.

Inside the app: **Settings → About & Motor** shows either
`Official signed build`, `Debug build (test mode allowed)`, or
`⚠️ Modified build`. Anything other than the first two means you should
re-install the official APK.

---

## 3. Activate Premium (optional)

TubeNime Premium is **a sideload licence**, not a Play Store subscription.

### 3.1 Get a code

Two payment channels (current prices are listed in the app):

- **Lokal (Indonesia)** — QRIS. Pay, send proof to the seller contact shown
  in the Premium screen, receive a one-month code by message.
- **International** — PayPal to the link in the Premium screen, include your
  preferred e-mail, receive the code by e-mail.

### 3.2 Redeem

1. Open the app → **Settings → Premium** (or tap **UPGRADE TO PRO**).
2. Scroll to **Redeem Code**.
3. Paste your code. Format:
   ```
   TN1-YYYYMM-XXXX-SSSSSSSS
   ```
   - `YYYYMM` — the month the licence is valid (e.g. `202610` = Oct 2026).
   - `XXXX` — random suffix (case-sensitive).
   - `SSSSSSSS` — HMAC signature, alphabet `ABCDEFGHJKMNPQRSTUVWXYZ23456789`
     (no `O`, `0`, `I`, `L`, `U`).
4. Tap **GO PRO**. The status should change to `PRO / Active (License) • s/d Okt 2026`.

Each licence is bound to **one device** (server-side activation, anti-share).
If you factory-reset your phone or change device, contact the seller — there
is no self-serve transfer (yet).

---

## 4. Auto-pay (optional)

If you have the **Bayar Otomatis** button on the Premium screen, the app is
talking to a server that accepts Midtrans payments (QRIS, e-wallet, virtual
account, credit card). The flow:

1. Tap **Bayar Otomatis** → choose 1/3/6/12 months.
2. The system opens the Snap payment page in your browser (Custom Tab).
3. Pay with QRIS / GoPay / OVO / DANA / ShopeePay / VA / kartu.
4. Return to the app — it polls the server and auto-redeems your code once
   the payment is confirmed (≤ 30 seconds).

If the auto-pay button is missing on your build, the maintainer has not
enabled the payment gateway for your distribution channel; use the manual
QRIS / PayPal flow above.

---

## 5. Updates

The app checks for new releases from GitHub on every Settings open
(throttled to once per 6 hours). When an update is available you'll see
a yellow **UPDATE AVAILABLE** banner above **PREMIUM STATUS**.

Tap the banner → browser opens the latest release page → download the new
APK → install over the existing one (data + premium state are preserved).

If you prefer to disable the update check (e.g. for a managed fleet),
build a custom APK without network access to `api.github.com`.

---

## 6. Troubleshooting

### 6.1 "Modified build"

You have a tampered APK. Uninstall it, download the official APK again from
the GitHub release, verify the SHA-256, then install.

### 6.2 "Kode tidak valid" / "BadSignature"

- Re-check every character (codes are case-sensitive).
- Make sure you removed any copy-paste whitespace or stray characters.
- The signature alphabet has no `O`, `0`, `I`, `L`, `U` — those are easy to
  confuse (`O` vs `0`, `I` vs `1`). The keygen deliberately excludes them.
- If the code still fails, contact the seller for a re-issue.

### 6.3 Download fails / "Video unavailable"

Some platforms block downloads from datacentre IPs or change their format
often. Try:

1. Paste the **direct URL** into a fresh browser tab and check if the
   platform plays it. If not, the original is gone.
2. For Instagram/Threads private posts, log in via **Settings → Login
   Cookies → Instagram** (Premium only) and try again.
3. For YouTube age-restricted videos, log in via YouTube cookies (Premium).

### 6.4 App won't open / keeps crashing

- Make sure Android version is ≥ 7.0 (API 24). TubeNime targets API 24+ only.
- If you have an older armv7 device (very rare in 2026), this build will
  not install — wait for a future fat-APK release.

### 6.5 Battery / data concerns

- **Settings → Downloads → Wi-Fi Only** — only download when on Wi-Fi.
- **Settings → Storage Location** — switch between `Downloads/TubeNime`
  and plain `Downloads`.
- The app runs `yt-dlp` + `ffmpeg` natively; both are started on demand and
  shut down when the queue empties.

---

## 7. Uninstall

Standard Android uninstall (Settings → Apps → TubeNime → Uninstall). The
app keeps no background service after this. Your premium licence is bound
to the device, so uninstalling invalidates it — you will receive a new code
if you reinstall later.

---

## 8. Reporting issues

Open an issue at <https://github.com/rainkidz/dropdesk/issues> with:

1. TubeNime version (`Settings → About & Motor`).
2. Android version and device model.
3. Platform you were trying to download from.
4. Logcat excerpt (if reproducible):

   ```bash
   adb logcat -d -s TubeNime:* PremiumManager:* LicenseManager:* BillingRepository:* AdsManager:* UpdateChecker:* > tubenime.log
   ```

---

## 9. License & credits

- TubeNime source: AGPL-3.0 (or compatible).
- Bundled: `yt-dlp` (Unlicense), `ffmpeg-kit` (LGPL 3), Material Components
  (Apache 2), Compose (Apache 2).
- Not affiliated with YouTube, TikTok, Instagram, Bilibili, Facebook, or
  Threads. All trademarks belong to their respective owners.
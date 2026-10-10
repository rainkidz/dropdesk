# TubeNime — Samsung Galaxy Store Submission Guide

Why Samsung: AdMob only reviews apps linked to a *supported* store, and
APKPure is not one of them. Galaxy Store is supported, free for
individual sellers, and big in Indonesia. Once the listing is live,
link it in AdMob → app review runs → full ad serving (including the
rewarded unit).

Canonical release to submit: **v4.5.2 (code 12)** or newer from
https://github.com/rainkidz/dropdesk/releases/latest
(Chaquopy 17 + Python 3.13 — first release with 16 KB page-size support,
which Galaxy Store mandates.)

---

## 0. Prerequisite: seller account (one-time, several days)

1. Create a Samsung account at https://seller.samsungapps.com/ → Sign Up
   Now → Sign in with Samsung Account. Set the country correctly
   **before** registering (cannot be changed later).
2. Register with Seller Portal (Private Seller is fine to start).
3. Apply for **commercial seller status** (required even for free apps):
   - Private seller: name must match your government ID + bank account
     holder name. PayPal is the easiest financial method.
   - Documents must be in English. Approval takes several days.
4. Keep the same signing key for everything (see §2).

## 1. Register the app

Seller Portal → red **Add New App** button → fill required (*) fields:

| Field | Value |
| --- | --- |
| App title | `TubeNime` |
| Package | `com.tubenime.app` |
| Category | general category (e.g. Tools / Video) — skip exclusives |
| Short + long description | **Variant A** from `docs/SUBMISSION.md` (no brand names) |
| Icon | `docs/landing/icon-512.png` |
| Screenshots | `docs/landing/store-shot-*.jpg` (1080×2100) |
| Feature graphic | `docs/landing/feature-1024x500.png` (if requested) |
| Privacy URL | `https://rainkidz.github.io/dropdesk/` |
| Support email | your active email (review notices go here) |
| Age rating | answer honestly (downloader that can open age-restricted content via user login — do not under-rate) |

Click **Save** before moving to the next tab (unsaved input is lost).

## 2. Upload binary — APK with OUR keystore (important)

- Upload the **APK** (`app-release.apk` from the GitHub release), **not**
  the AAB. Reason: AAB forces Galaxy Store-managed signing, which
  changes the signing certificate → breaks update continuity with
  GitHub/APKPure installs and complicates AdMob app identity. APK
  keeps our `snapsave.keystore` signature everywhere.
- Requirements (all met): target API ≥ 33 (we target 35), 64-bit
  binary included (arm64-v8a only is accepted).
- ⚠️ **16 KB page-size check**: since July 2026 Galaxy Store requires
  Android 15+ apps to support 16 KB pages. Our app bundles native
  libs (ffmpeg-kit, Chaquopy `.so`). Seller Portal checks this
  automatically on upload — if it rejects the binary for page size,
  stop and report back (fix = NDK rebuild flags, needs a new release).

## 3. Submit + link AdMob

1. Submit for review in Seller Portal (timeline: days, varies).
2. When the listing is live, copy its Galaxy Store URL.
3. AdMob console → Apps → Tubenime → Add store info → check
   **Samsung Galaxy Store** → search `com.tubenime.app` → link.
   App review starts automatically; rewarded + full serving unlock
   after approval.
4. Add the Galaxy Store URL as a mirror on the landing page
   (`docs/landing/index.html` hero buttons) so users have a
   one-tap install source.

## 4. Notes / risks

- Galaxy Store has its own IP review; a downloader app can be
  rejected like on Play. If rejected, read the reason: metadata
  wording fixes are cheap (resubmit same binary), binary-level
  rejection (page size, downloader policy) needs a code release.
- Never change the signing keystore. `snapsave.keystore` + passwords
  live in GitHub Secrets (`RELEASE_KEYSTORE_*`) and one local backup
  — losing it means the app can never be updated anywhere again.
- Each store release = same binary, new upload per store. Bump
  `versionCode`/`versionName` in `android-app/app/build.gradle.kts`
  only when shipping a new build (stores reject re-uploaded codes).

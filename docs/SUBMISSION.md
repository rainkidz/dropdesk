# TubeNime — APKPure & APKMirror Submission Guide

This is a step-by-step checklist for manually uploading `app-release.apk`
to the two major APK distribution sites. Run from the latest GitHub
Release page: https://github.com/rainkidz/dropdesk/releases/latest

---

## 0. Pre-flight

- [ ] You have an APKPure developer account: https://app.apppure.com/ (free)
- [ ] You have an APKMirror account: https://www.apkmirror.com/ (free)
- [ ] Download the APK + mapping.txt + checksum from the release page
- [ ] Compute the SHA-256 fingerprint of the APK so you can paste it into the form:

      ```bash
      sha256sum app-release.apk
      ```

- [ ] (Optional) Take fresh phone screenshots if the placeholders in `docs/landing/index.html` look stale.

---

## 1. App metadata (copy-paste)

**App name:** TubeNime

**Package name:** com.tubenime.app

**Version:** 4.4.0  (versionName from BuildConfig)

**Version code:** 9  (auto-increment per release)

**Min Android:** 7.0 (API 24)

**Target Android:** 15 (API 35)

**Architecture:** arm64-v8a only

**Category:** Tools  (or "Video Players & Editors" — APKPure accepts both)

**Short description (≤ 80 chars):**
```
Anime video downloader for YouTube, TikTok, IG, Bilibili, FB, Threads
```

**Long description (paste into both sites):**
```
Browse trending anime clips on YouTube, TikTok, Instagram, Bilibili,
Facebook, and Threads — and download the videos you love directly
to your phone.

FEATURES
• Trending anime feeds: one-tap access to today's anime hashtags
  across 6 platforms, randomised daily so you always see something
  fresh.
• Up to 1080p MP4: free tier up to 720p, PRO merges video and
  audio for full HD.
• Cookie login (PRO): drop in your YouTube or Instagram cookies
  to unlock private, age-restricted, or members-only downloads.
• Batch downloads (PRO): pull entire playlists and queues.
• Reward PRO: watch one short ad to unlock everything for 30
  minutes — no account, no payment, repeatable.
• Shōnen theme: distraction-free manga-style UI. Material You is
  deliberately off so the look stays consistent across devices.

PRIVACY
• No analytics SDK. No Google Play Services dependency for core
  features.
• Banner + interstitial ads fund the free tier. PRO reward
  sessions hide ads while active.
• The only outbound requests are to the platforms you browse,
  AdMob, and the update endpoint.

IMPORTANT — THIS IS A SIDELOAD APP
TubeNime is not on the Play Store because YouTube's Terms of Service
forbit downloading their content. Install the APK the same way you
would install NewPipe, Seal, or any other sideload video app:

1. Open the downloaded APK.
2. Allow your browser / file manager to install unknown apps.
3. Tap Install.

PRO is free: watch one short ad in Settings → PREMIUM STATUS to
unlock everything for 30 minutes. Visit the project page for
instructions: https://github.com/rainkidz/dropdesk

NOT AFFILIATED with YouTube, TikTok, Instagram, Bilibili, Facebook,
or Threads. All trademarks belong to their respective owners.
```

**Tags:**  video, downloader, anime, sideload, tools, yt-dlp, free

**Developer name:** rainkidz

**Developer email:** kiarikaira@gmail.com

**Privacy policy URL:** https://rainkidz.github.io/dropdesk/ (the landing
page itself acts as the policy; mirror to a dedicated page later if
APKPure rejects it)

**Website:** https://github.com/rainkidz/dropdesk

---

## 2. APKPure submission

URL: https://app.apppure.com/

1. Click **Upload APK** in the sidebar.
2. Drag `app-release.apk` (or click to browse). Wait for the SHA-256
   and basic info to auto-populate.
3. Fill the form using the metadata above. APKPure auto-detects the
   version code, min SDK, and target SDK from the APK.
4. Upload the icon: `docs/landing/icon-512.png` (512x512, generated
   from `designs/icon.png`; same artwork as the in-app launcher).
5. (Optional) Upload 3–5 phone screenshots (1080x2400 or similar).
6. Tick "I have the right to distribute this APK".
7. Submit. APKPure typically reviews within 24–72 hours for a new
   developer; later updates go through faster.

**APKPure-specific gotcha:** they sometimes reject apps with the word
"YouTube" in the title or description. If that happens, soften the
wording to "trending short-video platforms" and resubmit.

---

## 3. APKMirror submission

URL: https://www.apkmirror.com/apk-upload/

1. APKMirror requires you to be logged in and to verify your email.
2. Click **Upload an APK** → browse → select `app-release.apk`.
3. APKMirror parses the APK and shows a confirmation screen with
   package name, version, min/target SDK, and **a SHA-256 fingerprint
   field** that they verify automatically.
4. Fill in the metadata (same as above). APKMirror is stricter about:
   - **Source URL / Official site:** `https://rainkidz.github.io/dropdesk/`
     (NOT a Play Store URL — we are not on Play).
   - **Changelog:** paste the body of the GitHub release notes
     (`gh release view v4.4.0-rc1 --repo rainkidz/dropdesk --json
     body` gives you the markdown).
5. Upload a 512x512 PNG icon (`docs/landing/icon-512.png`, same as APKPure).
6. Optionally upload screenshots.
7. Tick the "I am the developer" checkbox. APKMirror cross-checks the
   signing certificate SHA-256 against the public Play Console record
   if the package was ever on Play. Since TubeNime never was, this
   check is skipped — APKMirror instead requires a public "official
   site" URL, which our landing page satisfies.
8. Submit. APKMirror manual review typically takes 24–48 hours.

**APKMirror-specific gotcha:** they reject anything that looks like a
repackage of a copyrighted app. We are an original codebase, so this
should not be an issue — but if they ask, point them to the GitHub
source.

---

## 4. Post-submission

Once both sites publish the listing, add the URLs to the landing page
(`docs/landing/index.html`):

- "All releases" pill button → also link to `https://app.apppure.com/`
  (search TubeNime) and `https://www.apkmirror.com/`
- Hero download button: GitHub remains the canonical "latest" link;
  APKPure/APKMirror are listed as mirrors.

After committing the URL update, the `Deploy landing page` workflow
will rebuild the page automatically (next push that touches
`docs/landing/**`).

---

## 5. Release flow recap

```
git tag v4.4.0           # when ready for a real release
git push origin v4.4.0    # → triggers Build TubeNime APK workflow
                          # → builds APK + AAB + mapping.txt
                          # → publishes GitHub Release (non-prerelease)
                          # → action available at /releases/latest
sha256sum app-release.apk  # compute new fingerprint
```

After the GitHub release is live, upload the same APK to APKPure and
APKMirror using the checklist above. Update the landing page once both
are accepted so users have a fallback mirror in case GitHub is blocked
in their region.

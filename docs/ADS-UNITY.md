# Unity Ads — Lapis 1 Monetisasi (Rewarded + Interstitial)

Kenapa Unity: AdMob rewarded no-fill (`Publisher data not found`) sebelum
app ter-link ke store yang didukung (Amazon Appstore — masih antre
verifikasi identitas). Unity tidak mensyaratkan store listing.

Arsitektur: `UnityAdsProvider.kt` (Game ID + placement) → dipanggil dari
`RewardedAdManager` (rewarded) dan `AdsManager` (interstitial). Banner tetap
AdMob. Selama `GAME_ID` masih placeholder, semua panggilan Unity no-op dan
alur AdMob lama yang jalan — aman untuk QA sebelum akun Unity ada.

SDK: `com.unity3d.ads:unity-ads:4.12.0` (API static klasik). JANGAN naik ke
4.19+: metadata Kotlin 2.1-nya tidak kompatibel dengan Kotlin 1.9 proyek ini
(build gagal `incompatible version of Kotlin`).

## Daftar + ambil ID (pemilik akun, sekali saja)

1. https://dashboard.unity3d.com → Sign up/in → **Monetization** →
   **Projects** → **New Project** → platform **Android** → nama mis.
   "TubeNime".
2. Project → **Ad Units** (placements auto dibuat untuk proyek baru):
   catat **Game ID** (angka, mis. `1234567`) + Placement ID rewarded
   (default `rewardedVideo`) + interstitial (default `video`).
3. **Test mode**: otomatis ikut build — debug build = iklan test Unity,
   release = iklan live. Jangan klik iklan live dari HP sendiri.
4. **Payout**: Organization → tambahkan payout (PayPal/bank). Ambang payout
   Unity ± $100 — konfirmasi angka saat daftar.
5. Tempel ke `UnityAdsProvider.kt` (3 konstanta `GAME_ID`,
   `REWARDED_PLACEMENT_ID`, `INTERSTITIAL_PLACEMENT_ID`) → rebuild →
   uji di HP: Settings → Watch Ad → PRO 30 menit harus aktif.

## Verifikasi yang sudah dilakukan (debug, tanpa ID)

- `assembleDebug` sukses; install + launch di Infinix hidup, logcat bersih.
- Provider log: "Unity Game ID belum diisi — provider nonaktif, fallback
  AdMob." → guard placeholder bekerja, tidak crash.

## Lapis 2 (nanti, setelah AdMob pulih)

Migrasi ke mediasi **AppLovin MAX**: AdMob (bidding) + Unity + Meta berebut
tiap impresi. Bukan sekarang — mediasi berisi satu network = kompleksitas
tanpa manfaat.

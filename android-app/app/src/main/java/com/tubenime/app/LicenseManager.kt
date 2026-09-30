package com.tubenime.app

import android.content.Context
import android.os.Build
import android.util.Log
import java.util.Calendar
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Lisensi premium bulanan untuk distribusi sideload (tanpa Play Billing).
 *
 * Alur: user bayar manual (QRIS lokal / PayPal internasional) → pemilik
 * generate kode via `tools/make_license.py` → user input kode di layar
 * Premium → aktif offline sampai akhir bulan kedaluwarsa.
 *
 * Format kode: `TN1-YYYYMM-XXXX-SSSSSSSS`
 * - `YYYYMM` kedaluwarsa (masih berlaku selama bulan itu berjalan).
 * - `XXXX` acak (unik per kode).
 * - `SSSSSSSS` MAC 40-bit = HMAC-SHA256(secret, "TN1|YYYYMM|XXXX"),
 *   di-encode alfabet Crockford (tanpa 0/O, 1/I/L/U).
 *
 * Anti-putar-jam: `license_last_seen_ms` mencatat waktu maju maksimum saat
 * lisensi terpantau valid; jam dimundurkan jauh ke belakang → ditolak.
 *
 * BATASAN JUJUR: secret ikut terkompilasi di APK (bisa diekstrak pembajak
 * gigih) dan kode tidak terikat perangkat (bisa dibagikan). Cukup untuk
 * skala manual; naik ke verifikasi server bila bocor/skala besar.
 */
object LicenseManager {

    private const val TAG = "LicenseManager"
    private const val VERSION = "TN1"
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    private const val PREF_KEY = "license_key"
    private const val PREF_EXPIRY = "license_expiry_yyyymm"
    private const val PREF_LAST_SEEN = "license_last_seen_ms"
    private const val LAST_SEEN_UPDATE_MS = 60L * 60 * 1000 // tulis max 1x/jam
    private const val ROLLBACK_TOLERANCE_MS = 24L * 60 * 60 * 1000 // toleransi 1 hari

    sealed interface RedeemResult {
        data object Ok : RedeemResult
        data object BadFormat : RedeemResult
        data object BadSignature : RedeemResult
        data object Expired : RedeemResult
        data object NotConfigured : RedeemResult
    }

    fun isConfigured(): Boolean = BuildConfig.LICENSE_HMAC_SECRET.isNotBlank()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PremiumManager.PREFS_NAME, Context.MODE_PRIVATE)

    /** Normalisasi input user: huruf besar, strip dipersatukan, spasi dibuang. */
    fun normalize(raw: String): String =
        raw.trim().uppercase().replace(Regex("[\\s_]+"), "").replace("—", "-").replace("–", "-")

    fun redeem(context: Context, rawKey: String): RedeemResult {
        if (!isConfigured()) return RedeemResult.NotConfigured
        val key = normalize(rawKey)
        val parts = key.split("-")
        if (parts.size != 4 || parts[0] != VERSION) return RedeemResult.BadFormat
        val (ver, expiry, rand, mac) = parts
        if (expiry.length != 6 || !expiry.all { it.isDigit() }) return RedeemResult.BadFormat
        if (rand.length != 4 || !rand.all { it in ALPHABET }) return RedeemResult.BadFormat
        if (mac.length != 8 || !mac.all { it in ALPHABET }) return RedeemResult.BadFormat
        if (!macEquals(expectedMac(ver, expiry, rand), mac)) return RedeemResult.BadSignature
        if (expiry.toInt() < currentYearMonth()) return RedeemResult.Expired
        prefs(context.applicationContext).edit()
            .putString(PREF_KEY, key)
            .putInt(PREF_EXPIRY, expiry.toInt())
            .putLong(PREF_LAST_SEEN, System.currentTimeMillis())
            .apply()
        return RedeemResult.Ok
    }

    /**
     * Identitas device yang stabil tapi anonim. Build.FINGERPRINT (vendor/
     * hardware/build) + ANDROID_ID (resettable pada factory reset, tapi
     * cukup unik per install normal) → SHA-256 hex.
     */
    fun deviceFingerprint(context: Context): String {
        val raw = "${Build.FINGERPRINT}|${android.provider.Settings.Secure.ANDROID_ID}"
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** True bila ada lisensi tersimpan yang masih berlaku (cek rollback jam). */
    fun isLicensed(context: Context): Boolean {
        if (!isConfigured()) return false
        val p = prefs(context.applicationContext)
        val expiry = p.getInt(PREF_EXPIRY, 0)
        if (expiry == 0 || expiry < currentYearMonth()) return false
        val now = System.currentTimeMillis()
        val lastSeen = p.getLong(PREF_LAST_SEEN, 0L)
        if (lastSeen != 0L && now < lastSeen - ROLLBACK_TOLERANCE_MS) {
            Log.w(TAG, "Clock appears rolled back — license suspended")
            return false
        }
        if (now - lastSeen > LAST_SEEN_UPDATE_MS) {
            p.edit().putLong(PREF_LAST_SEEN, now).apply()
        }
        return true
    }

    /** Label "Okt 2026" untuk UI, atau null bila tak ada lisensi. */
    fun licensedUntilLabel(context: Context): String? {
        val expiry = prefs(context.applicationContext).getInt(PREF_EXPIRY, 0)
        if (expiry == 0) return null
        val year = expiry / 100
        val month = expiry % 100
        if (month !in 1..12) return null
        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
        return "${months[month - 1]} $year"
    }

    fun clearLicense(context: Context) {
        prefs(context.applicationContext).edit()
            .remove(PREF_KEY).remove(PREF_EXPIRY).remove(PREF_LAST_SEEN).apply()
    }

    fun currentYearMonth(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 100 + (c.get(Calendar.MONTH) + 1)
    }

    private fun expectedMac(ver: String, expiry: String, rand: String): String {
        val msg = "$ver|$expiry|$rand"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(BuildConfig.LICENSE_HMAC_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(msg.toByteArray(Charsets.UTF_8))
        // 40 bit pertama → 8 char × 5 bit (Crockford base32).
        var acc = 0
        var bits = 0
        val out = StringBuilder()
        for (b in digest) {
            acc = (acc shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5 && out.length < 8) {
                bits -= 5
                out.append(ALPHABET[(acc shr bits) and 0x1F])
            }
            if (out.length == 8) break
        }
        return out.toString()
    }

    private fun macEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}

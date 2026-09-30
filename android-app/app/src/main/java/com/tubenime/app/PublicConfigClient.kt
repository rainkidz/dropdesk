package com.tubenime.app

import android.util.Log
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Klien konfigurasi publik (AdMob IDs dinamis) dari api-server.
 *
 * Endpoint: `GET {BILLING_BASE_URL}/api/config/public` → respons JSON
 * `{ admobBannerId, admobInterstitialId, minVersion, releasedAt, yyyymm, signature }`.
 * Signature = HMAC-SHA256(LICENSE_HMAC_SECRET, canonical(body tanpa signature))
 * hex 64 char. Klien verifikasi → pakai IDs server; bila tidak valid / offline
 * → fallback ke hardcoded (lihat [AdsManager] untuk default).
 */
object PublicConfigClient {

    private const val TAG = "PublicConfigClient"
    private const val JSON = "application/json; charset=utf-8"
    private const val SIG_LEN = 64 // hex SHA-256

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Hasil fetch publik. `config == null` artinya server tidak tersedia /
     * signature tidak valid / signature missing. Pemakai boleh pakai
     * `fromFallback()` untuk hardcoded IDs.
     */
    data class RemoteConfig(
        val admobBannerId: String,
        val admobInterstitialId: String,
        val minVersion: String,
        val releasedAt: String,
        val yyyymm: Int,
    )

    /** Fetch + verifikasi signature. Best-effort, no-block. */
    fun fetchAsync(
        onResult: (RemoteConfig?) -> Unit,
    ) {
        val base = BuildConfig.BILLING_BASE_URL.trimEnd('/')
        if (base.isBlank() || BuildConfig.LICENSE_HMAC_SECRET.isBlank()) {
            onResult(null)
            return
        }
        GlobalScope.launch(Dispatchers.IO) {
            val cfg = runCatching { fetchAndVerify(base) }.getOrNull()
            onResult(cfg)
        }
    }

    private fun fetchAndVerify(base: String): RemoteConfig? {
        val req = Request.Builder().url("$base/api/config/public").get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "config HTTP ${resp.code}")
                return null
            }
            val text = resp.body?.string().orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                Log.w(TAG, "config parse failed: ${e.message}")
                return null
            }
            val sig = json.optString("signature", "")
            if (!isHex64(sig)) {
                Log.w(TAG, "config signature missing/malformed")
                return null
            }
            // Bangun ulang canonical body TANPA signature, sort keys.
            val body = json.removeField("signature")
            if (body == null) return null
            val canonical = canonicalJson(body)
            if (!hmacEquals(canonical, sig)) {
                Log.w(TAG, "config signature mismatch")
                return null
            }
            val banner = body.optString("admobBannerId", "")
            val interstitial = body.optString("admobInterstitialId", "")
            if (!banner.matches(Regex("^ca-app-pub-\\d+/\\d+$")) ||
                !interstitial.matches(Regex("^ca-app-pub-\\d+/\\d+$"))
            ) {
                Log.w(TAG, "config ids malformed")
                return null
            }
            return RemoteConfig(
                admobBannerId = banner,
                admobInterstitialId = interstitial,
                minVersion = body.optString("minVersion", ""),
                releasedAt = body.optString("releasedAt", ""),
                yyyymm = body.optInt("yyyymm", 0),
            )
        }
    }

    private fun JSONObject.removeField(name: String): JSONObject? {
        if (!has(name)) return null
        // JSONObject tidak expose remove di Android; bikin salinan tanpa field.
        val out = JSONObject()
        val it = keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k != name) out.put(k, get(k))
        }
        return out
    }

    /** JSON kanonik: keys diurut alfabet, agar signature cocok dengan server. */
    private fun canonicalJson(obj: JSONObject): String {
        val keys = obj.keys().asSequence().toMutableList()
        keys.sort()
        val sb = StringBuilder("{")
        keys.forEachIndexed { idx, k ->
            if (idx > 0) sb.append(',')
            sb.append(JSONObject.quote(k)).append(':')
            val v = obj.opt(k)
            sb.append(when (v) {
                is Number, is Boolean -> v.toString()
                null -> "null"
                else -> JSONObject.quote(v.toString())
            })
        }
        sb.append('}')
        return sb.toString()
    }

    private fun hmacEquals(message: String, expectedHex: String): Boolean {
        val secret = BuildConfig.LICENSE_HMAC_SECRET.toByteArray(Charsets.UTF_8)
        if (secret.isEmpty()) return false
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val digest = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        if (hex.length != expectedHex.length) return false
        var diff = 0
        for (i in hex.indices) diff = diff or (hex[i].code xor expectedHex[i].code)
        return diff == 0
    }

    private fun isHex64(s: String): Boolean =
        s.length == SIG_LEN && s.all { it in '0'..'9' || it in 'a'..'f' }
}

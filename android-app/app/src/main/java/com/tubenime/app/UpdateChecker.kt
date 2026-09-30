package com.tubenime.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pemeriksa update aplikasi via GitHub Releases API publik.
 *
 * Endpoint: `https://api.github.com/repos/{owner}/{repo}/releases/latest`
 * Throttle: minimal 6 jam antara panggilan jaringan (cache di SharedPreferences).
 * Failure: silent — tidak pernah crash UI. Status "no_update" atau "error"
 * disimpan di prefs agar UI bisa menampilkan pesan yang tepat.
 *
 * BUKAN untuk distribusi Play Store (yang punya Play Console sendiri). Ini
 * untuk sideload: user unduh APK dari GitHub Releases.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val GITHUB_API = "https://api.github.com/repos/rainkidz/dropdesk/releases/latest"

    private const val PREF_NAME = "tubenime_update"
    private const val PREF_LAST_CHECK_MS = "update_last_check_ms"
    private const val PREF_LAST_VERSION = "update_last_version"
    private const val PREF_LAST_URL = "update_last_url"
    private const val PREF_LAST_NOTES = "update_last_notes"
    private const val PREF_LAST_STATE = "update_last_state" // available | up_to_date | error | rate_limited

    private const val THROTTLE_MS = 6L * 60 * 60 * 1000 // 6 jam

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    enum class State { Available, UpToDate, RateLimited, Error, NotConfigured }

    data class UpdateInfo(
        val currentVersion: String,
        val latestVersion: String,
        val htmlUrl: String,
        val notes: String,
    ) {
        /** True kalau latest > current (semver string compare). */
        val isNewer: Boolean get() = compareSemver(latestVersion, currentVersion) > 0
    }

    sealed interface Result {
        data class Ok(val info: UpdateInfo) : Result
        data object UpToDate : Result
        data object RateLimited : Result
        data class Failed(val reason: String) : Result
        data object Throttled : Result
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** Info update terakhir yang tersimpan (dipakai UI untuk tampilkan banner). */
    fun cachedInfo(context: Context): UpdateInfo? {
        val p = prefs(context)
        val v = p.getString(PREF_LAST_VERSION, null) ?: return null
        if (p.getString(PREF_LAST_STATE, null) != "available") return null
        val current = currentVersionName(context)
        if (compareSemver(v, current) <= 0) return null // sudah outdated, bersihkan
        return UpdateInfo(
            currentVersion = current,
            latestVersion = v,
            htmlUrl = p.getString(PREF_LAST_URL, null).orEmpty(),
            notes = p.getString(PREF_LAST_NOTES, null).orEmpty(),
        )
    }

    fun cachedState(context: Context): State {
        val p = prefs(context)
        val raw = p.getString(PREF_LAST_STATE, null) ?: return State.UpToDate
        return runCatching { State.valueOf(raw.replaceFirstChar { it.uppercase() }) }
            .getOrDefault(State.UpToDate)
    }

    /** Fetch update info. Throttled: kalau baru saja cek, return Throttled tanpa jaringan. */
    fun checkAsync(context: Context, onResult: (Result) -> Unit) {
        val now = System.currentTimeMillis()
        val last = prefs(context).getLong(PREF_LAST_CHECK_MS, 0L)
        if (now - last < THROTTLE_MS && prefs(context).contains(PREF_LAST_VERSION)) {
            onResult(Result.Throttled)
            return
        }
        GlobalScope.launch(Dispatchers.IO) {
            val r = fetchAndProcess(context)
            onResult(r)
        }
    }

    /** Reset cache — dipakai tombol "Check now" supaya selalu panggil API. */
    fun resetThrottle(context: Context) {
        prefs(context).edit().remove(PREF_LAST_CHECK_MS).apply()
    }

    private fun fetchAndProcess(context: Context): Result {
        val prefs = prefs(context)
        val current = currentVersionName(context)
        prefs.edit().putLong(PREF_LAST_CHECK_MS, System.currentTimeMillis()).apply()
        val req = Request.Builder()
            .url(GITHUB_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TubeNime/${current}")
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 403 || resp.code == 429 -> {
                        prefs.edit().putString(PREF_LAST_STATE, State.RateLimited.name.lowercase()).apply()
                        Result.RateLimited
                    }
                    resp.code == 404 -> {
                        // Repo belum punya release — anggap up-to-date, jangan ganggu user.
                        prefs.edit().putString(PREF_LAST_STATE, State.UpToDate.name.lowercase()).apply()
                        Result.UpToDate
                    }
                    !resp.isSuccessful -> {
                        Log.w(TAG, "GitHub API HTTP ${resp.code}")
                        prefs.edit().putString(PREF_LAST_STATE, State.Error.name.lowercase()).apply()
                        Result.Failed("HTTP ${resp.code}")
                    }
                    else -> {
                        val text = resp.body?.string().orEmpty()
                        val json = try { JSONObject(text) } catch (e: Exception) {
                            prefs.edit().putString(PREF_LAST_STATE, State.Error.name.lowercase()).apply()
                            return Result.Failed("bad_json")
                        }
                        val tag = json.optString("tag_name", "").trim().removePrefix("v")
                        val url = json.optString("html_url", "")
                        val rawNotes = json.optString("body", "")
                        if (tag.isEmpty()) {
                            prefs.edit().putString(PREF_LAST_STATE, State.Error.name.lowercase()).apply()
                            return Result.Failed("missing_tag")
                        }
                        val info = UpdateInfo(
                            currentVersion = current,
                            latestVersion = tag,
                            htmlUrl = url,
                            notes = rawNotes.take(400),
                        )
                        if (info.isNewer) {
                            prefs.edit()
                                .putString(PREF_LAST_VERSION, tag)
                                .putString(PREF_LAST_URL, url)
                                .putString(PREF_LAST_NOTES, rawNotes.take(400))
                                .putString(PREF_LAST_STATE, State.Available.name.lowercase())
                                .apply()
                            Result.Ok(info)
                        } else {
                            prefs.edit()
                                .remove(PREF_LAST_VERSION)
                                .remove(PREF_LAST_URL)
                                .remove(PREF_LAST_NOTES)
                                .putString(PREF_LAST_STATE, State.UpToDate.name.lowercase())
                                .apply()
                            Result.UpToDate
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "update check failed", e)
            prefs.edit().putString(PREF_LAST_STATE, State.Error.name.lowercase()).apply()
            Result.Failed(e.message ?: "network_error")
        }
    }

    private fun currentVersionName(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }

    /**
     * Compare dua versi "X.Y.Z" (extension suffix diabaikan). Return >0 bila
     * a > b, <0 bila a < b, 0 bila sama. Aman untuk input tanpa suffix.
     */
    private fun compareSemver(a: String, b: String): Int {
        val pa = a.split(Regex("[.\\-_]")).map { it.toIntOrNull() ?: 0 }
        val pb = b.split(Regex("[.\\-_]")).map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val ai = pa.getOrElse(i) { 0 }
            val bi = pb.getOrElse(i) { 0 }
            if (ai != bi) return ai - bi
        }
        return 0
    }
}

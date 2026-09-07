package com.tubenime.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * Client-side integrity checks that stop casually repackaged builds.
 *
 * Threat model: an attacker takes the official release APK, patches out the
 * premium gate, re-signs it with their own key and redistributes it as a
 * "premium unlocked" mod. Because the signature no longer matches our release
 * certificate, [isTampered] becomes true and every premium grant is refused.
 *
 * Debug/CI builds are exempt (debuggable == true), which is also why the test
 * toggle in [PremiumManager] can never unlock a shipped release APK.
 *
 * NOTE: this is defence-in-depth only. An attacker who recompiles the app from
 * source can delete these checks — permanent protection requires the
 * server-side validation in `artifacts/api-server` (see PremiumConfig).
 */
object SecurityGuard {

    private const val TAG = "SecurityGuard"

    /**
     * SHA-256 (hex, uppercase, no colons) of the RELEASE signing certificate.
     *
     * Injected by `build.gradle.kts` from the keystore actually configured for
     * release signing (BuildConfig.RELEASE_CERT_SHA256_HEX), so it always stays
     * in sync with the key used to sign the APK — rotating the keystore requires
     * no code change. Empty when no release signing key is configured, which
     * disables the check (see [releaseCheckConfigured]).
     *
     * Manual reference (not needed for rotation, only for inspection):
     *   keytool -list -v -keystore <keystore> -alias <alias>
     */
    val RELEASE_CERT_SHA256_HEX: String = BuildConfig.RELEASE_CERT_SHA256_HEX

    fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Release-cert check only runs once a hash has been embedded. */
    fun releaseCheckConfigured(): Boolean = RELEASE_CERT_SHA256_HEX.isNotBlank()

    /** SHA-256 fingerprints of the certificates the currently installed APK is signed with. */
    fun currentFingerprints(context: Context): List<String> {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners ?: emptyArray()
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
                    ?: emptyArray()
            }
            signatures.map { sha256Hex(it.toByteArray()) }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read signing certificates: ${e.message}")
            emptyList()
        }
    }

    /** True when the installed APK carries our release certificate. */
    fun isSignatureValid(context: Context): Boolean {
        if (!releaseCheckConfigured()) {
            Log.w(TAG, "RELEASE_CERT_SHA256_HEX is empty — signature check is DISABLED. " +
                "Fill it in before shipping a paid build.")
            return true
        }
        return InstallTrust.signaturesMatch(
            current = currentFingerprints(context),
            allowed = listOf(RELEASE_CERT_SHA256_HEX)
        )
    }

    /**
     * Whole-install trust decision. Debug builds (used for CI artifacts and
     * sideloaded testing) are trusted; every other install must be signed with
     * our release certificate.
     */
    fun isInstallTrusted(context: Context): Boolean =
        InstallTrust.installTrusted(
            debuggable = isDebuggable(context),
            checkConfigured = releaseCheckConfigured(),
            match = isSignatureValid(context)
        )

    /** True when the installed APK looks repackaged / re-signed. */
    fun isTampered(context: Context): Boolean = !isInstallTrusted(context)

    /** Short human-readable description used in Settings. */
    fun trustedBuildLabel(context: Context): String = when {
        isDebuggable(context) -> "Debug build (test mode allowed)"
        isSignatureValid(context) -> "Official signed build"
        else -> "⚠️ Modified build"
    }

    /** For debugging — lets the owner copy the current fingerprint into the constant. */
    fun fingerprintForLog(context: Context): String =
        currentFingerprints(context).firstOrNull() ?: "unavailable"

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }.uppercase()
    }
}

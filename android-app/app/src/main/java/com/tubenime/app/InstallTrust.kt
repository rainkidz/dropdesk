package com.tubenime.app

/**
 * Pure (Android-free) helpers that decide whether an install should be trusted.
 * Kept separate from [SecurityGuard] so the logic can be unit-tested on the JVM.
 */
object InstallTrust {

    /**
     * True when any currently-present signing certificate matches any allowed one.
     * A repackaged "mod premium" APK is re-signed with a different key, so no
     * current certificate will match the embedded release certificate.
     */
    fun signaturesMatch(current: Collection<String>, allowed: Collection<String>): Boolean {
        if (current.isEmpty() || allowed.isEmpty()) return false
        return current.any { candidate ->
            allowed.any { it.equals(candidate, ignoreCase = true) }
        }
    }

    /**
     * Overall install-trust decision:
     * - debuggable builds are trusted (developer builds / CI debug APKs);
     * - when no release certificate hash has been embedded yet the check is
     *   skipped (fail-open, but [SecurityGuard] logs loudly about it);
     * - otherwise the installed certificate(s) must match the release cert.
     */
    fun installTrusted(debuggable: Boolean, checkConfigured: Boolean, match: Boolean): Boolean =
        debuggable || !checkConfigured || match
}

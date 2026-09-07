package com.tubenime.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallTrustTest {

    private val releaseCert = "A7EA46732953F79D505FD3B0D49FAA70B1C2D164814A61F8B63EEDB483812024"

    @Test
    fun matchingSignatureIsTrusted() {
        assertTrue(InstallTrust.signaturesMatch(listOf(releaseCert), listOf(releaseCert)))
    }

    @Test
    fun repackagedSignatureIsRejected() {
        // A re-signed mod has a different (attacker) certificate.
        val attackerCert = "9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08"
        assertFalse(InstallTrust.signaturesMatch(listOf(attackerCert), listOf(releaseCert)))
    }

    @Test
    fun emptyCandidatesOrAllowListNeverMatch() {
        assertFalse(InstallTrust.signaturesMatch(emptyList(), listOf(releaseCert)))
        assertFalse(InstallTrust.signaturesMatch(listOf(releaseCert), emptyList()))
    }

    @Test
    fun comparisonIsCaseInsensitive() {
        assertTrue(InstallTrust.signaturesMatch(listOf(releaseCert.lowercase()), listOf(releaseCert)))
    }

    @Test
    fun debuggableBuildsAreAlwaysTrusted() {
        assertTrue(InstallTrust.installTrusted(debuggable = true, checkConfigured = true, match = false))
    }

    @Test
    fun unconfiguredCheckFailsOpen() {
        assertTrue(InstallTrust.installTrusted(debuggable = false, checkConfigured = false, match = false))
    }

    @Test
    fun releaseInstallRequiresMatchingCertificate() {
        assertTrue(InstallTrust.installTrusted(debuggable = false, checkConfigured = true, match = true))
        assertFalse(InstallTrust.installTrusted(debuggable = false, checkConfigured = true, match = false))
    }
}

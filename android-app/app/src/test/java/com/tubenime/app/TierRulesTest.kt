package com.tubenime.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TierRulesTest {

    private fun video(height: Int) = FormatChoice(
        id = "v$height",
        label = "${height}p",
        type = "video",
        ext = "mp4",
        quality = "${height}p",
        sizeBytes = null,
        height = height
    )

    private fun audio(bitrate: Int) = FormatChoice(
        id = "a$bitrate",
        label = "${bitrate}kbps",
        type = "audio",
        ext = "m4a",
        quality = "${bitrate}kbps",
        sizeBytes = null,
        bitrate = bitrate
    )

    // ── Video height ─────────────────────────────────────────

    @Test
    fun freeUserIsLimitedTo720p() {
        assertTrue(TierRules.isVideoAllowed(premium = false, height = 720))
        assertTrue(TierRules.isVideoAllowed(premium = false, height = 480))
        assertFalse(TierRules.isVideoAllowed(premium = false, height = 1080))
        assertFalse(TierRules.isVideoAllowed(premium = false, height = 2160))
    }

    @Test
    fun premiumUserSeesAllResolutions() {
        assertTrue(TierRules.isVideoAllowed(premium = true, height = 720))
        assertTrue(TierRules.isVideoAllowed(premium = true, height = 1080))
        assertTrue(TierRules.isVideoAllowed(premium = true, height = 2160))
    }

    @Test
    fun unknownHeightIsAllowedForEveryone() {
        assertTrue(TierRules.isVideoAllowed(premium = false, height = 0))
        assertTrue(TierRules.isVideoAllowed(premium = true, height = 0))
    }

    // ── Audio bitrate ────────────────────────────────────────

    @Test
    fun freeUserIsLimitedTo128kbps() {
        assertTrue(TierRules.isAudioAllowed(premium = false, bitrate = 128))
        assertFalse(TierRules.isAudioAllowed(premium = false, bitrate = 192))
        assertFalse(TierRules.isAudioAllowed(premium = false, bitrate = 320))
    }

    @Test
    fun premiumUserSeesAllAudioBitrates() {
        assertTrue(TierRules.isAudioAllowed(premium = true, bitrate = 320))
        assertTrue(TierRules.isAudioAllowed(premium = true, bitrate = 128))
    }

    @Test
    fun unknownBitrateIsAllowedForEveryone() {
        assertTrue(TierRules.isAudioAllowed(premium = false, bitrate = 0))
    }

    // ── Format list filtering ────────────────────────────────

    @Test
    fun filterFormatsKeepsEverythingForPremium() {
        val formats = listOf(video(2160), video(720), audio(320), audio(128))
        val result = TierRules.filterFormats(premium = true, formats)
        assertEquals(listOf("v2160", "v720", "a320", "a128"), result.map { it.id })
    }

    @Test
    fun filterFormatsDropsHighResolutionsAndBitratesForFreeUsers() {
        val formats = listOf(video(2160), video(720), audio(320), audio(128))
        val result = TierRules.filterFormats(premium = false, formats)
        assertEquals(listOf("v720", "a128"), result.map { it.id })
    }
}

package com.zlip.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSizeEstimateTest {
    @Test
    fun aMinuteAtTheCoverBitrateIsAboutSixteenMegabytes() {
        val bytes = MediaImport.estimatedBytes(60_000L, MediaImport.VIDEO_BITRATE)!!
        // 2 Mbit/s video + 128 kbit/s audio for 60 s, plus 10 % for the container.
        assertEquals(60L * (2_000_000 + 128_000) / 8 * 11 / 10, bytes)
        assertTrue(bytes in 15_000_000L..20_000_000L)
    }

    @Test
    fun anHourLongClipDoesNotOverflow() {
        val bytes = MediaImport.estimatedBytes(10 * 60 * 60_000L, MediaImport.VIDEO_BITRATE)!!
        assertTrue(bytes > 9_000_000_000L)
    }

    @Test
    fun anUnknownLengthIsNotEstimated() {
        assertNull(MediaImport.estimatedBytes(0L, MediaImport.VIDEO_BITRATE))
        assertNull(MediaImport.estimatedBytes(-1L, MediaImport.VIDEO_BITRATE))
        assertNull(MediaImport.estimatedBytes(60_000L, 0))
    }
}

package com.gareca.tv

import com.gareca.tv.epg.EpgManager
import com.gareca.tv.epg.EpgProgram
import com.gareca.tv.model.EventItem
import com.gareca.tv.model.StreamOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EpgManagerTest {

    @Before
    fun setup() {
        EpgManager.clear()
    }

    @Test
    fun testSyntheticScheduleGeneration() {
        val programs = EpgManager.getProgramsForChannel("Canal 7")
        assertNotNull(programs)
        assertTrue("Daily synthetic schedule should contain slots", programs.isNotEmpty())
        assertEquals("Should contain 8 standard slots across 24h", 8, programs.size)
    }

    @Test
    fun testCurrentProgramCalculationAndProgress() {
        val now = 1700000000000L
        val program = EpgProgram(
            title = "Noticiero Central",
            startEpochMillis = now - (30 * 60 * 1000L), // started 30 min ago
            endEpochMillis = now + (30 * 60 * 1000L)   // ends in 30 min (total 60 min)
        )

        assertTrue(program.isCurrentlyPlaying(now))
        assertEquals(50, program.getProgressPercent(now))
    }

    @Test
    fun testPutProgramsAndRetrieveCurrent() {
        val now = System.currentTimeMillis()
        val customPrograms = listOf(
            EpgProgram("Película de Acción", startEpochMillis = now - 1000L, endEpochMillis = now + 50000L)
        )
        EpgManager.putPrograms("HBO Plus", customPrograms)

        val current = EpgManager.getCurrentProgram("HBO Plus", now)
        assertNotNull(current)
        assertEquals("Película de Acción", current?.title)
    }

    @Test
    fun testFormattedSubtitleIntegration() {
        val event = EventItem(
            title = "Telefe",
            subtitle = "Transmisión en Vivo",
            streams = listOf(StreamOption("HLS", "http://example.com/stream.m3u8", "exo")),
            category = "Variedades"
        )
        val formatted = EpgManager.getFormattedSubtitle(event, event.subtitle)
        assertTrue("Formatted subtitle should contain time range brackets", formatted.startsWith("["))
        assertTrue("Formatted subtitle should include progress percentage", formatted.contains("%"))
    }
}

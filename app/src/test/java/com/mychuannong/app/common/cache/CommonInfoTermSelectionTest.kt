package com.mychuannong.app.common.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CommonInfoTermSelectionTest {
    private val fallTerm = "2026-2027-1"
    private val starts = mapOf(fallTerm to "2026-09-07 00:00:00", "2026-2027-2" to "2027-03-01 00:00:00")
    private val fallStart = LocalDate.of(2026, 9, 7)

    @Test
    fun `upcoming fall term is selected during the preview window`() {
        assertEquals(fallTerm, CommonInfo.getCurrentTerm(LocalDate.of(2026, 9, 5), starts))
        assertEquals(fallTerm, CommonInfo.getCurrentTerm(LocalDate.of(2026, 9, 6), starts))
        assertEquals(fallTerm, CommonInfo.getCurrentTerm(fallStart, starts))
    }

    @Test
    fun `fall teaching week boundaries are one based and monday anchored`() {
        assertEquals(1, CommonInfo.getCurrentWeekInt(fallTerm, LocalDate.of(2026, 9, 5), 20, starts))
        assertEquals(1, CommonInfo.getCurrentWeekInt(fallTerm, LocalDate.of(2026, 9, 6), 20, starts))
        assertEquals(1, CommonInfo.getCurrentWeekInt(fallTerm, LocalDate.of(2026, 9, 7), 20, starts))
        assertEquals(1, CommonInfo.getCurrentWeekInt(fallTerm, LocalDate.of(2026, 9, 13), 20, starts))
        assertEquals(2, CommonInfo.getCurrentWeekInt(fallTerm, LocalDate.of(2026, 9, 14), 20, starts))
        assertEquals("2026-09-07 00:00:00", starts[fallTerm])
    }

    @Test
    fun `preview does not move the whole summer into the next term`() {
        assertEquals("2025-2026-2", CommonInfo.getCurrentTerm(LocalDate.of(2026, 7, 15), starts))
        assertEquals("2025-2026-2", CommonInfo.getCurrentTerm(LocalDate.of(2026, 8, 30), starts))
        assertFalse(CommonInfo.hasTermStarted(fallTerm, LocalDate.of(2026, 9, 5), starts))
        assertTrue(CommonInfo.hasTermStarted(fallTerm, fallStart, starts))
    }
}

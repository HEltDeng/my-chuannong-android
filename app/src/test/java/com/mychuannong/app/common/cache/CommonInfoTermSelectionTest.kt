package com.mychuannong.app.common.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CommonInfoTermSelectionTest {
    private val fallTerm = "2026-2027-1"
    private val fallStart = LocalDate.of(2026, 9, 7)

    @Test
    fun `upcoming fall term is selected during the preview window`() {
        assertEquals(fallTerm, CommonInfo.getCurrentTerm(LocalDate.of(2026, 9, 5)))
        assertEquals(fallTerm, CommonInfo.getCurrentTerm(LocalDate.of(2026, 9, 6)))
        assertEquals(fallTerm, CommonInfo.getCurrentTerm(fallStart))
    }

    @Test
    fun `fall teaching week boundaries are one based and monday anchored`() {
        assertEquals(1, CommonInfo.getCurrentWeekInt(fallTerm, LocalDate.of(2026, 9, 5)))
        assertEquals(1, CommonInfo.getCurrentWeekInt(fallTerm, LocalDate.of(2026, 9, 6)))
        assertEquals(1, CommonInfo.getCurrentWeekInt(fallTerm, LocalDate.of(2026, 9, 7)))
        assertEquals(1, CommonInfo.getCurrentWeekInt(fallTerm, LocalDate.of(2026, 9, 13)))
        assertEquals(2, CommonInfo.getCurrentWeekInt(fallTerm, LocalDate.of(2026, 9, 14)))
        assertEquals("2026-09-07 00:00:00", CommonInfo.termMap[fallTerm])
    }

    @Test
    fun `preview does not move the whole summer into the next term`() {
        assertEquals("2025-2026-2", CommonInfo.getCurrentTerm(LocalDate.of(2026, 7, 15)))
        assertEquals("2025-2026-2", CommonInfo.getCurrentTerm(LocalDate.of(2026, 8, 30)))
        assertFalse(CommonInfo.hasTermStarted(fallTerm, LocalDate.of(2026, 9, 5)))
        assertTrue(CommonInfo.hasTermStarted(fallTerm, fallStart))
    }
}

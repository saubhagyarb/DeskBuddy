package com.saubh.deskbuddy.ui.standby.clock

import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClockHelpersTest {
    @Test
    fun `digits_givenArabicDefaultLocale_returnsAsciiDigits`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("0905", ClockTime(9, 5, 0).digits(is24h = true))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `digits_given24h_padsHourAndMinute`() {
        assertEquals("0905", ClockTime(9, 5, 0).digits(is24h = true))
        assertEquals("2359", ClockTime(23, 59, 0).digits(is24h = true))
    }

    @Test
    fun `digits_given12h_mapsMidnightAndNoonTo12`() {
        assertEquals("1200", ClockTime(0, 0, 0).digits(is24h = false))
        assertEquals("1230", ClockTime(12, 30, 0).digits(is24h = false))
        assertEquals("0115", ClockTime(13, 15, 0).digits(is24h = false))
    }

    @Test
    fun `angles_givenHalfPastThree_pointsHandsCorrectly`() {
        val t = ClockTime(15, 30, 0)
        assertEquals(105f, ClockAngles.hour(t))
        assertEquals(180f, ClockAngles.minute(t))
    }

    @Test
    fun `columns_givenTime_buildsFiveWideGlyphsWithGaps`() {
        val cols = DotMatrixFont.columns("12:34")
        assertEquals(5 + 5 + 1 + 5 + 5 + 4, cols.size)
        assertTrue(cols.all { it.size == 7 })
    }

    @Test
    fun `columns_givenEveryDigit_hasAGlyph`() {
        ('0'..'9').forEach { assertEquals(5, DotMatrixFont.columns(it.toString()).size, "digit $it") }
    }

    @Test
    fun `next_givenManySteps_staysWithinEightDp`() {
        val random = Random(7)
        var p = 0 to 0
        repeat(500) {
            p = BurnInWalk.next(p.first, p.second, random)
            assertTrue(p.first in -8..8 && p.second in -8..8)
        }
    }
}

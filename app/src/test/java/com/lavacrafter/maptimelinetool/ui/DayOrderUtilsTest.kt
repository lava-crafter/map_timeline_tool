package com.lavacrafter.maptimelinetool.ui

import com.lavacrafter.maptimelinetool.data.PointEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayOrderUtilsTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    private fun point(id: Long, time: ZonedDateTime) =
        PointEntity(id = id, timestamp = time.toInstant().toEpochMilli(), latitude = 0.0, longitude = 0.0, title = "p$id", note = "")

    @Test
    fun `today order follows the given day so it changes after midnight`() {
        val lateEvening = point(1, ZonedDateTime.of(2026, 10, 30, 23, 50, 0, 0, shanghai))
        val afterMidnight = point(2, ZonedDateTime.of(2026, 10, 31, 0, 10, 0, 0, shanghai))
        val laterMorning = point(3, ZonedDateTime.of(2026, 10, 31, 8, 0, 0, 0, shanghai))
        val points = listOf(laterMorning, afterMidnight, lateEvening)

        assertEquals(mapOf(1L to 1), buildTodayOrder(points, LocalDay(LocalDate.of(2026, 10, 30), shanghai)))
        assertEquals(mapOf(2L to 1, 3L to 2), buildTodayOrder(points, LocalDay(LocalDate.of(2026, 10, 31), shanghai)))
    }

    @Test
    fun `the same instant belongs to a different local day after a time zone change`() {
        val instant = point(1, ZonedDateTime.of(2026, 10, 31, 1, 0, 0, 0, shanghai))
        assertTrue(LocalDay(LocalDate.of(2026, 10, 31), shanghai).contains(instant.timestamp))
        assertFalse(LocalDay(LocalDate.of(2026, 10, 31), saoPaulo).contains(instant.timestamp))
        assertTrue(LocalDay(LocalDate.of(2026, 10, 30), saoPaulo).contains(instant.timestamp))
    }
}

package com.lavacrafter.maptimelinetool.ui

import com.lavacrafter.maptimelinetool.data.PointEntity
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class DayOrderUtilsTest {

    @Test
    fun `buildTodayOrder only includes today's points and keeps time order`() {
        val now = System.currentTimeMillis()
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis

        val points = listOf(
            point(id = 2L, timestamp = now + 2_000L),
            point(id = 1L, timestamp = now + 1_000L),
            point(id = 3L, timestamp = yesterday)
        )

        val result = buildTodayOrder(points)

        assertEquals(2, result.size)
        assertEquals(1, result[1L])
        assertEquals(2, result[2L])
    }

    private fun point(id: Long, timestamp: Long): PointEntity = PointEntity(
        id = id,
        timestamp = timestamp,
        latitude = 0.0,
        longitude = 0.0,
        title = "t$id",
        note = ""
    )
}

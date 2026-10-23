package com.hguide.memory

import java.time.Instant
import java.time.ZoneId

/** "작년 봄에", "지난달에", "며칠 전에" for the place-replay question. */
object RelativeTime {
    fun describe(thenMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val then = Instant.ofEpochMilli(thenMs).atZone(zone)
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val days = (nowMs - thenMs) / 86_400_000L
        val season = when (then.monthValue) { in 3..5 -> "봄"; in 6..8 -> "여름"; in 9..11 -> "가을"; else -> "겨울" }
        return when {
            days < 1 -> "오늘"
            days < 7 -> "며칠 전"
            days < 31 -> "지난달"
            now.year - then.year == 1 -> "작년 $season"
            now.year - then.year > 1 -> "${now.year - then.year}년 전 $season"
            else -> "${(days / 30).coerceAtLeast(1)}달 전"
        }
    }
}

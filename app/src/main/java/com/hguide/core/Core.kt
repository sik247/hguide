package com.hguide.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class PersonRole { PARENT, CHILD }
enum class TripContext { MORNING_COMMUTE, EVENING_HOME, LATE_NIGHT_HOME, WEEKEND, OTHER }
enum class DrivingEventType { LANE_CHANGE_NO_SIGNAL, HARSH_DECEL, ZONE_ENTER }
enum class PlaceType { HOME, WORK }
enum class FamilyState { OFF, ASKING, RECORDING, PAUSED }

/** Gear as the app sees it. PARK stands in for "parking brake engaged" (no parking-brake API in Vehicle SDK 2.3.2). */
enum class TripGear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }
enum class TurnSignal { OFF, LEFT, RIGHT }

data class GeoPoint(val lat: Double, val lng: Double)
data class Fix(val lat: Double, val lng: Double, val heading: Float, val timeMs: Long) {
    val point get() = GeoPoint(lat, lng)
}

/** Speech priority: coach cue > greeting > memory (spec §7). */
enum class SpeechPriority(val rank: Int) { MEMORY(1), GREETING(2), COACH(3) }

interface Clock {
    fun nowMs(): Long
    fun zone(): ZoneId = ZoneId.systemDefault()
    fun dateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs()), zone())
}

object SystemClockImpl : Clock {
    override fun nowMs() = System.currentTimeMillis()
}

/** Settable clock used by the demo console and tests. */
class MutableClock(private var now: Long = System.currentTimeMillis(), private val zone: ZoneId = ZoneId.systemDefault()) : Clock {
    override fun nowMs() = now
    override fun zone(): ZoneId = zone
    fun set(ms: Long) { now = ms }
    fun advance(ms: Long) { now += ms }
    fun setDateTime(dt: LocalDateTime) { now = dt.atZone(zone).toInstant().toEpochMilli() }
}

object Geo {
    private const val R = 6_371_000.0

    fun distanceM(a: GeoPoint, b: GeoPoint) = distanceM(a.lat, a.lng, b.lat, b.lng)

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * R * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Smallest absolute difference between two compass headings, 0..180. */
    fun headingDelta(a: Float, b: Float): Float {
        val d = ((b - a) % 360 + 540) % 360 - 180
        return kotlin.math.abs(d)
    }
}

/** A diarized piece of audio. text may be empty if the provider only separates speakers. */
data class Segment(val speakerId: String, val startMs: Long, val endMs: Long, val text: String)

fun log(tag: String, msg: String) {
    // Never pass transcript text here (spec §7).
    android.util.Log.d("hguide/$tag", msg)
}

/** Real time plus an adjustable offset: lets the demo console jump to "평일 08:10" while time keeps flowing. */
class OffsetClock : Clock {
    @Volatile private var offset = 0L
    override fun nowMs() = System.currentTimeMillis() + offset
    fun setDateTime(dt: LocalDateTime) { offset = dt.atZone(zone()).toInstant().toEpochMilli() - System.currentTimeMillis() }
    fun advance(ms: Long) { offset += ms }
}

package com.hguide.engine

import com.hguide.core.DrivingEventType
import com.hguide.core.Fix
import com.hguide.core.Geo
import com.hguide.core.GeoPoint
import com.hguide.core.TurnSignal

data class DifficultyZone(val name: String, val center: GeoPoint, val radiusM: Float)
data class RaisedEvent(val type: DrivingEventType, val ts: Long, val lat: Double, val lng: Double, val detail: String? = null)

/**
 * Turns fixes + turn-signal state into lesson events:
 *  - HARSH_DECEL: slowing by >= [harshDecelMps2] from at least [minSpeedForDecel]
 *  - LANE_CHANGE_NO_SIGNAL: heading shifts 12–40° within ~4 s at speed, with no turn signal on now or in the last 5 s
 *  - ZONE_ENTER: entering a configured difficulty zone (edge-triggered)
 */
class DrivingEventEngine(
    private val motion: MotionTracker,
    private val harshDecelMps2: Float = 3.0f,
    private val minSpeedForDecel: Float = 5f,
    private val minSpeedForLaneChange: Float = 8f,
) {
    private val window = ArrayDeque<Fix>()
    private var signalOn = false
    private var signalLastOnMs = Long.MIN_VALUE
    private var lastHarshMs = Long.MIN_VALUE
    private var lastLaneMs = Long.MIN_VALUE
    private val insideZones = mutableSetOf<String>()
    var zones: List<DifficultyZone> = emptyList()

    fun onTurnSignal(s: TurnSignal, nowMs: Long) {
        signalOn = s != TurnSignal.OFF
        if (signalOn) signalLastOnMs = nowMs
    }

    fun onFix(fix: Fix, nowMs: Long): List<RaisedEvent> {
        if (signalOn) signalLastOnMs = nowMs
        val prevSpeed = motion.onFix(fix)
        val out = mutableListOf<RaisedEvent>()

        if (prevSpeed >= minSpeedForDecel && motion.decelMps2 >= harshDecelMps2 && fix.timeMs - lastHarshMs > 5_000) {
            lastHarshMs = fix.timeMs
            out += RaisedEvent(DrivingEventType.HARSH_DECEL, fix.timeMs, fix.lat, fix.lng)
        }

        window.addLast(fix)
        while (window.isNotEmpty() && fix.timeMs - window.first().timeMs > 4_000) window.removeFirst()
        if (motion.speedMps >= minSpeedForLaneChange && window.size >= 2 && fix.timeMs - lastLaneMs > 10_000) {
            val delta = Geo.headingDelta(window.first().heading, fix.heading)
            val signalRecently = signalOn || nowMs - signalLastOnMs <= 5_000
            if (delta in 12f..40f && !signalRecently) {
                lastLaneMs = fix.timeMs
                out += RaisedEvent(DrivingEventType.LANE_CHANGE_NO_SIGNAL, fix.timeMs, fix.lat, fix.lng)
            }
        }

        for (z in zones) {
            val inside = Geo.distanceM(fix.point, z.center) <= z.radiusM
            if (inside && insideZones.add(z.name)) out += RaisedEvent(DrivingEventType.ZONE_ENTER, fix.timeMs, fix.lat, fix.lng, z.name)
            if (!inside) insideZones.remove(z.name)
        }
        return out
    }
}

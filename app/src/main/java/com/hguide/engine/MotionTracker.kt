package com.hguide.engine

import com.hguide.core.Fix
import com.hguide.core.Geo

/**
 * Neither Vehicle SDK 2.3.2 nor FusedLocation's LocationData exposes speed, so speed is derived from consecutive fixes.
 * TODO(verify-api): prefer a vehicle-speed signal if Pleos exposes one.
 */
class MotionTracker(private val stationaryBelowMps: Float = 0.8f) {
    var speedMps = 0f
        private set
    /** Deceleration of the latest fix pair in m/s² (positive = slowing down). */
    var decelMps2 = 0f
        private set
    private var last: Fix? = null

    val stationary get() = speedMps < stationaryBelowMps

    /** Returns the speed before this fix (for event detection). */
    fun onFix(fix: Fix): Float {
        val prevSpeed = speedMps
        val p = last
        if (p != null) {
            val dt = (fix.timeMs - p.timeMs) / 1000f
            if (dt in 0.2f..6f) {
                val v = (Geo.distanceM(p.point, fix.point) / dt).toFloat()
                decelMps2 = (prevSpeed - v) / dt
                speedMps = v
            }
        }
        last = fix
        return prevSpeed
    }

    fun reset() { speedMps = 0f; decelMps2 = 0f; last = null }
}

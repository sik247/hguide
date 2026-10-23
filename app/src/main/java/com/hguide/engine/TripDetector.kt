package com.hguide.engine

import com.hguide.core.Fix
import com.hguide.vehicle.VehicleSnapshot
import com.hguide.core.TripGear

sealed interface TripSignal {
    data class Started(val atMs: Long, val fix: Fix) : TripSignal
    data class Ended(val atMs: Long, val reason: Reason) : TripSignal
    enum class Reason { PARKED_LONG, APP_STOP }
}

/**
 * "Trip started" = app running + first location fix + parking state released (gear left PARK).
 * TODO(verify-api): use an ignition / power-state API if Pleos adds one (Vehicle 2.3.2 only has an IgnitionState enum, no accessor).
 * If gear is unavailable (UNKNOWN) it counts as released, i.e. app start + first fix.
 * "Trip ended" = parked continuously for [endParkedMs] (5 min), or the app stopping.
 */
class TripDetector(private val endParkedMs: Long = 5 * 60_000L) {
    var active = false
        private set
    private var fix: Fix? = null
    private var gear = TripGear.PARK
    private var parkedSince: Long? = null

    private val released get() = gear != TripGear.PARK

    fun onVehicle(s: VehicleSnapshot, nowMs: Long): TripSignal? {
        gear = s.gear
        parkedSince = if (s.gear == TripGear.PARK) (parkedSince ?: nowMs) else null
        return tryStart(nowMs)
    }

    fun onFix(f: Fix, nowMs: Long): TripSignal? {
        fix = f
        return tryStart(nowMs)
    }

    /** Call periodically (e.g. every 10 s). */
    fun onTick(nowMs: Long): TripSignal? {
        val since = parkedSince ?: return null
        if (active && nowMs - since >= endParkedMs) { active = false; return TripSignal.Ended(nowMs, TripSignal.Reason.PARKED_LONG) }
        return null
    }

    fun onAppStop(nowMs: Long): TripSignal? =
        if (active) { active = false; TripSignal.Ended(nowMs, TripSignal.Reason.APP_STOP) } else null

    private fun tryStart(nowMs: Long): TripSignal? {
        val f = fix ?: return null
        if (active || !released) return null
        active = true
        return TripSignal.Started(nowMs, f)
    }
}

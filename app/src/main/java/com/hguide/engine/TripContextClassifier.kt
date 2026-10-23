package com.hguide.engine

import com.hguide.core.Geo
import com.hguide.core.GeoPoint
import com.hguide.core.TripContext
import com.hguide.data.Place
import java.time.DayOfWeek
import java.time.LocalDateTime

data class ContextHours(val morningStart: Int = 5, val morningEnd: Int = 10, val eveningStart: Int = 17, val eveningEnd: Int = 21)

object TripContextClassifier {
    /**
     * Weekend wins. Otherwise (weekday):
     *  LATE_NIGHT_HOME : (near work OR heading home) and hour >= eveningEnd (21) or < morningStart (5)
     *  MORNING_COMMUTE : (near home OR heading to work) and hour in [morningStart, morningEnd)
     *  EVENING_HOME    : (near work OR heading home) and hour in [eveningStart, eveningEnd)
     */
    fun classify(
        start: GeoPoint,
        now: LocalDateTime,
        home: Place?,
        work: Place?,
        destination: GeoPoint?,
        hours: ContextHours = ContextHours(),
    ): TripContext {
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return TripContext.WEEKEND
        val h = now.hour
        fun near(p: Place?, pt: GeoPoint?) = p != null && pt != null && Geo.distanceM(pt, GeoPoint(p.lat, p.lng)) <= p.radiusM
        val nearHome = near(home, start)
        val nearWork = near(work, start)
        val toHome = near(home, destination)
        val toWork = near(work, destination)
        val lateNight = h >= hours.eveningEnd || h < hours.morningStart
        return when {
            (nearWork || toHome) && lateNight -> TripContext.LATE_NIGHT_HOME
            (nearHome || toWork) && h in hours.morningStart until hours.morningEnd -> TripContext.MORNING_COMMUTE
            (nearWork || toHome) && h in hours.eveningStart until hours.eveningEnd -> TripContext.EVENING_HOME
            else -> TripContext.OTHER
        }
    }
}

package com.hguide.demo

import com.hguide.AppContainer
import com.hguide.core.Fix
import com.hguide.core.GeoPoint
import com.hguide.core.OffsetClock
import com.hguide.core.TripGear
import com.hguide.core.TurnSignal
import com.hguide.engine.DifficultyZone
import com.hguide.navi.Destination
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.cos
import kotlin.math.sin

/** The "virtual console" for fake-gateway builds: scripts the car so the §9 scenarios can be run by tapping. */
class DemoActions(private val c: AppContainer) {
    companion object {
        val HOME = GeoPoint(37.4020, 127.1086)
        val WORK = GeoPoint(37.3948, 127.1112)
        val MERGE_ZONE = DifficultyZone("고속도로 합류 구간", GeoPoint(37.4100, 127.1086), 120f)
    }

    private val clock = c.clock as OffsetClock
    private val vehicle get() = c.fakeVehicle!!
    private val location get() = c.fakeLocation!!
    private var pos = HOME
    private var heading = 0f

    fun setTime(weekday: Boolean, hour: Int, minute: Int) {
        val day = if (weekday) DayOfWeek.WEDNESDAY else DayOfWeek.SATURDAY
        val date = LocalDate.now().with(TemporalAdjusters.nextOrSame(day))
        clock.setDateTime(LocalDateTime.of(date, LocalTime.of(hour, minute)))
        c.events.add("🕒 시간 ${if (weekday) "평일" else "주말"} %02d:%02d".format(hour, minute))
    }

    private fun emit() {
        clock.advance(1000)
        location.emit(Fix(pos.lat, pos.lng, heading, clock.nowMs()))
    }

    fun teleport(p: GeoPoint, label: String) {
        pos = p
        emit()
        c.events.add("📍 위치 → $label")
    }

    fun setDestination(p: GeoPoint?, name: String?) = c.fakeNavi!!.setDestination(p?.let { Destination(it, name) })
    fun gear(g: TripGear) { vehicle.setGear(g); c.events.add("⚙ 기어 ${g.name}") }
    fun signal(s: TurnSignal) { vehicle.setTurnSignal(s); c.events.add("↔ 방향지시등 ${s.name}") }
    fun answer(text: String?) { c.fakeStt?.answer(text) }

    /** Moves [seconds] at [mps], ending with heading [toHeading] (linear turn) — one fix per simulated second. */
    private suspend fun run(seconds: Int, mps: Double, toHeading: Float = heading) {
        val from = heading
        for (i in 1..seconds) {
            heading = from + (toHeading - from) * i / seconds
            val rad = Math.toRadians(heading.toDouble())
            pos = GeoPoint(pos.lat + mps * cos(rad) / 111_320.0, pos.lng + mps * sin(rad) / (111_320.0 * cos(Math.toRadians(pos.lat))))
            emit()
            delay(120)
        }
    }

    fun cruise(seconds: Int = 5, kmh: Int = 54) { c.scope.launch { run(seconds, kmh / 3.6) } }

    fun laneChangeNoSignal() = c.scope.launch {
        c.events.add("↗ 방향지시등 없이 진로 변경")
        run(3, 15.0); run(4, 15.0, toHeading = heading + 24f); heading = 0f
    }

    fun harshBrake() = c.scope.launch {
        c.events.add("🛑 급감속")
        run(3, 20.0); run(2, 5.0)
    }

    fun enterMergeZone() {
        c.engine.zones = listOf(MERGE_ZONE)
        c.scope.launch { pos = GeoPoint(MERGE_ZONE.center.lat - 0.0015, MERGE_ZONE.center.lng); heading = 0f; run(4, 40.0) }
    }

    fun startLesson(on: Boolean) { if (on) c.lesson.start("고속도로 합류 연습") else c.lesson.stop() }
}

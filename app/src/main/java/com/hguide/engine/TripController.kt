package com.hguide.engine

import com.hguide.core.Clock
import com.hguide.core.DrivingEventType
import com.hguide.core.EventLog
import com.hguide.core.Fix
import com.hguide.core.PlaceType
import com.hguide.core.TripContext
import com.hguide.data.DrivingEvent
import com.hguide.data.DrivingEventDao
import com.hguide.data.PersonDao
import com.hguide.data.PlaceDao
import com.hguide.data.SettingsStore
import com.hguide.data.Trip
import com.hguide.data.TripDao
import com.hguide.family.FamilyModeController
import com.hguide.location.LocationGateway
import com.hguide.memory.RecapResult
import com.hguide.memory.RecapService
import com.hguide.navi.NaviGateway
import com.hguide.vehicle.VehicleGateway
import com.hguide.vehicle.VehicleSnapshot
import com.hguide.voice.CoachSpeaker
import com.hguide.voice.GreetingSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Glue: gateway streams → trip detection → (greeting, family-mode question) at trip start, lesson cues during the
 * trip, and the recap (highlights → playlist) at trip end. All callbacks run on the scope's single dispatcher.
 */
class TripController(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val vehicle: VehicleGateway,
    private val location: LocationGateway,
    private val navi: NaviGateway,
    private val trips: TripDao,
    private val places: PlaceDao,
    private val drivingEvents: DrivingEventDao,
    private val people: PersonDao,
    private val settings: SettingsStore,
    private val detector: TripDetector,
    private val motion: MotionTracker,
    private val engine: DrivingEventEngine,
    private val guards: DrivingGuards,
    private val greeting: GreetingSpeaker,
    private val coach: CoachSpeaker,
    private val family: FamilyModeController,
    private val recap: RecapService,
    private val events: EventLog,
    private val tickMs: Long = 10_000,
) {
    private val _tripId = MutableStateFlow<Long?>(null)
    val currentTripId: StateFlow<Long?> = _tripId.asStateFlow()
    private val _context = MutableStateFlow<TripContext?>(null)
    val context: StateFlow<TripContext?> = _context.asStateFlow()
    private val _parked = MutableStateFlow(true)
    /** Screen interaction is allowed only while true (gear in PARK; stand-in for the parking brake). */
    val parked: StateFlow<Boolean> = _parked.asStateFlow()
    private val _stationary = MutableStateFlow(true)
    val stationary: StateFlow<Boolean> = _stationary.asStateFlow()
    private val _lastRecap = MutableStateFlow<RecapResult?>(null)
    val lastRecap: StateFlow<RecapResult?> = _lastRecap.asStateFlow()
    var lastFix: Fix? = null
        private set

    private var pendingStart = false

    fun start() {
        vehicle.start(); location.start(); navi.start()
        scope.launch { vehicle.state.collect { onVehicle(it) } }
        scope.launch { location.fixes.collect { onFix(it) } }
        scope.launch { while (true) { delay(tickMs); handle(detector.onTick(clock.nowMs())) } }
    }

    fun onVehicle(s: VehicleSnapshot) {
        val now = clock.nowMs()
        guards.turnSignalOn = s.turnSignal != com.hguide.core.TurnSignal.OFF
        engine.onTurnSignal(s.turnSignal, now)
        _parked.value = s.parked
        handle(detector.onVehicle(s, now))
        attemptPending()
    }

    fun onFix(f: Fix) {
        val now = clock.nowMs()
        lastFix = f
        val raised = engine.onFix(f, now)
        _stationary.value = motion.stationary
        for (e in raised) handleEvent(e)
        handle(detector.onFix(f, now))
        attemptPending()
    }

    /** App closing: ends the active trip so the recap runs. */
    fun onAppStop() = handle(detector.onAppStop(clock.nowMs()))

    /** Demo / manual: end the trip right now. */
    fun endTripNow() = handle(detector.onAppStop(clock.nowMs()))

    private fun handle(signal: TripSignal?) {
        when (signal) {
            is TripSignal.Started -> scope.launch { startTrip(signal) }
            is TripSignal.Ended -> endTrip()
            null -> Unit
        }
    }

    private suspend fun startTrip(s: TripSignal.Started) {
        val home = places.get(PlaceType.HOME)
        val work = places.get(PlaceType.WORK)
        val ctx = TripContextClassifier.classify(
            s.fix.point, clock.dateTime(), home, work, navi.destination.value?.point,
            ContextHours(settings.morningStartHour, settings.morningEndHour, settings.eveningStartHour, settings.eveningEndHour),
        )
        val id = trips.insert(Trip(startedAt = s.atMs, context = ctx))
        _tripId.value = id
        _context.value = ctx
        events.add("🚗 주행 시작 — ${ctx.name}")
        pendingStart = true
        attemptPending()
    }

    /** Greeting and the family-mode question only happen while stationary. */
    private fun attemptPending() {
        val id = _tripId.value ?: return
        if (!pendingStart || !motion.stationary) return
        pendingStart = false
        val ctx = _context.value ?: TripContext.OTHER
        scope.launch {
            greeting.maybeGreet(ctx, stationary = true)
            val riders = people.all()
            if (riders.isNotEmpty() && _tripId.value == id) family.beginAsking(id, riders)
        }
    }

    private fun handleEvent(e: RaisedEvent) {
        val id = _tripId.value ?: return
        if (e.type == DrivingEventType.HARSH_DECEL) guards.lastHarshDecelMs = clock.nowMs()
        scope.launch {
            drivingEvents.insert(DrivingEvent(tripId = id, type = e.type, ts = e.ts, lat = e.lat, lng = e.lng))
            coach.cue(e.type, id, clock.nowMs())
        }
    }

    private fun endTrip() {
        val id = _tripId.value ?: return
        _tripId.value = null
        pendingStart = false
        family.stop()
        events.add("🏁 주행 종료")
        scope.launch { _lastRecap.value = recap.finishTrip(id, navi.destination.value?.name) }
    }
}

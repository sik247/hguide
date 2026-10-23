package com.hguide

import com.hguide.audio.Wav
import com.hguide.core.DrivingEventType
import com.hguide.core.FamilyState
import com.hguide.core.Fix
import com.hguide.core.GeoPoint
import com.hguide.core.TripContext
import com.hguide.core.TripGear
import com.hguide.core.TurnSignal
import com.hguide.data.MemoryClip
import com.hguide.data.Trip
import com.hguide.vehicle.VehicleSnapshot
import com.hguide.voice.GreetResult
import com.hguide.voice.SampleConversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.sin

/** The §9 emulator scenarios, run headlessly against the fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScenarioTests {
    /** advanceUntilIdle() ignores backgroundScope work, so scenarios run in a plain child-less scope on the test scheduler. */
    private fun TestScope.appScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    private fun sample(n: String) = SampleConversation(n, Wav.readPcm(File("../sample/$n.wav")), SampleConversation.parseLabels(File("../sample/$n.labels.json").readText()))

    private fun fix(w: World, p: GeoPoint, heading: Float = 0f) = Fix(p.lat, p.lng, heading, w.clock.nowMs())

    /** Simulates driving with one fix per second; returns the new position. */
    private fun drive(w: World, from: GeoPoint, secs: Int, mps: Double, h0: Float, h1: Float = h0): GeoPoint {
        var p = from
        for (i in 1..secs) {
            val h = h0 + (h1 - h0) * i / secs
            val r = Math.toRadians(h.toDouble())
            p = GeoPoint(p.lat + mps * cos(r) / 111_320.0, p.lng + mps * sin(r) / (111_320.0 * cos(Math.toRadians(p.lat))))
            w.clock.advance(1000)
            w.controller.onFix(fix(w, p, h))
        }
        return p
    }

    // ---- S1 ------------------------------------------------------------------------------------------------------

    @Test fun s1_morning_greeting_plays_once_and_not_again_on_restart() = runTest {
        val w = World(appScope()).also { it.seedFamily() } // 평일(수) 08:10
        w.controller.onVehicle(VehicleSnapshot(gear = TripGear.PARK))
        w.controller.onFix(fix(w, World.HOME))
        w.controller.onVehicle(VehicleSnapshot(gear = TripGear.DRIVE)) // parking released
        advanceUntilIdle()

        assertEquals(TripContext.MORNING_COMMUTE, w.controller.context.value)
        assertEquals(1, w.greetingLogs.rows.size)
        assertEquals(TripContext.MORNING_COMMUTE, w.greetingLogs.rows[0].context)
        assertEquals("exactly one greeting played (아빠's approved AI voice)", 1, w.player.played.size)

        w.controller.onAppStop()
        w.controller.onVehicle(VehicleSnapshot(gear = TripGear.DRIVE)) // "restart" the same day
        advanceUntilIdle()
        assertEquals("no second greeting", 1, w.greetingLogs.rows.size)
        assertEquals(1, w.player.played.size)
    }

    // ---- S2 ------------------------------------------------------------------------------------------------------

    @Test fun s2_late_night_at_work_gets_overtime_greeting() = runTest {
        val w = World(appScope()).also { it.seedFamily() }
        w.clock.setDateTime(LocalDateTime.of(2026, 10, 21, 22, 30))
        w.controller.onVehicle(VehicleSnapshot(gear = TripGear.PARK))
        w.controller.onFix(fix(w, World.WORK))
        w.controller.onVehicle(VehicleSnapshot(gear = TripGear.DRIVE))
        advanceUntilIdle()

        assertEquals(TripContext.LATE_NIGHT_HOME, w.controller.context.value)
        assertEquals(TripContext.LATE_NIGHT_HOME, w.greetingLogs.rows.single().context)
        assertTrue(w.greetingLogs.rows.single().text.length <= 40)
    }

    @Test fun greeting_is_never_played_while_moving() = runTest {
        val w = World(appScope()).also { it.seedFamily() }
        assertEquals(GreetResult.NotStationary, w.greeting.maybeGreet(TripContext.MORNING_COMMUTE, stationary = false))
        assertEquals(0, w.greetingLogs.rows.size)
    }

    // ---- S3 ------------------------------------------------------------------------------------------------------

    @Test fun s3_one_refusal_creates_no_audio_files() = runTest {
        val w = World(appScope(), pcm = Wav.tone(60_000, 300.0)).also { it.seedFamily() }
        val tripId = w.trips.insert(Trip(startedAt = w.clock.nowMs()))
        w.stt.enqueue("응", "아니", "응") // 아빠 yes, 엄마 no, 나 yes
        w.family.beginAsking(tripId, w.people.all())
        advanceTimeBy(120_000); runCurrent()

        assertEquals(FamilyState.OFF, w.family.state.value)
        assertEquals(0, w.chunks.rows.size)
        assertTrue("no audio file on disk", File(w.dir, "audio").walk().none { it.isFile })
        assertFalse("recorder refuses without full consent", w.recorder.start(tripId))
    }

    @Test fun s3_silence_counts_as_refusal() = runTest {
        val w = World(appScope(), pcm = Wav.tone(60_000, 300.0)).also { it.seedFamily() }
        val tripId = w.trips.insert(Trip(startedAt = w.clock.nowMs()))
        w.stt.enqueue("응", null, "응")
        w.family.beginAsking(tripId, w.people.all())
        advanceTimeBy(60_000); runCurrent()
        assertEquals(FamilyState.OFF, w.family.state.value)
        assertEquals(0, w.chunks.rows.size)
    }

    @Test fun s3_full_consent_records_and_voice_command_stops_it() = runTest {
        val w = World(appScope(), pcm = Wav.tone(120_000, 300.0)).also { it.seedFamily() }
        val tripId = w.trips.insert(Trip(startedAt = w.clock.nowMs()))
        w.stt.enqueue("응", "네", "좋아")
        w.family.beginAsking(tripId, w.people.all())
        runCurrent()
        assertEquals(FamilyState.RECORDING, w.family.state.value)

        advanceTimeBy(65_000); runCurrent()
        assertEquals(2, w.chunks.rows.size)
        assertTrue(w.trips.get(tripId)!!.familyMode)
        assertEquals(setOf("dad", "mom", "me"), w.trips.get(tripId)!!.consentPersonIds.toSet())

        w.stt.enqueue("기록 그만")
        advanceTimeBy(1_000); runCurrent()
        assertEquals(FamilyState.OFF, w.family.state.value)
        advanceTimeBy(120_000); runCurrent()
        assertEquals("nothing recorded after stop", 2, w.chunks.rows.size)
    }

    // ---- S4 ------------------------------------------------------------------------------------------------------

    @Test fun s4_pipeline_from_audio_to_three_clips_without_the_scolding() = runTest {
        val expected = org.json.JSONObject(File("../sample/expected_highlights.json").readText())
        val all = mutableListOf<MemoryClip>()
        val excluded = expected.getJSONArray("must_exclude").getString(0)

        for (name in listOf("conversation1", "conversation2")) {
            val s = sample(name)
            val w = World(appScope(), pcm = s.pcm, samples = listOf(s)).also { it.seedFamily() }
            val tripId = w.trips.insert(Trip(startedAt = w.clock.nowMs()))
            w.consent.begin(tripId, listOf("dad", "mom", "me"))
            listOf("dad", "mom", "me").forEach { w.consent.respond(tripId, it, true) }
            assertTrue(w.recorder.start(tripId))
            advanceTimeBy(61_000); runCurrent()
            w.recorder.stop()

            val rows = w.utterances.forTrip(tripId)
            assertEquals("every labelled line transcribed", s.segments.size, rows.size)
            assertTrue("speakers matched to enrolled people", rows.none { it.personId == null })
            val clips = w.recap.finishTrip(tripId).clips
            clips.forEach { c ->
                assertTrue(File(c.audioPath).exists())
                val ms = c.endMs - c.startMs
                assertTrue("clip $ms ms within 5..20 s", ms in 5_000..20_000)
                assertTrue(c.title.length <= 12)
                val covered = rows.filter { it.startMs >= c.startMs && it.endMs <= c.endMs }.joinToString(" ") { it.text }
                assertFalse("scolding excluded", covered.contains(excluded))
            }
            all += clips
        }

        assertEquals(3, all.size)
        val want = expected.getJSONArray("highlights")
        assertEquals(want.length(), all.size)
        for (i in 0 until want.length()) {
            val e = want.getJSONObject(i)
            val match = all.firstOrNull { it.startMs == e.getLong("start_ms") && it.emotionTag == e.getString("emotion") }
            assertNotNull("expected ${e.getString("emotion")} clip at ${e.getLong("start_ms")}", match)
        }
    }

    // ---- S5 ------------------------------------------------------------------------------------------------------

    private suspend fun World.addClip(p: GeoPoint, title: String): MemoryClip {
        val wav = File(dir, "$title.wav").also { Wav.write(it, Wav.tone(6_000, 330.0)) }
        val clip = MemoryClip(tripId = 1, personId = "dad", title = title, summary = "아빠: 잘했어", emotionTag = "proud", audioPath = wav.path,
            startMs = 0, endMs = 6_000, lat = p.lat, lng = p.lng, placeName = "고속도로", createdAt = clock.nowMs() - 400L * 86_400_000L)
        return clip.copy(id = clips.insert(clip))
    }

    @Test fun s5_asks_first_plays_on_yes_then_not_again_the_same_day() = runTest {
        val w = World(appScope()).also { it.seedFamily() }
        val spot = GeoPoint(37.4100, 127.1086)
        val far = GeoPoint(37.3000, 127.0000)
        val clip = w.addClip(spot, "A")
        w.replay.start(); runCurrent()

        w.location.emit(fix(w, far)); runCurrent()
        assertEquals(1, w.replay.registeredAlerts)

        w.stt.enqueue("응")
        w.location.emit(fix(w, spot)); advanceUntilIdle()
        assertTrue("question asked first", w.tts.spoken.any { it.contains("들어볼까요") })
        assertEquals("then the clip played", 1, w.player.played.size)
        assertNotNull(w.clips.get(clip.id)!!.lastPlayedAt)

        w.location.emit(fix(w, far)); w.location.emit(fix(w, spot)); advanceUntilIdle()
        assertEquals("same place again: no replay (30-day cooldown)", 1, w.player.played.size)
        assertEquals("and no second question", 1, w.tts.spoken.count { it.contains("들어볼까요") })
    }

    @Test fun s5_nothing_plays_with_turn_signal_on() = runTest {
        val w = World(appScope()).also { it.seedFamily() }
        val spot = GeoPoint(37.4100, 127.1086)
        w.addClip(spot, "B")
        w.replay.start(); runCurrent()
        w.location.emit(fix(w, GeoPoint(37.3000, 127.0000))); runCurrent()

        w.controller.onVehicle(VehicleSnapshot(gear = TripGear.DRIVE, turnSignal = TurnSignal.LEFT))
        w.stt.enqueue("응")
        w.location.emit(fix(w, spot)); advanceUntilIdle()
        assertTrue(w.tts.spoken.none { it.contains("들어볼까요") })
        assertEquals(0, w.player.played.size)
    }

    @Test fun s5_declining_does_not_play_or_start_the_cooldown() = runTest {
        val w = World(appScope()).also { it.seedFamily() }
        val spot = GeoPoint(37.4100, 127.1086)
        val clip = w.addClip(spot, "C")
        w.replay.start(); runCurrent()
        w.location.emit(fix(w, GeoPoint(37.3000, 127.0000))); runCurrent()
        w.stt.enqueue("아니")
        w.location.emit(fix(w, spot)); advanceUntilIdle()
        assertEquals(0, w.player.played.size)
        assertNull(w.clips.get(clip.id)!!.lastPlayedAt)
    }

    @Test fun s5_only_the_nearest_clips_get_alerts() = runTest {
        val w = World(appScope()).also { it.seedFamily() }
        repeat(30) { i -> w.addClip(GeoPoint(37.40 + i * 0.001, 127.10), "clip$i") }
        w.replay.sync(GeoPoint(37.40, 127.10))
        assertEquals(w.replay.maxAlerts, w.replay.registeredAlerts)
    }

    // ---- S6 ------------------------------------------------------------------------------------------------------

    private fun startTripAt(w: World, p: GeoPoint) {
        w.controller.onVehicle(VehicleSnapshot(gear = TripGear.PARK))
        w.controller.onFix(fix(w, p))
        w.controller.onVehicle(VehicleSnapshot(gear = TripGear.DRIVE))
    }

    @Test fun s6_direction_change_without_signal_gives_one_coaching_line_in_the_parents_voice() = runTest {
        val w = World(appScope()).also { it.seedFamily() }
        w.lesson.start("차선 변경")
        val start = GeoPoint(37.45, 127.15) // nowhere near home/work: no greeting
        startTripAt(w, start); advanceUntilIdle()
        w.player.played.clear(); w.tts.spoken.clear()

        var p = drive(w, start, 3, 15.0, 0f)
        p = drive(w, p, 4, 15.0, 0f, 24f)
        advanceUntilIdle()

        assertEquals(1, w.drivingEvents.rows.count { it.type == DrivingEventType.LANE_CHANGE_NO_SIGNAL })
        assertEquals("one coaching sentence, as 아빠's AI voice", 1, w.player.played.size)
        assertTrue(w.player.played.single().path.contains("cache/dad"))
    }

    @Test fun s6_no_coaching_event_when_the_turn_signal_was_used() = runTest {
        val w = World(appScope()).also { it.seedFamily() }
        w.lesson.start()
        val start = GeoPoint(37.45, 127.15)
        startTripAt(w, start); advanceUntilIdle()
        w.controller.onVehicle(VehicleSnapshot(gear = TripGear.DRIVE, turnSignal = TurnSignal.LEFT))
        var p = drive(w, start, 3, 15.0, 0f)
        p = drive(w, p, 4, 15.0, 0f, 24f)
        advanceUntilIdle()
        assertTrue(w.drivingEvents.rows.none { it.type == DrivingEventType.LANE_CHANGE_NO_SIGNAL })
    }

    @Test fun s6_coaching_is_silent_when_no_lesson_is_running() = runTest {
        val w = World(appScope()).also { it.seedFamily() }
        val start = GeoPoint(37.45, 127.15)
        startTripAt(w, start); advanceUntilIdle()
        w.player.played.clear(); w.tts.spoken.clear()
        var p = drive(w, start, 3, 15.0, 0f)
        drive(w, p, 4, 15.0, 0f, 24f)
        advanceUntilIdle()
        assertEquals(0, w.player.played.size)
        assertTrue(w.tts.spoken.isEmpty())
    }

    // ---- S7 ------------------------------------------------------------------------------------------------------

    @Test fun s7_revoking_approval_deletes_the_profile_and_falls_back_to_default_tts() = runTest {
        val w = World(appScope()).also { it.seedFamily() }
        val first = w.greeting.maybeGreet(TripContext.MORNING_COMMUTE, stationary = true) as GreetResult.Played
        assertTrue("approved: AI voice", first.viaClone)
        assertEquals(1, w.player.played.size)
        assertTrue(w.clone.cacheDir("dad").exists())

        w.enrollment.revokeClone("dad")

        assertFalse(w.clone.hasProfile("dad"))
        assertFalse("cached clone audio deleted", w.clone.cacheDir("dad").exists())
        assertFalse("embedding file deleted", File(w.dir, "voice_profiles/dad.emb").exists())
        assertFalse(w.people.get("dad")!!.voiceCloneApproved)
        assertNotNull(w.profiles.get("dad")!!.revokedAt)
        assertFalse(w.enrollment.cloneAllowed("dad"))

        // next greeting (different context, so it is not skipped as "already done today")
        w.clock.setDateTime(LocalDateTime.of(2026, 10, 21, 18, 30))
        val after = w.greeting.maybeGreet(TripContext.EVENING_HOME, stationary = true) as GreetResult.Played
        assertFalse("falls back to default TTS", after.viaClone)
        assertTrue(w.tts.spoken.contains(after.text))
        assertEquals("no new cloned audio played", 1, w.player.played.size)

        // coaching falls back too
        w.lesson.start()
        val cue = w.coach.cue(DrivingEventType.HARSH_DECEL, tripId = 1, nowMs = w.clock.nowMs())
        assertNotNull(cue)
        assertEquals(cue, w.tts.spoken.last())
        assertEquals(1, w.player.played.size)
    }

    @Test fun clone_is_never_used_without_the_owners_approval() = runTest {
        val w = World(appScope()).also { it.seedFamily(approveDad = false) }
        val r = w.greeting.maybeGreet(TripContext.MORNING_COMMUTE, stationary = true) as GreetResult.Played
        assertFalse(r.viaClone)
        assertFalse(w.clone.hasProfile("dad"))
        assertEquals(0, w.player.played.size)
    }

    @Test fun own_recorded_greeting_beats_the_ai_voice() = runTest {
        val w = World(appScope()).also { it.seedFamily() }
        val mine = File(w.dir, "mine.wav").also { Wav.write(it, Wav.tone(2_000, 440.0)) }
        w.settings.setCustomGreetingPath("dad", TripContext.MORNING_COMMUTE, mine.path)
        val r = w.greeting.maybeGreet(TripContext.MORNING_COMMUTE, stationary = true) as GreetResult.Played
        assertTrue(r.recorded)
        assertEquals(listOf(mine), w.player.played)
    }
}

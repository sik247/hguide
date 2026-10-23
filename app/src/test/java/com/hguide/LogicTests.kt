package com.hguide

import com.hguide.ai.FakeLlmProvider
import com.hguide.ai.LlmProvider
import com.hguide.ai.PromptLibrary
import com.hguide.ai.PromptNames
import com.hguide.audio.Wav
import com.hguide.core.DrivingEventType
import com.hguide.core.Fix
import com.hguide.core.Geo
import com.hguide.core.GeoPoint
import com.hguide.core.PlaceType
import com.hguide.core.TripContext
import com.hguide.core.TripGear
import com.hguide.data.MemoryClip
import com.hguide.data.Place
import com.hguide.data.Utterance
import com.hguide.engine.ContextHours
import com.hguide.engine.DrivingEventEngine
import com.hguide.engine.MotionTracker
import com.hguide.engine.TripContextClassifier
import com.hguide.engine.TripDetector
import com.hguide.engine.TripSignal
import com.hguide.family.ConsentManager
import com.hguide.memory.HighlightExtractor
import com.hguide.memory.RelativeTime
import com.hguide.memory.ReplayDecision
import com.hguide.memory.ReplayPolicy
import com.hguide.memory.ReplayState
import com.hguide.speech.Phrases
import com.hguide.vehicle.VehicleSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class LogicTests {
    private val home = Place("HOME", PlaceType.HOME, 37.4020, 127.1086, 300)
    private val work = Place("WORK", PlaceType.WORK, 37.3948, 127.1112, 300)
    private val atHome = GeoPoint(37.4020, 127.1086)
    private val atWork = GeoPoint(37.3948, 127.1112)
    private val elsewhere = GeoPoint(37.5, 127.2)
    private fun at(dow: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 18 + dow, h, m) // 2026-10-19 is Monday

    // ---- trip context ----
    @Test fun classifier_table() {
        fun c(p: GeoPoint, t: LocalDateTime, dest: GeoPoint? = null) = TripContextClassifier.classify(p, t, home, work, dest)
        assertEquals(TripContext.MORNING_COMMUTE, c(atHome, at(1, 8, 10)))
        assertEquals(TripContext.MORNING_COMMUTE, c(elsewhere, at(2, 9, 0), dest = atWork))
        assertEquals(TripContext.EVENING_HOME, c(atWork, at(3, 18, 30)))
        assertEquals(TripContext.EVENING_HOME, c(elsewhere, at(3, 19, 0), dest = atHome))
        assertEquals(TripContext.LATE_NIGHT_HOME, c(atWork, at(3, 22, 30)))
        assertEquals(TripContext.LATE_NIGHT_HOME, c(atWork, at(4, 1, 0)))
        assertEquals(TripContext.WEEKEND, c(atHome, at(6, 8, 10)))
        assertEquals(TripContext.WEEKEND, c(atWork, at(7, 22, 30)))
        assertEquals(TripContext.OTHER, c(elsewhere, at(2, 14, 0)))
        assertEquals(TripContext.OTHER, c(atHome, at(2, 14, 0)))
    }

    @Test fun classifier_honours_custom_hours() {
        val hours = ContextHours(morningStart = 6, morningEnd = 8)
        assertEquals(TripContext.OTHER, TripContextClassifier.classify(atHome, at(1, 8, 30), home, work, null, hours))
        assertEquals(TripContext.MORNING_COMMUTE, TripContextClassifier.classify(atHome, at(1, 7, 0), home, work, null, hours))
    }

    // ---- trip detector ----
    @Test fun trip_starts_on_first_fix_after_parking_is_released_and_ends_after_five_minutes_parked() {
        val d = TripDetector()
        val fix = Fix(37.0, 127.0, 0f, 0)
        assertEquals(null, d.onVehicle(VehicleSnapshot(gear = TripGear.PARK), 0))
        assertEquals("no fix yet", null, d.onVehicle(VehicleSnapshot(gear = TripGear.DRIVE), 1))
        assertTrue(d.onFix(fix, 2) is TripSignal.Started)
        assertEquals("still the same trip", null, d.onFix(fix, 3))
        d.onVehicle(VehicleSnapshot(gear = TripGear.PARK), 10_000)
        assertEquals(null, d.onTick(10_000 + 4 * 60_000))
        assertTrue(d.onTick(10_000 + 5 * 60_000) is TripSignal.Ended)
    }

    @Test fun trip_continues_through_a_short_stop_and_ends_on_app_stop() {
        val d = TripDetector()
        d.onFix(Fix(37.0, 127.0, 0f, 0), 0)
        assertTrue(d.onVehicle(VehicleSnapshot(gear = TripGear.DRIVE), 1) is TripSignal.Started)
        d.onVehicle(VehicleSnapshot(gear = TripGear.PARK), 100)
        d.onVehicle(VehicleSnapshot(gear = TripGear.DRIVE), 100_000)
        assertEquals(null, d.onTick(400_000))
        assertTrue(d.onAppStop(500_000) is TripSignal.Ended)
    }

    @Test fun without_a_gear_signal_first_fix_starts_the_trip() {
        val d = TripDetector()
        assertEquals(null, d.onVehicle(VehicleSnapshot(gear = TripGear.UNKNOWN), 0))
        assertTrue(d.onFix(Fix(37.0, 127.0, 0f, 0), 1) is TripSignal.Started)
    }

    // ---- driving events ----
    private fun engine() = DrivingEventEngine(MotionTracker())
    private fun fixAt(t: Long, northM: Double, h: Float = 0f) = Fix(37.4 + northM / 111_320.0, 127.1, h, t)

    @Test fun harsh_deceleration_is_detected_and_gentle_slowing_is_not() {
        val e = engine()
        var raised = emptyList<com.hguide.engine.RaisedEvent>()
        var y = 0.0
        for (s in 0..3) { y += 20; raised = e.onFix(fixAt(s * 1000L, y), s * 1000L) } // 20 m/s
        y += 4; raised = e.onFix(fixAt(4000, y), 4000) // 4 m/s => -16 m/s²
        assertEquals(listOf(DrivingEventType.HARSH_DECEL), raised.map { it.type })

        val g = engine()
        y = 0.0
        for (s in 0..3) { y += 20; g.onFix(fixAt(s * 1000L, y), s * 1000L) }
        y += 18; raised = g.onFix(fixAt(4000, y), 4000) // 18 m/s => -2 m/s²
        assertTrue(raised.isEmpty())
    }

    @Test fun lane_change_needs_speed_and_no_recent_signal() {
        fun run(signal: Boolean, mps: Double): List<DrivingEventType> {
            val e = engine()
            if (signal) e.onTurnSignal(com.hguide.core.TurnSignal.LEFT, 0)
            val out = mutableListOf<DrivingEventType>()
            var y = 0.0
            val heads = listOf(0f, 0f, 0f, 6f, 12f, 18f, 24f)
            heads.forEachIndexed { i, h -> y += mps; out += e.onFix(fixAt(i * 1000L, y, h), i * 1000L).map { it.type } }
            return out
        }
        assertEquals(listOf(DrivingEventType.LANE_CHANGE_NO_SIGNAL), run(signal = false, mps = 15.0))
        assertTrue("signal on", run(signal = true, mps = 15.0).isEmpty())
        assertTrue("too slow (parking manoeuvre)", run(signal = false, mps = 3.0).isEmpty())
    }

    @Test fun entering_a_zone_fires_once() {
        val e = engine()
        e.zones = listOf(com.hguide.engine.DifficultyZone("merge", GeoPoint(37.4, 127.1), 100f))
        val a = e.onFix(Fix(37.4, 127.1, 0f, 0), 0)
        val b = e.onFix(Fix(37.4, 127.1, 0f, 1000), 1000)
        assertEquals(listOf(DrivingEventType.ZONE_ENTER), a.map { it.type })
        assertTrue(b.isEmpty())
    }

    // ---- replay rules ----
    private val clip = MemoryClip(id = 1, tripId = 1, personId = "dad", title = "t", summary = "s", emotionTag = "warm", audioPath = "x", startMs = 0, endMs = 6000, lat = 0.0, lng = 0.0, placeName = "p", createdAt = 0)
    private val ok = ReplayState(nowMs = 100L * 86_400_000L, lessonActive = false, turnSignalOn = false, lastHarshDecelMs = null, playedToday = 0, speechBusy = false)

    @Test fun replay_policy_rules() {
        assertEquals(ReplayDecision.Allow, ReplayPolicy.decide(clip, ok))
        assertTrue(ReplayPolicy.decide(clip.copy(proximityEnabled = false), ok) is ReplayDecision.Deny)
        assertTrue(ReplayPolicy.decide(clip, ok.copy(lessonActive = true)) is ReplayDecision.Deny)
        assertTrue(ReplayPolicy.decide(clip, ok.copy(turnSignalOn = true)) is ReplayDecision.Deny)
        assertTrue(ReplayPolicy.decide(clip, ok.copy(playedToday = 2)) is ReplayDecision.Deny)
        assertEquals(ReplayDecision.Allow, ReplayPolicy.decide(clip, ok.copy(playedToday = 1)))
        assertTrue(ReplayPolicy.decide(clip, ok.copy(speechBusy = true)) is ReplayDecision.Deny)
        assertTrue(ReplayPolicy.decide(clip, ok.copy(askedTodayClipIds = setOf(1))) is ReplayDecision.Deny)
        // 10 s after a hard brake
        assertTrue(ReplayPolicy.decide(clip, ok.copy(lastHarshDecelMs = ok.nowMs - 9_000)) is ReplayDecision.Deny)
        assertEquals(ReplayDecision.Allow, ReplayPolicy.decide(clip, ok.copy(lastHarshDecelMs = ok.nowMs - 11_000)))
        // 30-day cooldown
        assertTrue(ReplayPolicy.decide(clip.copy(lastPlayedAt = ok.nowMs - 29L * 86_400_000), ok) is ReplayDecision.Deny)
        assertEquals(ReplayDecision.Allow, ReplayPolicy.decide(clip.copy(lastPlayedAt = ok.nowMs - 31L * 86_400_000), ok))
    }

    @Test fun relative_time_wording() {
        val day = 86_400_000L
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val now = LocalDateTime.of(2026, 10, 21, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val lastSpring = LocalDateTime.of(2025, 4, 10, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("작년 봄", RelativeTime.describe(lastSpring, now, zone))
        assertEquals("며칠 전", RelativeTime.describe(now - 3 * day, now, zone))
        assertEquals("지난달", RelativeTime.describe(now - 20 * day, now, zone))
    }

    // ---- consent ----
    @Test fun consent_requires_everyone_and_nobody_is_not_everyone() {
        val c = ConsentManager()
        assertFalse(c.allConsented(1))
        c.begin(1, emptyList()); assertFalse("no riders", c.allConsented(1))
        c.begin(2, listOf("a", "b")); c.respond(2, "a", true)
        assertFalse(c.allConsented(2))
        c.respond(2, "b", true); assertTrue(c.allConsented(2))
        c.begin(3, listOf("a", "b")); c.respond(3, "a", true); c.respond(3, "b", false)
        assertFalse(c.allConsented(3)); assertTrue(c.anyRefused(3))
        c.respond(3, "stranger", true); assertFalse(c.allConsented(3))
        c.withdraw(2); assertFalse(c.allConsented(2))
    }

    // ---- prompts / LLM ----
    private val prompts = PromptLibrary { File("src/main/assets/$it").readText() }

    @Test fun every_prompt_loads_and_fills() {
        for (n in listOf(PromptNames.HIGHLIGHT, PromptNames.GREETING, PromptNames.COACH, PromptNames.PLACE_INTRO)) {
            val p = prompts.load(n)
            assertTrue("$n system", p.system.isNotBlank()); assertTrue("$n user", p.user.isNotBlank())
        }
        val g = prompts.fill(PromptNames.GREETING, mapOf("speaker_role" to "아빠", "style_samples" to "- 천천히 해", "context" to "MORNING_COMMUTE", "recent_greetings" to "없음", "weekday" to "수요일", "time" to "08:10", "child_name" to "나"))
        assertTrue(g.system.contains("아빠의 말투")); assertFalse(g.system.contains("{speaker_role}")); assertFalse(g.user.contains("{"))
    }

    @Test fun fake_llm_greeting_avoids_recent_lines() = runTest {
        val llm = FakeLlmProvider()
        val first = prompts.fill(PromptNames.GREETING, mapOf("speaker_role" to "아빠", "style_samples" to "-", "context" to "EVENING_HOME", "recent_greetings" to "없음", "weekday" to "월", "time" to "18:00", "child_name" to "나"))
        val t1 = org.json.JSONObject(llm.completeJson(first.system, first.user)).getString("text")
        val second = prompts.fill(PromptNames.GREETING, mapOf("speaker_role" to "아빠", "style_samples" to "-", "context" to "EVENING_HOME", "recent_greetings" to t1, "weekday" to "월", "time" to "18:00", "child_name" to "나"))
        val t2 = org.json.JSONObject(llm.completeJson(second.system, second.user)).getString("text")
        assertTrue(t1 != t2); assertTrue(t1.length <= 40 && t2.length <= 40)
    }

    // ---- highlight validation ----
    private fun utt(id: Long, start: Long, end: Long, text: String, who: String? = "dad") = Utterance(id, 1, who, start, end, text, 37.4, 127.1)
    private val names = mapOf("dad" to "아빠", "me" to "나")

    private class Canned(val json: String) : LlmProvider { override suspend fun completeJson(system: String, user: String) = json }

    @Test fun extractor_drops_sensitive_clips_even_if_the_model_picked_them() = runTest {
        val rows = listOf(utt(1, 0, 6000, "병원에서 돈 얘기 들었어"), utt(2, 20_000, 26_000, "정말 잘했어"))
        val json = """{"highlights":[{"start_ms":0,"end_ms":6000,"speaker":"아빠","title":"x","summary":"s","emotion":"warm"},{"start_ms":20000,"end_ms":26000,"speaker":"아빠","title":"칭찬","summary":"s","emotion":"proud"}]}"""
        val out = HighlightExtractor(Canned(json), prompts).extract("2026-10-21", "a", "b", rows, names)
        assertEquals(listOf("칭찬"), out.map { it.title })
    }

    @Test fun extractor_caps_at_five_clamps_length_and_snaps_to_sentences() = runTest {
        val rows = (0 until 8).map { utt(it + 1L, it * 30_000L, it * 30_000L + 3_000, "정말 잘했어 $it") } +
            utt(100, 300_000, 340_000, "아주 긴 이야기를 하고 있어요")
        val items = (0 until 8).joinToString(",") { """{"start_ms":${it * 30_000 + 500},"end_ms":${it * 30_000 + 1_000},"speaker":"아빠","title":"가나다라마바사아자차카타파하","summary":"s","emotion":"weird"}""" }
        val out = HighlightExtractor(Canned("""설명입니다 {"highlights":[$items]} 끝"""), prompts).extract("d", "a", "b", rows, names)
        assertEquals(5, out.size)
        out.forEach {
            assertTrue("${it.endMs - it.startMs}", it.endMs - it.startMs in 5_000..20_000)
            assertEquals(0L, it.startMs % 30_000) // snapped to the start of a sentence
            assertTrue(it.title.length <= 12)
            assertEquals("unknown emotion normalised", "warm", it.emotion)
        }
        val long = HighlightExtractor(Canned("""{"highlights":[{"start_ms":300000,"end_ms":340000,"speaker":"아빠","title":"긴","summary":"","emotion":"story"}]}"""), prompts).extract("d", "a", "b", rows, names)
        assertEquals(20_000, long.single().endMs - long.single().startMs)
    }

    @Test fun extractor_survives_bad_model_output() = runTest {
        val rows = listOf(utt(1, 0, 6000, "정말 잘했어"))
        for (bad in listOf("", "not json", "{}", """{"highlights":"nope"}""")) {
            assertEquals(emptyList<Any>(), HighlightExtractor(Canned(bad), prompts).extract("d", "a", "b", rows, names))
        }
        val throwing = object : LlmProvider { override suspend fun completeJson(system: String, user: String): String = error("down") }
        assertEquals(emptyList<Any>(), HighlightExtractor(throwing, prompts).extract("d", "a", "b", rows, names))
        assertEquals(emptyList<Any>(), HighlightExtractor(Canned("{}"), prompts).extract("d", "a", "b", emptyList(), names))
    }

    // ---- misc ----
    @Test fun phrases() {
        assertTrue(Phrases.isYes("응")); assertTrue(Phrases.isYes("네, 들려줘")); assertFalse(Phrases.isYes("아니")); assertFalse(Phrases.isYes(null)); assertFalse(Phrases.isYes("글쎄"))
        assertTrue(Phrases.isStopRecording("기록 그만")); assertTrue(Phrases.isStopRecording("기록그만해")); assertFalse(Phrases.isStopRecording("그만해"))
    }

    @Test fun wav_roundtrip_and_distance_helpers() {
        val f = File.createTempFile("wav", ".wav")
        val pcm = Wav.tone(500, 440.0)
        Wav.write(f, pcm)
        assertEquals(pcm.toList(), Wav.readPcm(f).toList())
        assertEquals(500, Wav.bytesToMs(pcm.size).toInt())
        assertEquals(0f, Geo.headingDelta(350f, 350f)); assertEquals(20f, Geo.headingDelta(350f, 10f))
        assertTrue(Geo.distanceM(37.4, 127.1, 37.401, 127.1) in 105.0..115.0)
    }
}

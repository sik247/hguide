package com.hguide

import com.hguide.ai.FakeLlmProvider
import com.hguide.ai.PromptLibrary
import com.hguide.audio.FakeAudioPlayer
import com.hguide.audio.FakeAudioSource
import com.hguide.core.MutableClock
import com.hguide.core.EventLog
import com.hguide.core.PersonRole
import com.hguide.core.PlaceType
import com.hguide.data.InMemorySettings
import com.hguide.data.Person
import com.hguide.data.Place
import com.hguide.engine.DrivingEventEngine
import com.hguide.engine.DrivingGuards
import com.hguide.engine.LessonState
import com.hguide.engine.MotionTracker
import com.hguide.engine.TripController
import com.hguide.engine.TripDetector
import com.hguide.family.ChunkProcessor
import com.hguide.family.ConsentManager
import com.hguide.family.FamilyModeController
import com.hguide.family.Recorder
import com.hguide.location.FakeLocationGateway
import com.hguide.memory.ClipCutter
import com.hguide.memory.HighlightExtractor
import com.hguide.memory.PlaceNamer
import com.hguide.memory.PlaceReplayController
import com.hguide.memory.RecapService
import com.hguide.navi.FakeNaviGateway
import com.hguide.speech.FakeSttGateway
import com.hguide.speech.FakeTtsGateway
import com.hguide.vehicle.FakeVehicleGateway
import com.hguide.voice.*
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime

/** Everything wired with fakes and in-memory storage, for scenario tests. */
class World(val scope: CoroutineScope, pcm: ByteArray = ByteArray(0), samples: List<SampleConversation> = emptyList(), chunkMs: Long = 30_000) {
    val dir: File = Files.createTempDirectory("hguide-test").toFile()
    val clock = MutableClock().also { it.setDateTime(LocalDateTime.of(2026, 10, 21, 8, 10)) } // Wednesday
    val events = EventLog()
    val settings = InMemorySettings()
    val prompts = PromptLibrary { File("src/main/assets/$it").readText() }
    val llm = FakeLlmProvider()

    val people = MemPersonDao(); val profiles = MemVoiceProfileDao(); val trips = MemTripDao()
    val chunks = MemAudioChunkDao(); val utterances = MemUtteranceDao(); val clips = MemMemoryClipDao()
    val places = MemPlaceDao(); val greetingLogs = MemGreetingLogDao(); val drivingEvents = MemDrivingEventDao()

    val vehicle = FakeVehicleGateway(); val location = FakeLocationGateway(); val navi = FakeNaviGateway()
    val tts = FakeTtsGateway(); val stt = FakeSttGateway().also { it.autoSilence = true }
    val player = FakeAudioPlayer()
    val arbiter = SpeechArbiter(tts, player)

    val embedder = FakeVoiceEmbedder()
    val clone = FakeVoiceCloneProvider(File(dir, "voice"))
    val enrollment = VoiceEnrollment(people, profiles, embedder, clone, clock, dir)
    val voiceSpeaker = VoiceSpeaker(enrollment, clone, arbiter)
    val styles = StyleSamples(utterances)
    val matcher = SpeakerMatcher(profiles, embedder)
    val processor = ChunkProcessor(stt, FakeDiarizationProvider(samples), matcher, utterances)

    val consent = ConsentManager()
    val lesson = LessonState(); val guards = DrivingGuards(); val motion = MotionTracker()
    val engine = DrivingEventEngine(motion); val detector = TripDetector()

    lateinit var controller: TripController
    val recorder = Recorder(consent, { FakeAudioSource(pcm) }, dir, chunks, processor, clock, scope, lastFix = { controller.lastFix }, drivingState = { "MOVING" }, chunkMs = chunkMs)
    val family = FamilyModeController(consent, recorder, arbiter, stt, trips, scope, events)
    val greeting = GreetingSpeaker(llm, prompts, voiceSpeaker, styles, people, greetingLogs, settings, clock, events)
    val coach = CoachSpeaker(llm, prompts, voiceSpeaker, styles, people, drivingEvents, settings, lesson, events)
    val extractor = HighlightExtractor(llm, prompts)
    val cutter = ClipCutter(chunks)
    val placeNamer = PlaceNamer(places)
    val recap = RecapService(trips, utterances, people, extractor, cutter, clips, placeNamer, clock, dir)
    val replay = PlaceReplayController(location, clips, people, settings, llm, prompts, arbiter, stt, guards, lesson, clock, scope, events, answerTimeoutMs = 1_000)

    init {
        controller = TripController(scope, clock, vehicle, location, navi, trips, places, drivingEvents, people, settings, detector, motion, engine, guards, greeting, coach, family, recap, events)
    }

    companion object {
        val HOME = com.hguide.core.GeoPoint(37.4020, 127.1086)
        val WORK = com.hguide.core.GeoPoint(37.3948, 127.1112)
    }

    /** 아빠 / 엄마 / 나, enrolled; 아빠 approved cloning; greetings in 아빠's voice; home + work set. */
    suspend fun seedFamily(approveDad: Boolean = true) {
        val now = clock.nowMs()
        listOf(Person("dad", "아빠", PersonRole.PARENT, false, now), Person("mom", "엄마", PersonRole.PARENT, false, now + 1), Person("me", "나", PersonRole.CHILD, false, now + 2)).forEach { people.upsert(it) }
        places.upsert(Place("HOME", PlaceType.HOME, HOME.lat, HOME.lng, 300))
        places.upsert(Place("WORK", PlaceType.WORK, WORK.lat, WORK.lng, 300))
        for (id in listOf("dad", "mom", "me")) {
            val files = (0..2).map { i -> File(dir, "enroll_${id}_$i.wav").also { f -> com.hguide.audio.Wav.write(f, com.hguide.audio.Wav.silence(500)) } }
            enrollment.enroll(id, files)
        }
        if (approveDad) enrollment.approveClone("dad")
        settings.greetingVoicePersonId = "dad"
    }
}

package com.hguide

import android.app.Application
import com.hguide.ai.FakeLlmProvider
import com.hguide.ai.LlmProvider
import com.hguide.ai.PromptLibrary
import com.hguide.audio.AndroidAudioPlayer
import com.hguide.audio.AudioPlayer
import com.hguide.audio.AudioSource
import com.hguide.audio.FakeAudioSource
import com.hguide.audio.MicAudioSource
import com.hguide.audio.Wav
import com.hguide.core.Clock
import com.hguide.core.EventLog
import com.hguide.core.OffsetClock
import com.hguide.core.SystemClockImpl
import com.hguide.data.HguideDatabase
import com.hguide.data.PrefsSettings
import com.hguide.data.SettingsStore
import com.hguide.demo.DemoActions
import com.hguide.demo.DemoSeeder
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
import com.hguide.location.LocationGateway
import com.hguide.location.PleosLocationGateway
import com.hguide.memory.ClipCutter
import com.hguide.memory.HighlightExtractor
import com.hguide.memory.PlaceNamer
import com.hguide.memory.PlaceReplayController
import com.hguide.memory.PrivacyManager
import com.hguide.memory.RecapService
import com.hguide.navi.FakeNaviGateway
import com.hguide.navi.NaviGateway
import com.hguide.navi.PleosNaviGateway
import com.hguide.speech.FakeSttGateway
import com.hguide.speech.DemoTtsGateway
import com.hguide.speech.DemoVoices
import com.hguide.speech.PlatformTts
import com.hguide.speech.TypecastTts
import com.hguide.speech.PleosSttGateway
import com.hguide.speech.PleosTtsGateway
import com.hguide.speech.SttGateway
import com.hguide.speech.TtsGateway
import com.hguide.vehicle.FakeVehicleGateway
import com.hguide.vehicle.PleosVehicleGateway
import com.hguide.vehicle.VehicleGateway
import com.hguide.voice.CoachSpeaker
import com.hguide.voice.DiarizationProvider
import com.hguide.voice.FakeDiarizationProvider
import com.hguide.voice.DemoVoiceCloneProvider
import com.hguide.voice.FakeVoiceCloneProvider
import com.hguide.voice.FakeVoiceEmbedder
import com.hguide.voice.GreetingSpeaker
import com.hguide.voice.SampleConversation
import com.hguide.voice.SpeakerMatcher
import com.hguide.voice.SpeechArbiter
import com.hguide.voice.StyleSamples
import com.hguide.voice.VoiceCloneProvider
import com.hguide.voice.VoiceEmbedder
import com.hguide.voice.VoiceEnrollment
import com.hguide.voice.VoiceSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual DI: one place that decides Fake vs Pleos / vendor and wires every component. */
class AppContainer(private val app: Application) {
    val appContext get() = app.applicationContext
    private val fakeGateways = BuildConfig.USE_FAKE_GATEWAYS
    private val fakeVendors = BuildConfig.USE_FAKES

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val baseDir = app.filesDir
    val db = HguideDatabase.build(app)
    val settings: SettingsStore = PrefsSettings(app)
    val events = EventLog()
    val clock: Clock = if (fakeGateways) OffsetClock() else SystemClockImpl
    val prompts = PromptLibrary { app.assets.open(it).use { s -> s.readBytes().decodeToString() } }

    private fun asset(name: String) = app.assets.open("sample/$name").use { it.readBytes() }
    val samples: List<SampleConversation> by lazy {
        listOf("conversation1", "conversation2").map { n ->
            SampleConversation(n, Wav.readPcmBytes(asset("$n.wav")), SampleConversation.parseLabels(asset("$n.labels.json").decodeToString()))
        }
    }

    // ---- gateways ----
    val fakeVehicle = if (fakeGateways) FakeVehicleGateway() else null
    val fakeLocation = if (fakeGateways) FakeLocationGateway() else null
    val fakeNavi = if (fakeGateways) FakeNaviGateway() else null
    val fakeStt = if (fakeGateways) FakeSttGateway() else null
    /** Emulator demos speak through Android's TTS so a recording has sound; tests keep the silent fakes. */
    private val platformTts by lazy { PlatformTts(app) }
    private val typecast = TypecastTts(BuildConfig.TYPECAST_API_KEY, java.io.File(app.cacheDir, "typecast"))
    private val demoVoices = DemoVoices(BuildConfig.TYPECAST_VOICE_DAD, BuildConfig.TYPECAST_VOICE_MOM, BuildConfig.TYPECAST_VOICE_GUIDE)
    val fakeTts: TtsGateway? = if (fakeGateways) DemoTtsGateway(platformTts, events, typecast, demoVoices.guide, AndroidAudioPlayer()) else null

    val vehicle: VehicleGateway = fakeVehicle ?: PleosVehicleGateway(app)
    val location: LocationGateway = fakeLocation ?: PleosLocationGateway(app)
    val navi: NaviGateway = fakeNavi ?: PleosNaviGateway(app)
    val tts: TtsGateway = fakeTts ?: PleosTtsGateway(app)
    val stt: SttGateway = fakeStt ?: PleosSttGateway(app)
    val player: AudioPlayer = AndroidAudioPlayer()

    // ---- vendors (interface + Fake; real vendors plug in here) ----
    val llm: LlmProvider = if (fakeVendors) FakeLlmProvider() else NotConfiguredLlm()
    val diarizer: DiarizationProvider = if (fakeVendors) FakeDiarizationProvider(samples) else NotConfiguredDiarization()
    val embedder: VoiceEmbedder = if (fakeVendors) FakeVoiceEmbedder() else NotConfiguredEmbedder()
    val clone: VoiceCloneProvider = when {
        fakeVendors && fakeGateways -> DemoVoiceCloneProvider(FakeVoiceCloneProvider(java.io.File(baseDir, "voice")), platformTts, typecast, demoVoices)
        fakeVendors -> FakeVoiceCloneProvider(java.io.File(baseDir, "voice"))
        else -> NotConfiguredVoiceClone()
    }

    // ---- core services ----
    private val chunkMs = 30_000L // spec F2: 30-second chunks
    val arbiter = SpeechArbiter(tts, player)
    val lesson = LessonState()
    val guards = DrivingGuards()
    val motion = MotionTracker()
    val engine = DrivingEventEngine(motion)
    val detector = TripDetector()
    val consent = ConsentManager()
    val enrollment = VoiceEnrollment(db.personDao(), db.voiceProfileDao(), embedder, clone, clock, baseDir)
    val voiceSpeaker = VoiceSpeaker(enrollment, clone, arbiter)
    val styles = StyleSamples(db.utteranceDao())
    val matcher = SpeakerMatcher(db.voiceProfileDao(), embedder)
    val chunkProcessor = ChunkProcessor(stt, diarizer, matcher, db.utteranceDao())

    val audioSourceFactory: () -> AudioSource = {
        if (fakeGateways && fakeVendors) FakeAudioSource(Wav.concat(samples.map { it.pcm }), paceFactor = 10.0) else MicAudioSource(app)
    }
    fun newAudioSource(): AudioSource = audioSourceFactory()

    lateinit var tripController: TripController
        private set
    val recorder = Recorder(
        consent, audioSourceFactory, baseDir, db.audioChunkDao(), chunkProcessor, clock, scope,
        lastFix = { tripController.lastFix },
        drivingState = { if (tripController.parked.value) "PARKED" else if (tripController.stationary.value) "STATIONARY" else "MOVING" },
        chunkMs = chunkMs,
    )
    val family = FamilyModeController(consent, recorder, arbiter, stt, db.tripDao(), scope, events)
    val greeting = GreetingSpeaker(llm, prompts, voiceSpeaker, styles, db.personDao(), db.greetingLogDao(), settings, clock, events)
    val coach = CoachSpeaker(llm, prompts, voiceSpeaker, styles, db.personDao(), db.drivingEventDao(), settings, lesson, events)
    val extractor = HighlightExtractor(llm, prompts)
    val cutter = ClipCutter(db.audioChunkDao())
    val placeNamer = PlaceNamer(db.placeDao())
    val recap = RecapService(db.tripDao(), db.utteranceDao(), db.personDao(), extractor, cutter, db.memoryClipDao(), placeNamer, clock, baseDir)
    val replay = PlaceReplayController(location, db.memoryClipDao(), db.personDao(), settings, llm, prompts, arbiter, stt, guards, lesson, clock, scope, events)
    val privacy = PrivacyManager(db.audioChunkDao(), db.utteranceDao(), db.memoryClipDao(), db.greetingLogDao(), settings, clock, baseDir)

    val demo: DemoActions? = if (fakeGateways) DemoActions(this) else null

    fun start() {
        tripController = TripController(
            scope, clock, vehicle, location, navi, db.tripDao(), db.placeDao(), db.drivingEventDao(), db.personDao(), settings,
            detector, motion, engine, guards, greeting, coach, family, recap, events,
        )
        scope.launch {
            if (fakeVendors) DemoSeeder(this@AppContainer).seedIfEmpty()
            if (fakeVendors && fakeGateways) DemoSeeder(this@AppContainer).seedMemories()
            privacy.purgeExpiredRawAudio()
            tripController.start()
            replay.start()
            replay.sync(tripController.lastFix?.point ?: com.hguide.core.GeoPoint(0.0, 0.0))
        }
    }

    /** Called when the app is closing: ends the trip (recap runs) and releases the SDKs. */
    fun onAppStop() {
        if (::tripController.isInitialized) tripController.onAppStop()
        vehicle.stop(); location.stop(); navi.stop()
    }
}

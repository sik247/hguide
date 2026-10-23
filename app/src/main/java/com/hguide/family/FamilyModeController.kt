package com.hguide.family

import com.hguide.core.EventLog
import com.hguide.core.FamilyState
import com.hguide.core.SpeechPriority
import com.hguide.data.Person
import com.hguide.data.Trip
import com.hguide.data.TripDao
import com.hguide.speech.Phrases
import com.hguide.speech.SttGateway
import com.hguide.voice.SpeechArbiter
import com.hguide.voice.SpeechRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConsentPrompt(val tripId: Long, val riders: List<Person>, val answers: Map<String, Boolean>)

/**
 * OFF → ASKING → RECORDING → PAUSED → OFF.
 * Riders consent by voice (one at a time, by name) or on screen while parked; any refusal ends the flow with nothing recorded.
 */
class FamilyModeController(
    private val consent: ConsentManager,
    private val recorder: Recorder,
    private val arbiter: SpeechArbiter,
    private val stt: SttGateway,
    private val trips: TripDao,
    private val scope: CoroutineScope,
    private val events: EventLog? = null,
    private val answerTimeoutMs: Long = 8_000,
) {
    private val _state = MutableStateFlow(FamilyState.OFF)
    val state: StateFlow<FamilyState> = _state.asStateFlow()
    private val _prompt = MutableStateFlow<ConsentPrompt?>(null)
    val prompt: StateFlow<ConsentPrompt?> = _prompt.asStateFlow()

    private var tripId = -1L
    private var riders: List<Person> = emptyList()
    private var voiceJob: Job? = null
    private var commandJob: Job? = null

    fun beginAsking(tripId: Long, riders: List<Person>) {
        if (_state.value != FamilyState.OFF || riders.isEmpty()) return
        this.tripId = tripId
        this.riders = riders
        consent.begin(tripId, riders.map { it.id })
        _state.value = FamilyState.ASKING
        publish()
        voiceJob = scope.launch { askByVoice() }
    }

    /** Screen path (UI only enables this while parked) and the voice path both land here. */
    fun respond(personId: String, yes: Boolean) {
        if (_state.value != FamilyState.ASKING) return
        consent.respond(tripId, personId, yes)
        publish()
        evaluate()
    }

    private suspend fun askByVoice() {
        arbiter.play(SpeechRequest(SpeechPriority.GREETING, text = "오늘 가족이 함께 타고 있나요? 대화를 기록할까요?"))
        for (p in riders) {
            if (_state.value != FamilyState.ASKING) return
            if (consent.answered(tripId, p.id) != null) continue
            arbiter.play(SpeechRequest(SpeechPriority.GREETING, text = "${p.name}, 기록에 동의하시나요?"))
            val answer = stt.listenForPhrase(answerTimeoutMs)
            if (consent.answered(tripId, p.id) != null) continue // answered on screen meanwhile
            // Silence or anything unclear counts as "no": consent must be explicit.
            respond(p.id, Phrases.isYes(answer))
        }
    }

    private fun evaluate() {
        if (_state.value != FamilyState.ASKING) return
        when {
            consent.anyRefused(tripId) -> {
                voiceJob?.cancel()
                consent.withdraw(tripId)
                _state.value = FamilyState.OFF
                _prompt.value = null
                events?.add("🚫 동의 거부 — 기록하지 않음")
            }
            consent.allConsented(tripId) -> {
                voiceJob?.cancel()
                startRecording()
            }
        }
    }

    private fun startRecording() {
        if (!recorder.start(tripId)) { _state.value = FamilyState.OFF; return }
        _state.value = FamilyState.RECORDING
        scope.launch {
            trips.get(tripId)?.let { trips.update(it.copy(familyMode = true, consentPersonIds = consent.consentedIds(tripId))) }
        }
        events?.add("● 기록 시작")
        listenForStopCommand()
    }

    private fun listenForStopCommand() {
        commandJob?.cancel()
        commandJob = scope.launch {
            // TODO(verify-api): on the real STT the mic may be busy with the recorder; sendAudio() from the chunk stream is the alternative.
            while (_state.value == FamilyState.RECORDING) {
                val heard = stt.listenForPhrase(10_000)
                if (Phrases.isStopRecording(heard)) { stop(); return@launch }
                if (heard == null) delay(500)
            }
        }
    }

    fun pause() {
        if (_state.value != FamilyState.RECORDING) return
        recorder.stop()
        _state.value = FamilyState.PAUSED
        events?.add("⏸ 기록 일시정지")
    }

    fun resume() {
        if (_state.value != FamilyState.PAUSED) return
        if (!recorder.start(tripId)) { _state.value = FamilyState.OFF; return }
        _state.value = FamilyState.RECORDING
        events?.add("● 기록 재개")
        listenForStopCommand()
    }

    /** "기록 그만" / trip end: stops immediately and withdraws consent for the rest of the trip. */
    fun stop() {
        if (_state.value == FamilyState.OFF) return
        voiceJob?.cancel(); commandJob?.cancel()
        recorder.stop()
        consent.withdraw(tripId)
        _state.value = FamilyState.OFF
        _prompt.value = null
        events?.add("■ 기록 종료")
    }

    private fun publish() {
        _prompt.value = ConsentPrompt(tripId, riders, riders.mapNotNull { r -> consent.answered(tripId, r.id)?.let { r.id to it } }.toMap())
    }
}

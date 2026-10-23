package com.hguide.speech

import com.hguide.core.EventLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

interface TtsGateway {
    val available: Boolean
    /** Speaks with the platform default voice. Returns false if interrupted or failed. */
    suspend fun speak(text: String): Boolean
    fun stop()
}

interface SttGateway {
    val available: Boolean
    /** Transcribes 16 kHz mono PCM16 audio. Returns null if the engine failed. */
    suspend fun transcribe(pcm: ByteArray): String?
    /** Listens on the mic for one utterance (voice commands, yes/no). Null on timeout. */
    suspend fun listenForPhrase(timeoutMs: Long): String?
}

class FakeTtsGateway(private val log: EventLog? = null) : TtsGateway {
    override var available = true
    val spoken = mutableListOf<String>()
    @Volatile private var stopped = false
    override suspend fun speak(text: String): Boolean {
        stopped = false
        spoken += text
        log?.add("🔊 ${text}")
        return !stopped
    }
    override fun stop() { stopped = true }
}

/**
 * Scripted answers first (tests); otherwise waits for the demo UI to call [answer] until the timeout.
 */
class FakeSttGateway : SttGateway {
    override var available = true
    private val script = ArrayDeque<String?>()
    private var waiting: CompletableDeferred<String?>? = null
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()
    /** When true, listenForPhrase returns immediately with null if nothing is scripted (headless tests). */
    var autoSilence = false
    var transcript: (ByteArray) -> String? = { "" }

    fun enqueue(vararg answers: String?) { answers.forEach { script.addLast(it) } }
    fun answer(text: String?) { waiting?.complete(text) }

    override suspend fun transcribe(pcm: ByteArray): String? = transcript(pcm)

    override suspend fun listenForPhrase(timeoutMs: Long): String? {
        if (script.isNotEmpty()) return script.removeFirst()
        if (autoSilence) return null
        val d = CompletableDeferred<String?>()
        waiting = d
        _listening.value = true
        return try { withTimeoutOrNull(timeoutMs) { d.await() } } finally { waiting = null; _listening.value = false }
    }
}

object Phrases {
    private val yes = listOf("응", "네", "예", "좋아", "그래", "들려", "들을래", "동의", "맞아", "yes")
    private val no = listOf("아니", "싫", "거부", "안 ", "괜찮아요", "no")
    private val stopRecording = listOf("기록 그만", "기록그만", "녹음 그만", "그만 기록")

    fun isYes(s: String?): Boolean {
        val t = s?.trim()?.lowercase() ?: return false
        if (no.any { t.startsWith(it) || t.contains("아니") }) return false
        return yes.any { t.contains(it) }
    }
    fun isNo(s: String?): Boolean = s != null && !isYes(s) && no.any { s.contains(it) }
    fun isStopRecording(s: String?): Boolean = s != null && stopRecording.any { s.replace(" ", "").contains(it.replace(" ", "")) }
}

package com.hguide.voice

import com.hguide.audio.AudioPlayer
import com.hguide.core.SpeechPriority
import com.hguide.speech.TtsGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class SpeechRequest(
    val priority: SpeechPriority,
    val text: String? = null,
    val file: File? = null,
    /** Shown as the "AI 목소리" badge while playing. */
    val aiVoice: Boolean = false,
)

/**
 * One voice at a time. Priority: coach cue > greeting > memory. A request that cannot outrank what is currently
 * playing is dropped (returns false); a higher one pre-empts it.
 */
class SpeechArbiter(private val tts: TtsGateway, private val player: AudioPlayer) {
    private val lock = Any()
    private var current: Any? = null
    private var currentReq: SpeechRequest? = null
    private val _aiVoice = MutableStateFlow(false)
    /** UI badge: true while an AI-cloned voice is playing. */
    val aiVoice: StateFlow<Boolean> = _aiVoice.asStateFlow()
    val busy get() = synchronized(lock) { current != null }

    suspend fun play(req: SpeechRequest): Boolean {
        val token = Any()
        synchronized(lock) {
            val cur = currentReq
            if (cur != null) {
                if (cur.priority.rank >= req.priority.rank) return false
                tts.stop(); player.stop()
            }
            current = token; currentReq = req; _aiVoice.value = req.aiVoice
        }
        try {
            return when {
                req.file != null -> player.play(req.file)
                req.text != null -> tts.speak(req.text)
                else -> false
            }
        } finally {
            synchronized(lock) {
                if (current === token) { current = null; currentReq = null; _aiVoice.value = false }
            }
        }
    }

    fun stopAll() { tts.stop(); player.stop() }
}

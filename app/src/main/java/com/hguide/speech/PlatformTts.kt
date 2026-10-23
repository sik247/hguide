package com.hguide.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.hguide.core.EventLog
import com.hguide.core.log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Android's own TextToSpeech, used only in fake-gateway (emulator demo) builds so the demo is audible.
 * Real vehicles use Pleos TTS ([PleosTtsGateway]). Everything degrades to "silent but succeeded" when no Korean voice exists.
 */
class PlatformTts(context: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, (Boolean) -> Unit>()
    private val tts = TextToSpeech(context.applicationContext) { ready.complete(it == TextToSpeech.SUCCESS) }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onDone(id: String) { pending.remove(id)?.invoke(true) }
            @Deprecated("Deprecated in Java") override fun onError(id: String) { pending.remove(id)?.invoke(false) }
            override fun onStop(id: String, interrupted: Boolean) { pending.remove(id)?.invoke(false) }
        })
    }
    private var korean: Boolean? = null

    private suspend fun usable(): Boolean {
        korean?.let { return it }
        val ok = withTimeoutOrNull(5_000) { ready.await() } == true && tts.setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE
        if (!ok) log("tts", "platform TTS without Korean; demo speech stays silent")
        return ok.also { korean = it }
    }

    private suspend fun run(start: (String) -> Int): Boolean = suspendCancellableCoroutine { cont ->
        val id = "u" + System.nanoTime()
        pending[id] = { ok -> if (cont.isActive) cont.resume(ok) }
        if (start(id) != TextToSpeech.SUCCESS) pending.remove(id)?.invoke(false)
        cont.invokeOnCancellation { pending.remove(id); tts.stop() }
    }

    suspend fun speak(text: String, pitch: Float = 1f): Boolean {
        if (!usable()) return false
        tts.setPitch(pitch)
        return run { id -> tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) }
    }

    suspend fun toFile(text: String, pitch: Float, out: File): Boolean {
        if (!usable()) return false
        out.parentFile?.mkdirs()
        tts.setPitch(pitch)
        return run { id -> tts.synthesizeToFile(text, null, out, id) } && out.length() > 0
    }

    fun stop() { tts.stop() }
}

/**
 * [TtsGateway] for emulator demos: logs like the fake, and speaks with the Typecast guide voice when a key is set,
 * else through [PlatformTts].
 */
class DemoTtsGateway(
    private val tts: PlatformTts,
    private val log: EventLog?,
    private val voice: TypecastTts? = null,
    private val guideVoice: String = "",
    private val player: com.hguide.audio.AudioPlayer? = null,
) : TtsGateway {
    override val available = true
    @Volatile private var stopped = false

    override suspend fun speak(text: String): Boolean {
        stopped = false
        log?.add("🔊 $text")
        val mp3 = voice?.toFile(text, guideVoice)
        val spoke = if (mp3 != null && player != null) player.play(mp3) else tts.speak(text)
        if (!spoke && !stopped) delay(1_500) // no voice at all: keep the on-screen caption up for a moment
        return !stopped
    }

    override fun stop() { stopped = true; tts.stop(); player?.stop() }
}

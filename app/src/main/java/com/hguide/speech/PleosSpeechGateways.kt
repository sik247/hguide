package com.hguide.speech

import android.content.Context
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import com.hguide.core.log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** TextToSpeech SDK 3.3.1, HYBRID mode (server first, on-device fallback). */
class PleosTtsGateway(context: Context) : TtsGateway {
    private val tts = TextToSpeech(context, TtsMode.HYBRID)
    @Volatile override var available = false
        private set
    private val lock = Mutex()

    init {
        tts.initialize(object : TtsInit {
            override fun onSuccess() { available = true }
            override fun onFailure(error: ai.pleos.playground.tts.constant.ErrorType) { log("tts", "init failed: $error") }
        })
        // TODO(verify-api): server-mode synthesis needs registerApp(...) with My Project credentials; HYBRID falls back on-device without it.
    }

    override suspend fun speak(text: String): Boolean = lock.withLock {
        if (!available) return false
        suspendCancellableCoroutine { cont ->
            val l = object : EventListener {
                private fun done(ok: Boolean) { tts.removeEventListener(this); if (cont.isActive) cont.resume(ok) }
                override fun onStart() {}
                override fun onReady() {}
                override fun onDone() = done(true)
                override fun onStop() = done(false)
                override fun onError(error: ai.pleos.playground.tts.constant.ErrorType) { log("tts", "error: $error"); done(false) }
                override fun onUpdatedRms(rms: Double) {}
            }
            tts.addEventListener(l)
            // TODO(verify-api): meaning of the Boolean in speak(text, Boolean) is undocumented; false assumed = do not interrupt.
            tts.speak(text, false)
            cont.invokeOnCancellation { tts.stop(); tts.removeEventListener(l) }
        }
    }

    override fun stop() { runCatching { tts.stop() } }
}

/** SpeechToText SDK 3.3.1, HYBRID mode. */
class PleosSttGateway(context: Context) : SttGateway {
    private val stt = SpeechToText(context, SttMode.HYBRID)
    @Volatile override var available = false
        private set
    private val lock = Mutex()

    init {
        stt.initialize(object : SttInit {
            override fun onSuccess() { available = true }
            override fun onFailure(error: ai.pleos.playground.stt.constant.ErrorType) { log("stt", "init failed: $error") }
        })
    }

    /** Runs one recognition session; [feed] pushes audio (null = use the mic). */
    private suspend fun session(timeoutMs: Long, firstFinalOnly: Boolean, feed: (() -> Unit)?): String? = lock.withLock {
        if (!available) return null
        val finals = StringBuilder()
        val done = CompletableDeferred<Unit>()
        val l = object : ResultListener {
            override fun onUpdated(text: String, isFinal: Boolean) {
                if (isFinal) { if (finals.isNotEmpty()) finals.append(' '); finals.append(text); if (firstFinalOnly) done.complete(Unit) }
            }
            override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) {}
            override fun onStartedRecognition() {}
            override fun onEndedRecognition() { done.complete(Unit) }
            override fun onError(error: ai.pleos.playground.stt.constant.ErrorType) { log("stt", "error: $error"); done.complete(Unit) }
            override fun onReady() {}
            override fun onUpdatedRms(rms: Double) {}
        }
        stt.addListener(l)
        try {
            stt.request()
            feed?.invoke()
            withTimeoutOrNull(timeoutMs) { done.await() }
            stt.stop()
            finals.toString().ifBlank { null }
        } finally { stt.removeListener(l) }
    }

    override suspend fun transcribe(pcm: ByteArray): String? = session(timeoutMs = 60_000, firstFinalOnly = false) {
        // TODO(verify-api): request() + sendAudio() semantics (mic vs injected audio, max buffer size) are not documented.
        var off = 0
        while (off < pcm.size) {
            val end = minOf(off + 3200, pcm.size)
            stt.sendAudio(pcm.copyOfRange(off, end))
            off = end
        }
        stt.completeAudioSend()
    }

    override suspend fun listenForPhrase(timeoutMs: Long): String? = session(timeoutMs, firstFinalOnly = true, feed = null)
}

package com.hguide.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import com.hguide.core.EventLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

interface AudioPlayer {
    /** Plays to completion. Returns false if stopped/failed. */
    suspend fun play(file: File): Boolean
    fun stop()
}

class AndroidAudioPlayer : AudioPlayer {
    @Volatile private var mp: MediaPlayer? = null

    override suspend fun play(file: File): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val p = MediaPlayer()
            mp = p
            fun finish(ok: Boolean) {
                runCatching { p.release() }
                if (mp === p) mp = null
                if (cont.isActive) cont.resume(ok)
            }
            try {
                p.setDataSource(file.absolutePath)
                p.setOnCompletionListener { finish(true) }
                p.setOnErrorListener { _, _, _ -> finish(false); true }
                p.prepare()
                p.start()
            } catch (e: Exception) { finish(false) }
            cont.invokeOnCancellation { runCatching { p.stop() }; runCatching { p.release() } }
        }
    }

    override fun stop() { runCatching { mp?.stop() }; runCatching { mp?.release() }; mp = null }
}

/** Logs instead of playing; keeps the demo running on an emulator without audio output. */
class FakeAudioPlayer(private val log: EventLog? = null, var playMs: Long = 0) : AudioPlayer {
    val played = mutableListOf<File>()
    @Volatile private var stopped = false
    override suspend fun play(file: File): Boolean {
        stopped = false
        played += file
        log?.add("▶ 재생: ${file.name}")
        if (playMs > 0) delay(playMs)
        return !stopped
    }
    override fun stop() { stopped = true }
}

/** Source of microphone PCM (16 kHz mono PCM16). */
interface AudioSource {
    /** Blocks (suspends) until [ms] of audio has been captured. */
    suspend fun read(ms: Long): ByteArray
    fun close() {}
}

class MicAudioSource(@Suppress("unused") private val context: Context) : AudioSource {
    private var rec: AudioRecord? = null

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested by MainActivity before family mode starts
    private fun ensure(): AudioRecord = rec ?: run {
        val min = AudioRecord.getMinBufferSize(Wav.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        AudioRecord(MediaRecorder.AudioSource.MIC, Wav.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 16_384))
            .also { it.startRecording(); rec = it }
    }

    override suspend fun read(ms: Long): ByteArray = withContext(Dispatchers.IO) {
        val r = ensure()
        val out = ByteArray(Wav.msToBytes(ms))
        var off = 0
        while (off < out.size) {
            val n = r.read(out, off, out.size - off)
            if (n <= 0) break
            off += n
        }
        out
    }

    override fun close() { runCatching { rec?.stop() }; runCatching { rec?.release() }; rec = null }
}

/** Demo/test source: plays back prepared PCM (looping), or silence, paced at [paceFactor]x real time. */
class FakeAudioSource(private val pcm: ByteArray = ByteArray(0), private val paceFactor: Double = 1.0) : AudioSource {
    private var pos = 0
    var closed = false
        private set
    override suspend fun read(ms: Long): ByteArray {
        delay((ms / paceFactor).toLong().coerceAtLeast(1))
        val n = Wav.msToBytes(ms)
        if (pcm.isEmpty()) return ByteArray(n)
        val out = ByteArray(n)
        for (i in 0 until n) { out[i] = pcm[pos]; pos = (pos + 1) % pcm.size }
        return out
    }
    override fun close() { closed = true }
}

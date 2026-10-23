package com.hguide.family

import com.hguide.audio.AudioSource
import com.hguide.audio.Wav
import com.hguide.core.Clock
import com.hguide.core.Fix
import com.hguide.core.log
import com.hguide.data.AudioChunk
import com.hguide.data.AudioChunkDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Writes the microphone to [chunkMs]-long WAV chunks. There is exactly one entry point, [start], and it refuses to run
 * unless [ConsentManager.allConsented] is true; consent is re-checked before every chunk is written.
 */
class Recorder(
    private val consent: ConsentManager,
    private val sourceFactory: () -> AudioSource,
    private val baseDir: File,
    private val chunks: AudioChunkDao,
    private val processor: ChunkProcessor?,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val lastFix: () -> Fix?,
    private val drivingState: () -> String,
    val chunkMs: Long = 30_000,
) {
    private var job: Job? = null
    private var source: AudioSource? = null
    private var recordedMs = 0L // trip-relative time of recorded audio (pauses excluded)
    private var tripId = -1L
    val recording get() = job?.isActive == true

    /** Returns false (and records nothing) if consent is not complete. */
    fun start(tripId: Long): Boolean {
        if (!consent.allConsented(tripId)) return false
        if (recording) return true
        if (this.tripId != tripId) { this.tripId = tripId; recordedMs = 0 }
        val src = sourceFactory().also { source = it }
        job = scope.launch {
            try {
                while (true) {
                    val pcm = src.read(chunkMs)
                    if (!consent.allConsented(tripId)) break // consent withdrawn while the chunk was being captured: discard it
                    writeChunk(tripId, pcm)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("recorder", "stopped: ${e.javaClass.simpleName}")
            }
        }
        return true
    }

    fun stop() {
        job?.cancel(); job = null
        source?.close(); source = null
    }

    private suspend fun writeChunk(tripId: Long, pcm: ByteArray) {
        val startMs = recordedMs
        val endMs = startMs + Wav.bytesToMs(pcm.size)
        recordedMs = endMs
        val file = File(baseDir, "audio/trip_$tripId/chunk_${startMs}.wav")
        Wav.write(file, pcm)
        val fix = lastFix()
        val chunk = AudioChunk(
            tripId = tripId, path = file.path, startMs = startMs, endMs = endMs,
            lat = fix?.lat ?: 0.0, lng = fix?.lng ?: 0.0, drivingState = drivingState(), createdAt = clock.nowMs(),
        )
        val id = chunks.insert(chunk)
        // TODO(security): encrypt chunk files at rest (Phase 1; MVP stores them in app-internal storage only).
        processor?.process(chunk.copy(id = id), file)
    }
}

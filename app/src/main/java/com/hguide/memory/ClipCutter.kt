package com.hguide.memory

import com.hguide.audio.Wav
import com.hguide.data.AudioChunkDao
import java.io.File

/** Cuts [startMs, endMs) (trip-relative) out of the recorded chunks into a standalone WAV clip. */
class ClipCutter(private val chunks: AudioChunkDao) {
    suspend fun cut(tripId: Long, startMs: Long, endMs: Long, out: File): Boolean {
        val parts = mutableListOf<ByteArray>()
        for (c in chunks.forTrip(tripId)) {
            if (c.endMs <= startMs || c.startMs >= endMs) continue
            val f = File(c.path)
            if (!f.exists()) continue
            val pcm = Wav.readPcm(f)
            val from = Wav.msToBytes((startMs - c.startMs).coerceAtLeast(0)).coerceAtMost(pcm.size)
            val to = Wav.msToBytes(minOf(endMs, c.endMs) - c.startMs).coerceIn(from, pcm.size)
            if (to > from) parts += pcm.copyOfRange(from, to)
        }
        if (parts.isEmpty()) return false
        Wav.write(out, Wav.concat(parts))
        return true
    }
}

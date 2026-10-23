package com.hguide.family

import com.hguide.audio.Wav
import com.hguide.core.Segment
import com.hguide.data.AudioChunk
import com.hguide.data.Utterance
import com.hguide.data.UtteranceDao
import com.hguide.speech.SttGateway
import com.hguide.voice.DiarizationProvider
import com.hguide.voice.SpeakerMatcher
import java.io.File

/** STT + diarization + speaker matching for one recorded chunk → Utterance rows. Never logs transcript text. */
class ChunkProcessor(
    private val stt: SttGateway,
    private val diarizer: DiarizationProvider,
    private val matcher: SpeakerMatcher,
    private val utterances: UtteranceDao,
) {
    suspend fun process(chunk: AudioChunk, file: File) {
        val pcm = Wav.readPcm(file)
        val segments = diarizer.diarize(file)
        val rows = if (segments.isEmpty()) {
            // No diarization result: keep the chunk as one unattributed utterance if STT heard anything.
            val text = stt.transcribe(pcm).orEmpty().trim()
            if (text.isEmpty()) emptyList() else listOf(row(chunk, null, 0, chunk.endMs - chunk.startMs, text))
        } else {
            segments.mapNotNull { seg -> toRow(chunk, file, pcm, seg) }
        }
        if (rows.isNotEmpty()) utterances.insertAll(rows)
    }

    private suspend fun toRow(chunk: AudioChunk, file: File, pcm: ByteArray, seg: Segment): Utterance? {
        val text = seg.text.ifBlank {
            val from = Wav.msToBytes(seg.startMs).coerceIn(0, pcm.size)
            val to = Wav.msToBytes(seg.endMs).coerceIn(from, pcm.size)
            stt.transcribe(pcm.copyOfRange(from, to)).orEmpty()
        }.trim()
        if (text.isEmpty()) return null
        return row(chunk, matcher.match(file, seg), seg.startMs, seg.endMs, text)
    }

    private fun row(c: AudioChunk, personId: String?, relStart: Long, relEnd: Long, text: String) =
        Utterance(tripId = c.tripId, personId = personId, startMs = c.startMs + relStart, endMs = c.startMs + relEnd, text = text, lat = c.lat, lng = c.lng)
}

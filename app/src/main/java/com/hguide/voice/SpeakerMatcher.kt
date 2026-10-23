package com.hguide.voice

import com.hguide.core.Segment
import com.hguide.data.VoiceProfileDao
import java.io.File

/** Maps a diarized speaker to an enrolled person (personId) or null = unknown. Revoked profiles never match. */
class SpeakerMatcher(
    private val profiles: VoiceProfileDao,
    private val embedder: VoiceEmbedder,
    private val threshold: Float = 0.85f,
) {
    suspend fun match(audio: File, segment: Segment): String? {
        val candidates = profiles.all().filter { it.revokedAt == null && File(it.embeddingPath).exists() }
        if (candidates.isEmpty()) return null
        val v = embedder.embed(audio, segment.speakerId)
        val best = candidates.map { it.personId to Embeddings.cosine(v, Embeddings.load(File(it.embeddingPath))) }.maxByOrNull { it.second }
        return best?.takeIf { it.second >= threshold }?.first
    }
}

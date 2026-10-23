package com.hguide

import com.hguide.ai.LlmProvider
import com.hguide.core.Segment
import com.hguide.voice.DiarizationProvider
import com.hguide.voice.VoiceCloneProvider
import com.hguide.voice.VoiceEmbedder
import java.io.File

/**
 * Used when BuildConfig.USE_FAKES is false and no real vendor has been wired yet. They fail loudly (LLM, clone) or
 * return nothing (diarization, embeddings); every caller already degrades: templates for text, default TTS for voice,
 * whole-chunk STT instead of speaker segments.
 */
class NotConfiguredLlm : LlmProvider {
    override suspend fun completeJson(system: String, user: String): String = error("LLM vendor not configured")
}

class NotConfiguredDiarization : DiarizationProvider {
    override suspend fun diarize(audio: File): List<Segment> = emptyList()
}

class NotConfiguredEmbedder : VoiceEmbedder {
    override suspend fun embed(audio: File, hint: String?): FloatArray = FloatArray(0)
}

class NotConfiguredVoiceClone : VoiceCloneProvider {
    override suspend fun createProfile(personId: String, samples: List<File>) = Result.failure<Unit>(IllegalStateException("voice clone vendor not configured"))
    override suspend fun synthesize(personId: String, text: String) = Result.failure<File>(IllegalStateException("voice clone vendor not configured"))
    override suspend fun deleteProfile(personId: String) {}
}

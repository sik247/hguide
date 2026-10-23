package com.hguide.voice

import com.hguide.audio.Wav
import com.hguide.core.Segment
import org.json.JSONArray
import java.io.File
import kotlin.math.sqrt
import kotlin.random.Random

interface DiarizationProvider {
    /** Segments are relative to the start of [audio]. `text` may be empty when the vendor only separates speakers. */
    suspend fun diarize(audio: File): List<Segment>
}

interface VoiceCloneProvider {
    suspend fun createProfile(personId: String, samples: List<File>): Result<Unit>
    suspend fun synthesize(personId: String, text: String): Result<File>
    suspend fun deleteProfile(personId: String)
}

/** Speaker embedding (for matching diarized speakers to enrolled parents). [hint] is only used by the fake. */
interface VoiceEmbedder {
    suspend fun embed(audio: File, hint: String?): FloatArray
}

object Embeddings {
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var d = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val den = sqrt(na) * sqrt(nb)
        return if (den == 0f) 0f else d / den
    }

    fun mean(list: List<FloatArray>): FloatArray {
        val out = FloatArray(list.first().size)
        list.forEach { v -> for (i in out.indices) out[i] += v[i] / list.size }
        return out
    }

    fun save(file: File, v: FloatArray) { file.parentFile?.mkdirs(); file.writeText(v.joinToString(",")) }
    fun load(file: File): FloatArray = file.readText().split(",").map { it.toFloat() }.toFloatArray()
}

// ---- Fakes ---------------------------------------------------------------------------------------------------------

/** A prepared conversation: audio plus ground-truth speaker labels (from /sample). */
class SampleConversation(val name: String, val pcm: ByteArray, val segments: List<Segment>) {
    companion object {
        fun parseLabels(json: String): List<Segment> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Segment(o.getString("speaker"), o.getLong("start_ms"), o.getLong("end_ms"), o.getString("text"))
            }
        }
    }
}

/**
 * Locates the chunk inside a known sample's PCM (chunks are exact slices of it) and returns the labelled segments that
 * start within the chunk, shifted to chunk-relative time. Unknown or silent audio yields no segments.
 */
class FakeDiarizationProvider(private val samples: List<SampleConversation>) : DiarizationProvider {
    override suspend fun diarize(audio: File): List<Segment> {
        val chunk = Wav.readPcm(audio)
        // Chunks may start in silence, so anchor on the first non-silent stretch rather than the first bytes.
        val firstSound = chunk.indexOfFirst { it.toInt() != 0 }
        if (firstSound < 0) return emptyList()
        val anchor = firstSound - firstSound % 2
        val probe = chunk.copyOfRange(anchor, minOf(chunk.size, anchor + 4096))
        for (s in samples) {
            val hit = indexOf(s.pcm, probe)
            if (hit < 0 || hit < anchor) continue
            val offMs = Wav.bytesToMs(hit - anchor)
            val endMs = offMs + Wav.bytesToMs(chunk.size)
            return s.segments.filter { it.startMs in offMs until endMs }
                .map { it.copy(startMs = it.startMs - offMs, endMs = it.endMs - offMs) }
        }
        return emptyList()
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || hay.size < needle.size) return -1
        val first = needle[0]
        var i = 0
        while (i <= hay.size - needle.size) {
            if (hay[i] == first) {
                var j = 1
                while (j < needle.size && hay[i + j] == needle[j]) j++
                if (j == needle.size) return i
            }
            i += 2 // PCM16 alignment
        }
        return -1
    }
}

/** Deterministic pseudo-embedding derived from the speaker label. */
class FakeVoiceEmbedder : VoiceEmbedder {
    override suspend fun embed(audio: File, hint: String?): FloatArray {
        val r = Random((hint ?: "unknown").hashCode())
        return FloatArray(64) { r.nextFloat() * 2 - 1 }
    }
}

/** "Synthesizes" a beep sequence so the whole pipeline (cache, playback, deletion) is exercisable offline. */
class FakeVoiceCloneProvider(private val dir: File) : VoiceCloneProvider {
    private fun profileFile(personId: String) = File(dir, "clone/$personId.profile")
    fun cacheDir(personId: String) = File(dir, "cache/$personId")
    fun hasProfile(personId: String) = profileFile(personId).exists()

    override suspend fun createProfile(personId: String, samples: List<File>): Result<Unit> = runCatching {
        require(samples.isNotEmpty()) { "no samples" }
        profileFile(personId).apply { parentFile?.mkdirs() }.writeText("fake-clone:${samples.size}")
    }

    override suspend fun synthesize(personId: String, text: String): Result<File> = runCatching {
        check(hasProfile(personId)) { "no clone profile" }
        val out = File(cacheDir(personId), "${text.hashCode().toUInt()}.wav")
        val hz = 180.0 + (personId.hashCode().mod(120))
        val ms = (text.length * 60L).coerceIn(1200L, 6000L)
        Wav.write(out, Wav.concat(listOf(Wav.tone(ms / 2, hz), Wav.silence(100), Wav.tone(ms / 2, hz * 1.25))))
        out
    }

    override suspend fun deleteProfile(personId: String) {
        profileFile(personId).delete()
        cacheDir(personId).deleteRecursively()
    }
}

/**
 * Emulator-demo stand-in for a real clone vendor: same profile rules as [FakeVoiceCloneProvider] (no profile → failure),
 * but the line is spoken by Typecast (per-person voice, when a key is configured) or the platform TTS at a per-person
 * pitch instead of a beep, so a demo video has a voice.
 */
class DemoVoiceCloneProvider(
    private val base: FakeVoiceCloneProvider,
    private val tts: com.hguide.speech.PlatformTts,
    private val voice: com.hguide.speech.TypecastTts? = null,
    private val voices: com.hguide.speech.DemoVoices? = null,
) : VoiceCloneProvider by base {
    override suspend fun synthesize(personId: String, text: String): Result<File> {
        if (!base.hasProfile(personId)) return base.synthesize(personId, text)
        voices?.let { v -> voice?.toFile(text, v.forPerson(personId))?.let { return Result.success(it) } }
        val out = File(base.cacheDir(personId), "tts_${text.hashCode().toUInt()}.wav")
        if (out.exists() && out.length() > 0) return Result.success(out)
        val pitch = when (personId) { "dad" -> 0.78f; "mom" -> 1.12f; else -> 1f }
        return if (tts.toFile(text, pitch, out)) Result.success(out) else base.synthesize(personId, text)
    }
}

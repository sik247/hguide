package com.hguide.voice

import com.hguide.core.Clock
import com.hguide.core.log
import com.hguide.data.PersonDao
import com.hguide.data.VoiceProfile
import com.hguide.data.VoiceProfileDao
import java.io.File

/**
 * Voice enrollment (3 read-aloud sentences, once, while parked) plus the clone approval / revocation lifecycle.
 * Cloning is only ever enabled by the voice's owner approving it here (Person.voiceCloneApproved).
 */
class VoiceEnrollment(
    private val people: PersonDao,
    private val profiles: VoiceProfileDao,
    private val embedder: VoiceEmbedder,
    private val clone: VoiceCloneProvider,
    private val clock: Clock,
    private val baseDir: File,
) {
    val sentences = listOf(
        "오늘도 안전하게 운전하자.",
        "천천히 가도 괜찮아, 서두르지 마.",
        "조심히 다녀오고, 집에 오면 맛있는 거 먹자.",
    )

    private fun embeddingFile(personId: String) = File(baseDir, "voice_profiles/$personId.emb")
    private fun sampleDir(personId: String) = File(baseDir, "voice_profiles/$personId.samples")

    /** Stores the speaker-recognition profile (used to label who said what). Does NOT enable cloning. */
    suspend fun enroll(personId: String, samples: List<File>) {
        require(samples.size >= sentences.size) { "need ${sentences.size} samples" }
        val person = people.get(personId) ?: error("unknown person")
        val emb = Embeddings.mean(samples.map { embedder.embed(it, person.name) })
        Embeddings.save(embeddingFile(personId), emb)
        val dir = sampleDir(personId).apply { mkdirs() }
        samples.forEachIndexed { i, f -> f.copyTo(File(dir, "s$i.wav"), overwrite = true) }
        profiles.upsert(VoiceProfile(personId, embeddingFile(personId).path, samples.size, clock.nowMs(), null))
    }

    /** The owner approves AI voice cloning of their own voice. Requires an enrolled profile. */
    suspend fun approveClone(personId: String): Result<Unit> {
        val person = people.get(personId) ?: return Result.failure(IllegalStateException("unknown person"))
        val profile = profiles.get(personId)
        if (profile == null || profile.revokedAt != null) return Result.failure(IllegalStateException("not enrolled"))
        val samples = sampleDir(personId).listFiles()?.toList().orEmpty()
        val r = clone.createProfile(personId, samples)
        if (r.isSuccess) people.upsert(person.copy(voiceCloneApproved = true))
        return r
    }

    /** Revocation: approval off, clone profile + cached audio + embedding + samples deleted immediately. */
    suspend fun revokeClone(personId: String) {
        val person = people.get(personId)
        if (person != null) people.upsert(person.copy(voiceCloneApproved = false))
        clone.deleteProfile(personId)
        embeddingFile(personId).delete()
        sampleDir(personId).deleteRecursively()
        profiles.get(personId)?.let { profiles.upsert(it.copy(revokedAt = clock.nowMs())) }
        log("voice", "clone revoked: $personId")
    }

    /** True only if the owner approved AND the profile is live. Every clone synthesis goes through this. */
    suspend fun cloneAllowed(personId: String): Boolean {
        val p = people.get(personId) ?: return false
        val prof = profiles.get(personId)
        return p.voiceCloneApproved && prof != null && prof.revokedAt == null
    }
}

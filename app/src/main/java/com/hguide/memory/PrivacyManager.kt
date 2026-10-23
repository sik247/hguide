package com.hguide.memory

import com.hguide.core.Clock
import com.hguide.data.AudioChunkDao
import com.hguide.data.GreetingLogDao
import com.hguide.data.MemoryClipDao
import com.hguide.data.SettingsStore
import com.hguide.data.UtteranceDao
import java.io.File

/** Raw-audio retention (30 days default; highlight clips are kept) and the "delete everything" switch. */
class PrivacyManager(
    private val chunks: AudioChunkDao,
    private val utterances: UtteranceDao,
    private val clips: MemoryClipDao,
    private val greetings: GreetingLogDao,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val baseDir: File,
) {
    /** Deletes raw chunk files + rows older than the retention window. Returns how many chunks were removed. */
    suspend fun purgeExpiredRawAudio(): Int {
        val cutoff = clock.nowMs() - settings.rawAudioRetentionDays * 86_400_000L
        val old = chunks.createdBefore(cutoff)
        old.forEach { File(it.path).delete(); chunks.delete(it.id) }
        return old.size
    }

    /** Settings → delete all recordings, transcripts, clips and greeting audio. Profiles/places stay (separate controls). */
    suspend fun deleteAllRecordings() {
        chunks.deleteAll(); utterances.deleteAll()
        clips.all().forEach { File(it.audioPath).delete() }
        clips.deleteAll(); greetings.deleteAll()
        File(baseDir, "audio").deleteRecursively()
        File(baseDir, "clips").deleteRecursively()
    }
}

package com.hguide.voice

import com.hguide.core.SpeechPriority
import com.hguide.core.log
import java.io.File

data class Spoken(val text: String, val viaClone: Boolean, val played: Boolean, val audioPath: String?)

/**
 * Speaks a line "as" a family member: the cloned voice if (and only if) the owner approved it, otherwise the default
 * Pleos TTS voice. This is the single place cloned audio is produced, and it only ever plays on the car speakers —
 * nothing here can send audio to a call or message.
 */
class VoiceSpeaker(
    private val enrollment: VoiceEnrollment,
    private val clone: VoiceCloneProvider,
    private val arbiter: SpeechArbiter,
) {
    suspend fun speakAs(personId: String, text: String, priority: SpeechPriority): Spoken {
        if (enrollment.cloneAllowed(personId)) {
            val synth = clone.synthesize(personId, text)
            val file = synth.getOrNull()
            if (file != null) {
                val ok = arbiter.play(SpeechRequest(priority, file = file, aiVoice = true, caption = text))
                return Spoken(text, viaClone = true, played = ok, audioPath = file.path)
            }
            log("voice", "clone synthesis failed, falling back to default TTS")
        }
        val ok = arbiter.play(SpeechRequest(priority, text = text))
        return Spoken(text, viaClone = false, played = ok, audioPath = null)
    }

    suspend fun playRecorded(file: File, priority: SpeechPriority): Boolean =
        arbiter.play(SpeechRequest(priority, file = file, aiVoice = false))
}

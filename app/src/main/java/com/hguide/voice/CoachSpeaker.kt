package com.hguide.voice

import com.hguide.ai.LlmProvider
import com.hguide.ai.PromptLibrary
import com.hguide.ai.PromptNames
import com.hguide.ai.extractJsonObject
import com.hguide.core.DrivingEventType
import com.hguide.core.EventLog
import com.hguide.core.SpeechPriority
import com.hguide.data.DrivingEventDao
import com.hguide.data.PersonDao
import com.hguide.data.SettingsStore
import com.hguide.engine.LessonState

/** Parent-voice coaching cues for lesson events. Highest speech priority. Falls back to the default TTS voice. */
class CoachSpeaker(
    private val llm: LlmProvider,
    private val prompts: PromptLibrary,
    private val speaker: VoiceSpeaker,
    private val styles: StyleSamples,
    private val people: PersonDao,
    private val eventsDao: DrivingEventDao,
    private val settings: SettingsStore,
    private val lesson: LessonState,
    private val log: EventLog? = null,
    private val minGapMs: Long = 8_000,
) {
    private var lastCueMs = com.hguide.engine.NEVER
    private val lastByType = mutableMapOf<DrivingEventType, Long>()

    /** Returns the spoken text, or null if no cue was given (lesson off, rate-limited, or no voice configured). */
    suspend fun cue(type: DrivingEventType, tripId: Long, nowMs: Long): String? {
        if (!lesson.active.value) return null
        if (nowMs - lastCueMs < minGapMs) return null
        if (nowMs - (lastByType[type] ?: com.hguide.engine.NEVER) < 20_000) return null
        val personId = voiceOwner() ?: return null
        val person = people.get(personId) ?: return null

        val count = eventsDao.countSince(tripId, type, nowMs - 10 * 60_000L)
        val p = prompts.fill(
            PromptNames.COACH,
            mapOf("speaker_role" to person.name, "style_samples" to styles.forPerson(personId), "event_type" to type.name, "lesson" to lesson.lessonName, "count" to count.toString()),
        )
        val text = runCatching { extractJsonObject(llm.completeJson(p.system, p.user))?.optString("text") }.getOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 25 } ?: fallback(type)
        val spoken = speaker.speakAs(personId, text, SpeechPriority.COACH)
        if (!spoken.played) return null
        lastCueMs = nowMs
        lastByType[type] = nowMs
        log?.add("🚗 코칭(${type.name}) — ${person.name}${if (spoken.viaClone) " AI 목소리" else " 기본 음성"}")
        return text
    }

    private suspend fun voiceOwner(): String? =
        settings.greetingVoicePersonId ?: people.all().firstOrNull { it.role == com.hguide.core.PersonRole.PARENT }?.id

    private fun fallback(type: DrivingEventType) = when (type) {
        DrivingEventType.LANE_CHANGE_NO_SIGNAL -> "다음엔 깜빡이 먼저 켜자."
        DrivingEventType.HARSH_DECEL -> "괜찮아, 천천히 가자."
        DrivingEventType.ZONE_ENTER -> "여기는 천천히 가자."
    }
}

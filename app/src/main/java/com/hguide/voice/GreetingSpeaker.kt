package com.hguide.voice

import com.hguide.ai.LlmProvider
import com.hguide.ai.PromptLibrary
import com.hguide.ai.PromptNames
import com.hguide.ai.extractJsonObject
import com.hguide.core.Clock
import com.hguide.core.EventLog
import com.hguide.core.SpeechPriority
import com.hguide.core.TripContext
import com.hguide.data.GreetingLog
import com.hguide.data.GreetingLogDao
import com.hguide.data.PersonDao
import com.hguide.data.SettingsStore
import java.io.File
import java.time.format.TextStyle
import java.util.Locale

sealed interface GreetResult {
    data class Played(val text: String, val viaClone: Boolean, val recorded: Boolean) : GreetResult
    data object NotStationary : GreetResult
    data object Off : GreetResult
    data object NoGreetingForContext : GreetResult
    data object AlreadyGreeted : GreetResult
    data object Dropped : GreetResult
}

/** Trip-start greeting in a parent's voice: once per context per day, only while stationary. */
class GreetingSpeaker(
    private val llm: LlmProvider,
    private val prompts: PromptLibrary,
    private val speaker: VoiceSpeaker,
    private val styles: StyleSamples,
    private val people: PersonDao,
    private val logs: GreetingLogDao,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val events: EventLog? = null,
) {
    suspend fun maybeGreet(ctx: TripContext, stationary: Boolean, childName: String = "우리 아이"): GreetResult {
        if (!stationary) return GreetResult.NotStationary
        if (ctx == TripContext.OTHER) return GreetResult.NoGreetingForContext
        val personId = settings.greetingVoicePersonId ?: return GreetResult.Off
        val person = people.get(personId) ?: return GreetResult.Off
        val now = clock.dateTime()
        val date = now.toLocalDate().toString()
        if (logs.forDateAndContext(date, ctx) != null) return GreetResult.AlreadyGreeted

        // "직접 남기기": a recording made by the parent takes priority over any generated voice.
        val recorded = settings.customGreetingPath(personId, ctx)?.let(::File)?.takeIf { it.exists() }
        if (recorded != null) {
            if (!speaker.playRecorded(recorded, SpeechPriority.GREETING)) return GreetResult.Dropped
            logs.insert(GreetingLog(date = date, context = ctx, personId = personId, text = "(직접 녹음)", audioPath = recorded.path, playedAt = clock.nowMs()))
            events?.add("🎙 ${person.name} 직접 녹음 인사 재생")
            return GreetResult.Played("(직접 녹음)", viaClone = false, recorded = true)
        }

        val recent = logs.since(clock.nowMs() - 7 * 86_400_000L).map { it.text }
        val text = generate(personId, person.name, ctx, now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN), "%02d:%02d".format(now.hour, now.minute), childName, recent)
        val spoken = speaker.speakAs(personId, text, SpeechPriority.GREETING)
        if (!spoken.played) return GreetResult.Dropped
        logs.insert(GreetingLog(date = date, context = ctx, personId = personId, text = text, audioPath = spoken.audioPath, playedAt = clock.nowMs()))
        events?.add("👋 인사(${ctx.name}) — ${person.name}${if (spoken.viaClone) " AI 목소리" else " 기본 음성"}")
        return GreetResult.Played(text, spoken.viaClone, recorded = false)
    }

    private suspend fun generate(personId: String, role: String, ctx: TripContext, weekday: String, time: String, child: String, recent: List<String>): String {
        val p = prompts.fill(
            PromptNames.GREETING,
            mapOf(
                "speaker_role" to role,
                "style_samples" to styles.forPerson(personId),
                "context" to ctx.name,
                "recent_greetings" to (recent.joinToString(" / ").ifBlank { "없음" }),
                "weekday" to weekday, "time" to time, "child_name" to child,
            ),
        )
        val text = runCatching { extractJsonObject(llm.completeJson(p.system, p.user))?.optString("text") }.getOrNull()?.trim()
        return text?.takeIf { it.isNotEmpty() && it.length <= 40 && it !in recent } ?: fallback(ctx)
    }

    private fun fallback(ctx: TripContext) = when (ctx) {
        TripContext.MORNING_COMMUTE -> "오늘도 파이팅, 아침 꼭 챙겨 먹어."
        TripContext.EVENING_HOME -> "오늘도 고생했어. 조심히 들어와."
        TripContext.LATE_NIGHT_HOME -> "늦게까지 고생했어. 졸리면 쉬었다 와."
        TripContext.WEEKEND -> "주말이다, 즐겁게 다녀와."
        TripContext.OTHER -> "조심히 다녀와."
    }
}

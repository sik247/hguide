package com.hguide.memory

import com.hguide.ai.LlmProvider
import com.hguide.ai.PromptLibrary
import com.hguide.ai.PromptNames
import com.hguide.ai.extractJsonObject
import com.hguide.data.Utterance

data class Highlight(
    val startMs: Long, val endMs: Long, val speaker: String, val personId: String,
    val title: String, val summary: String, val emotion: String, val lat: Double, val lng: Double,
)

/**
 * Sends the trip transcript to the LLM, then validates what comes back: max 5, clips snapped to utterance boundaries,
 * 5–20 s long, sensitive content dropped even if the model picked it.
 */
class HighlightExtractor(private val llm: LlmProvider, private val prompts: PromptLibrary) {
    companion object {
        const val MAX_CLIPS = 5
        const val MIN_MS = 5_000L
        const val MAX_MS = 20_000L
        val EMOTIONS = setOf("proud", "funny", "warm", "milestone", "story")
    }

    suspend fun extract(
        date: String, startPlace: String, endPlace: String,
        utterances: List<Utterance>, personNames: Map<String, String>, // personId -> name
    ): List<Highlight> {
        if (utterances.isEmpty()) return emptyList()
        val sorted = utterances.sortedBy { it.startMs }
        val nameToId = personNames.entries.associate { it.value to it.key }
        val transcript = sorted.joinToString("\n") {
            val s = it.startMs / 1000
            "[%02d:%02d] %s: %s".format(s / 60, s % 60, it.personId?.let(personNames::get) ?: "모름", it.text)
        }
        val p = prompts.fill(PromptNames.HIGHLIGHT, mapOf("date" to date, "start_place" to startPlace, "end_place" to endPlace, "utterances" to transcript))
        val raw = runCatching { llm.completeJson(p.system, p.user) }.getOrNull() ?: return emptyList()
        val arr = extractJsonObject(raw)?.optJSONArray("highlights") ?: return emptyList()

        val out = mutableListOf<Highlight>()
        for (i in 0 until arr.length()) {
            if (out.size >= MAX_CLIPS) break
            val o = arr.optJSONObject(i) ?: continue
            val wantStart = o.optLong("start_ms", -1)
            val wantEnd = o.optLong("end_ms", -1)
            if (wantStart < 0) continue

            val first = sorted.indexOfFirst { wantStart in it.startMs..it.endMs }.takeIf { it >= 0 }
                ?: sorted.indexOfFirst { it.startMs >= wantStart }.takeIf { it >= 0 } ?: continue
            val start = sorted[first].startMs
            if (out.any { start < it.endMs && it.startMs < start + MIN_MS }) continue // overlaps an earlier pick

            // Grow by whole utterances up to what the model asked for, capped at 20 s; extend to >= 5 s if needed.
            var last = first
            while (last + 1 < sorted.size) {
                val n = sorted[last + 1]
                val wantsMore = n.startMs <= wantEnd || sorted[last].endMs - start < MIN_MS
                if (!wantsMore || n.endMs - start > MAX_MS) break
                last++
            }
            var end = sorted[last].endMs
            if (end - start > MAX_MS) end = start + MAX_MS
            if (end - start < MIN_MS) end = start + MIN_MS

            val covered = sorted.subList(first, last + 1)
            if (covered.any { Sensitivity.isSensitive(it.text) }) continue

            val speaker = o.optString("speaker").ifBlank { covered.first().personId?.let(personNames::get) ?: "가족" }
            val personId = nameToId[speaker] ?: covered.first().personId ?: "unknown"
            val emotion = o.optString("emotion").takeIf { it in EMOTIONS } ?: "warm"
            out += Highlight(
                startMs = start, endMs = end, speaker = speaker, personId = personId,
                title = o.optString("title", "추억").trim().take(12).ifBlank { "추억" },
                summary = o.optString("summary").trim().take(60),
                emotion = emotion, lat = covered.first().lat, lng = covered.first().lng,
            )
        }
        return out
    }
}

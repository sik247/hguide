package com.hguide.voice

import com.hguide.data.UtteranceDao

/** Real sentences a parent said (from family-mode recordings) used to steer the LLM's tone. */
class StyleSamples(private val utterances: UtteranceDao) {
    suspend fun forPerson(personId: String, limit: Int = 8): String {
        val rows = utterances.recentBy(personId, limit).map { it.text.trim().take(60) }.filter { it.length >= 4 }
        return if (rows.isEmpty()) "(샘플 없음 — 평소 말투처럼 따뜻한 반말)" else rows.joinToString("\n") { "- $it" }
    }
}

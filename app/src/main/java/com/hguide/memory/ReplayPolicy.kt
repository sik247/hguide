package com.hguide.memory

import com.hguide.data.MemoryClip

data class ReplayState(
    val nowMs: Long,
    val lessonActive: Boolean,
    val turnSignalOn: Boolean,
    val lastHarshDecelMs: Long?,
    val playedToday: Int,
    val speechBusy: Boolean,
    val askedTodayClipIds: Set<Long> = emptySet(),
)

sealed interface ReplayDecision {
    data object Allow : ReplayDecision
    data class Deny(val reason: String) : ReplayDecision
}

/** F4 rules: same-place cooldown 30 days, max 2 plays/day, never during a lesson, with a turn signal on, or 10 s after a hard brake. */
object ReplayPolicy {
    const val COOLDOWN_MS = 30L * 86_400_000L
    const val MAX_PER_DAY = 2
    const val HARSH_QUIET_MS = 10_000L

    fun decide(clip: MemoryClip, s: ReplayState): ReplayDecision = when {
        !clip.proximityEnabled -> ReplayDecision.Deny("place alert off for this clip")
        s.lessonActive -> ReplayDecision.Deny("lesson in progress")
        s.turnSignalOn -> ReplayDecision.Deny("turn signal on")
        s.lastHarshDecelMs != null && s.nowMs - s.lastHarshDecelMs < HARSH_QUIET_MS -> ReplayDecision.Deny("recent hard braking")
        s.speechBusy -> ReplayDecision.Deny("another voice is playing")
        s.playedToday >= MAX_PER_DAY -> ReplayDecision.Deny("daily limit reached")
        clip.lastPlayedAt != null && s.nowMs - clip.lastPlayedAt < COOLDOWN_MS -> ReplayDecision.Deny("30-day cooldown")
        clip.id in s.askedTodayClipIds -> ReplayDecision.Deny("already asked today")
        else -> ReplayDecision.Allow
    }
}

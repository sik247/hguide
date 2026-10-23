package com.hguide.ui

import com.hguide.core.FamilyState
import com.hguide.core.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ~30 s scripted walkthrough for recording a demo video (fake-gateway builds only). Drives the same screens and demo
 * actions a presenter would tap, and waits on real state (speech finished, consent done) rather than fixed sleeps.
 */
suspend fun runAutoplay(vm: MainViewModel, nav: Nav) {
    val d = vm.demo ?: return
    suspend fun until(timeoutMs: Long, cond: () -> Boolean) = withTimeoutOrNull(timeoutMs) { while (!cond()) delay(80) }
    suspend fun speechDone(timeoutMs: Long = 9_000) {
        until(1_500) { vm.speaking.value != null }
        until(timeoutMs) { vm.speaking.value == null }
    }

    val t0 = System.currentTimeMillis()
    // Clean slate, starting on 엄마 so the voice change is visible.
    nav.go(Tab.HOME)
    d.resetForDemo().join()
    vm.setGreetingVoice("mom")
    delay(1_600)

    // 1) Voice: 아빠's AI voice, previewed, then chosen.
    nav.detail = Detail.VoicePick
    delay(900)
    vm.scriptPick("dad")
    delay(600)
    vm.previewVoice("dad")
    speechDone(5_000)
    vm.setGreetingVoice("dad")
    vm.scriptPick(null)
    nav.detail = null
    delay(1_100)

    // 2) 운전 시작: lesson on, gear D → greeting in 아빠's voice, then everyone says "응" to recording.
    vm.lesson(true)
    delay(700)
    d.morningStart()
    until(9_000) { vm.familyState.value == FamilyState.ASKING }
    d.answerAllYes().join()
    until(4_000) { vm.familyState.value == FamilyState.RECORDING }
    delay(500)

    // 3) Coaching: lane change without a signal.
    d.coachLaneChange()
    speechDone()
    delay(300)

    // 4) Passing a saved memory: the car asks first, "응", the clip plays.
    d.passMemory()
    speechDone()                       // "…들어볼까요?"
    delay(300)
    vm.fakeStt?.answer("응")
    until(3_000) { vm.speaking.value?.file != null }
    until(15_000) { vm.speaking.value == null } // the whole clip, never cut mid-sentence
    delay(1_500)

    // 5) Arrive and park: the trip's highlights become new memories.
    d.arriveAndPark().join()
    nav.go(Tab.MEMORIES)
    delay(1_800)
    (vm.clips.value.firstOrNull { it.title == "아빠와 첫 드라이브" } ?: vm.clips.value.firstOrNull())?.let { c ->
        nav.detail = Detail.Player(c.id)
        delay(2_000)
    }
    delay(300)
    nav.go(Tab.HOME)
    log("autoplay", "done in ${System.currentTimeMillis() - t0} ms")
}

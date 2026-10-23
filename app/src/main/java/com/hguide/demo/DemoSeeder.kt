package com.hguide.demo

import com.hguide.AppContainer
import com.hguide.audio.Wav
import com.hguide.core.PersonRole
import com.hguide.core.PlaceType
import com.hguide.data.Person
import com.hguide.data.Place
import com.hguide.data.Utterance
import java.io.File

/**
 * First-run demo data (fake-vendor builds only): a family of three, home/work, enrolled voice profiles, a few lines of
 * "how 아빠/엄마 talk" for the LLM style prompt. 아빠's clone approval is pre-set to simulate the owner approving it.
 */
class DemoSeeder(private val c: AppContainer) {
    suspend fun seedIfEmpty() {
        val people = c.db.personDao()
        if (people.all().isNotEmpty()) return
        val now = c.clock.nowMs()
        val family = listOf(
            Person("dad", "아빠", PersonRole.PARENT, false, now),
            Person("mom", "엄마", PersonRole.PARENT, false, now + 1),
            Person("me", "나", PersonRole.CHILD, false, now + 2),
        )
        family.forEach { people.upsert(it) }
        c.db.placeDao().upsert(Place("HOME", PlaceType.HOME, DemoActions.HOME.lat, DemoActions.HOME.lng, 300))
        c.db.placeDao().upsert(Place("WORK", PlaceType.WORK, DemoActions.WORK.lat, DemoActions.WORK.lng, 300))

        val dir = File(c.baseDir, "voice_seed").apply { mkdirs() }
        for (p in family) {
            val samples = (0 until c.enrollment.sentences.size).map { i -> File(dir, "${p.id}_$i.wav").also { Wav.write(it, Wav.silence(1000)) } }
            c.enrollment.enroll(p.id, samples)
        }
        c.enrollment.approveClone("dad")

        val style = listOf(
            "dad" to listOf("천천히 해도 괜찮아, 아빠가 옆에 있잖아.", "오늘도 고생했다, 조심히 들어와라.", "그래 잘하고 있어, 급할 거 없어."),
            "mom" to listOf("밥은 먹고 다니니? 꼭 챙겨 먹어.", "수고했어 우리 딸, 안전하게 와.", "천천히 가, 엄마는 괜찮아."),
        )
        c.db.utteranceDao().insertAll(style.flatMap { (id, lines) -> lines.mapIndexed { i, t -> Utterance(tripId = -1, personId = id, startMs = i * 1000L, endMs = i * 1000L + 900, text = t, lat = 0.0, lng = 0.0) } })

        c.settings.greetingVoicePersonId = "dad"
        c.settings.driverPersonId = "me"
    }
}

package com.hguide.demo

import com.hguide.AppContainer
import com.hguide.audio.Wav
import com.hguide.core.GeoPoint
import com.hguide.core.PersonRole
import com.hguide.core.PlaceType
import com.hguide.data.MemoryClip
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

    /**
     * Demo playlist: a few family moments (the scolding is never one of them), placed along the demo route so the
     * place-replay can be shown. Seeded once; [DemoActions.resetForDemo] seeds again.
     */
    suspend fun seedMemories(force: Boolean = false) {
        val prefs = c.appContext.getSharedPreferences("demo", android.content.Context.MODE_PRIVATE)
        if (!force && prefs.getBoolean("memories_seeded", false)) return
        val dao = c.db.memoryClipDao()
        val now = c.clock.nowMs()
        val day = 86_400_000L
        val dir = File(c.baseDir, "clips").apply { mkdirs() }
        // Audio: Typecast dialogues in assets/demo_memories (sample/generate_demo_memories.py), lengths in ms.
        data class Seed(val asset: String, val lengthMs: Long, val person: String, val title: String, val summary: String, val emotion: String, val at: GeoPoint, val place: String, val daysAgo: Int)
        val seeds = listOf(
            Seed("merge_practice", 8_180, "dad", "고속도로 합류 연습", "천천히 가속하며 처음 합류해 본 날", "calm", DemoActions.MERGE_ZONE.center, "판교 IC", 40),
            Seed("parking_success", 9_980, "mom", "한 번에 성공한 주차", "엄마가 \"대단하다\"며 박수 친 순간", "proud", GeoPoint(37.3900, 127.1150), "마트 주차장", 20),
            Seed("dad_rookie_story", 8_280, "dad", "아빠의 초보 시절 이야기", "아빠도 처음엔 차선을 못 찾았대요", "funny", GeoPoint(37.3985, 127.1100), "회사 가는 길", 12),
            Seed("first_drive", 8_180, "dad", "아빠와 첫 드라이브", "\"이제 혼자 가도 되겠다\" — 첫 고속도로", "proud", DemoActions.MEMORY_SPOT, "한강 진입로", 3),
        )
        for ((i, sd) in seeds.withIndex()) {
            val out = File(dir, "demo_${sd.asset}.mp3")
            c.appContext.assets.open("demo_memories/${sd.asset}.mp3").use { input -> out.outputStream().use { input.copyTo(it) } }
            dao.insert(
                MemoryClip(
                    tripId = -1, personId = sd.person, title = sd.title, summary = sd.summary, emotionTag = sd.emotion, audioPath = out.path,
                    startMs = 0, endMs = sd.lengthMs, lat = sd.at.lat, lng = sd.at.lng, placeName = sd.place, createdAt = now - sd.daysAgo * day + i,
                ),
            )
        }
        prefs.edit().putBoolean("memories_seeded", true).apply()
    }
}

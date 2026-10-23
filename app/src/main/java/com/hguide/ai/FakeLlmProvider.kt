package com.hguide.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * Deterministic stand-in for an LLM. Understands the four prompts in assets/prompts and returns the same JSON schemas.
 * Highlights are picked by keyword rules that mirror the prompt's "pick / don't pick" lists.
 */
class FakeLlmProvider : LlmProvider {
    override suspend fun completeJson(system: String, user: String): String = when {
        "편집자" in system -> highlights(user)
        "짧은 인사" in system -> greeting(system, user)
        "코치" in system -> coach(user)
        "추억 클립을 틀기 전에" in system -> placeIntro(user)
        else -> "{}"
    }

    // ---- highlights -------------------------------------------------------------------------------------------

    private data class Line(val ms: Long, val speaker: String, val text: String)

    private val negative = listOf("소리", "왜 그랬", "짜증", "싸우", "다퉜", "화가", "위험", "죽을", "병원", "아프", "돈", "월급", "욕", "험담", "무서")
    private val praise = listOf("잘했", "잘하네", "대단", "혼자 가도", "최고", "훌륭")
    private val funny = listOf("ㅋㅋ", "하하", "웃기", "웃었", "농담")
    private val warm = listOf("고마워", "고맙", "사랑해", "보고 싶")
    private val milestone = listOf("처음", "첫 ")
    private val story = listOf("어릴 때", "옛날", "왔던")

    private fun parse(user: String): List<Line> {
        val re = Regex("""\[(\d+):(\d{2})]\s*([^:]+):\s*(.*)""")
        return user.lines().mapNotNull { re.find(it)?.destructured?.let { (m, s, who, t) -> Line((m.toLong() * 60 + s.toLong()) * 1000, who.trim(), t.trim()) } }
    }

    private fun emotionOf(t: String): Pair<String, String>? = when {
        milestone.any { it in t } && praise.any { it in t } -> "milestone" to "첫 도전 성공"
        praise.any { it in t } -> "proud" to "칭찬 한마디"
        funny.any { it in t } -> "funny" to "함께 웃은 순간"
        warm.any { it in t } -> "warm" to "다정한 한마디"
        story.any { it in t } -> "story" to "이곳의 이야기"
        milestone.any { it in t } -> "milestone" to "처음 해낸 일"
        else -> null
    }

    private fun highlights(user: String): String {
        val lines = parse(user)
        val out = JSONArray()
        var lastEnd = -1L
        for ((i, l) in lines.withIndex()) {
            if (out.length() >= 5) break
            if (l.ms < lastEnd) continue
            if (negative.any { it in l.text }) continue
            val (emotion, title) = emotionOf(l.text) ?: continue
            // extend to the following lines until ~10 s, never across a negative line
            var end = l.ms + 6_000
            for (n in lines.drop(i + 1)) {
                if (n.ms > l.ms + 10_000 || negative.any { it in n.text }) break
                end = maxOf(end, n.ms + 3_000)
            }
            lastEnd = end
            out.put(
                JSONObject()
                    .put("start_ms", l.ms).put("end_ms", end).put("speaker", l.speaker)
                    .put("title", title).put("summary", "${l.speaker}: ${l.text}".take(40))
                    .put("emotion", emotion).put("lat", 0.0).put("lng", 0.0),
            )
        }
        return JSONObject().put("highlights", out).toString()
    }

    // ---- greeting ---------------------------------------------------------------------------------------------

    private val greetings = mapOf(
        "MORNING_COMMUTE" to listOf("오늘도 보고 싶다. 출근 잘하고, 아침 꼭 챙겨 먹어.", "좋은 아침이야. 오늘도 힘내자, 아침은 먹었지?", "오늘도 파이팅. 컨디션 잘 챙기고."),
        "EVENING_HOME" to listOf("오늘도 고생 많았어. 조심히 들어와.", "수고했어. 안전하게 들어와.", "오늘 하루 잘 버텼네. 천천히 와."),
        "LATE_NIGHT_HOME" to listOf("야근이네, 오늘도 고생했어. 졸리면 쉬었다 와.", "늦게까지 고생했다. 졸리면 꼭 쉬어가.", "고생 많았어. 천천히 안전하게 와."),
        "WEEKEND" to listOf("주말이다! 오늘은 가볍게 즐기자.", "쉬는 날이네, 즐겁게 다녀와."),
        "OTHER" to listOf("오늘도 안전운전하자.", "조심히 다녀와."),
    )

    private fun greeting(system: String, user: String): String {
        val ctx = Regex("""context:\s*([A-Z_]+)""").find(user)?.groupValues?.get(1) ?: "OTHER"
        val recent = Regex("""최근 7일 인사\((.*?)\)""", RegexOption.DOT_MATCHES_ALL).find(system)?.groupValues?.get(1).orEmpty()
        val options = greetings[ctx] ?: greetings.getValue("OTHER")
        val pick = options.firstOrNull { it !in recent } ?: options.first()
        return JSONObject().put("text", pick).toString()
    }

    // ---- coach ------------------------------------------------------------------------------------------------

    private fun coach(user: String): String {
        val ev = Regex("""event:\s*([A-Z_]+)""").find(user)?.groupValues?.get(1)
        val count = Regex("""횟수:\s*(\d+)""").find(user)?.groupValues?.get(1)?.toInt() ?: 0
        val text = when (ev) {
            "LANE_CHANGE_NO_SIGNAL" -> "다음엔 깜빡이 먼저 켜고 가자."
            "HARSH_DECEL" -> if (count >= 2) "괜찮아, 천천히 숨 쉬고 가자." else "앞차와 거리를 조금 더 두자."
            "ZONE_ENTER" -> "여기 합류 구간이야, 속도 맞추자."
            else -> "천천히, 안전하게 가자."
        }
        return JSONObject().put("text", text).toString()
    }

    // ---- place intro ------------------------------------------------------------------------------------------

    private fun placeIntro(user: String): String {
        val who = Regex("""말한 사람=([^,]+)""").find(user)?.groupValues?.get(1)?.trim() ?: "가족"
        val whenText = Regex("""날짜=([^,]+)""").find(user)?.groupValues?.get(1)?.trim() ?: "예전에"
        return JSONObject().put("text", "$whenText, ${who}랑 왔던 곳이에요. 들어볼까요?").toString()
    }
}

package com.hguide.memory

import com.hguide.ai.LlmProvider
import com.hguide.ai.PromptLibrary
import com.hguide.ai.PromptNames
import com.hguide.ai.extractJsonObject
import com.hguide.core.Clock
import com.hguide.core.EventLog
import com.hguide.core.Geo
import com.hguide.core.GeoPoint
import com.hguide.core.SpeechPriority
import com.hguide.core.log
import com.hguide.data.MemoryClip
import com.hguide.data.MemoryClipDao
import com.hguide.data.PersonDao
import com.hguide.data.SettingsStore
import com.hguide.engine.DrivingGuards
import com.hguide.engine.LessonState
import com.hguide.location.LocationGateway
import com.hguide.location.ProximityEvent
import com.hguide.speech.Phrases
import com.hguide.speech.SttGateway
import com.hguide.voice.SpeechArbiter
import com.hguide.voice.SpeechRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Registers proximity alerts for saved clips (nearest [maxAlerts] only), and on entry asks first — "들어볼까요?" —
 * before playing anything. Rules live in [ReplayPolicy].
 */
class PlaceReplayController(
    private val location: LocationGateway,
    private val clips: MemoryClipDao,
    private val people: PersonDao,
    private val settings: SettingsStore,
    private val llm: LlmProvider,
    private val prompts: PromptLibrary,
    private val arbiter: SpeechArbiter,
    private val stt: SttGateway,
    private val guards: DrivingGuards,
    private val lesson: LessonState,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val events: EventLog? = null,
    val maxAlerts: Int = 20,
    private val resyncDistanceM: Double = 2_000.0,
    private val answerTimeoutMs: Long = 7_000,
) {
    private val alertToClip = mutableMapOf<String, Long>()
    private val clipToAlert = mutableMapOf<Long, String>()
    private val askedToday = mutableMapOf<Long, LocalDate>()
    private var lastSyncAt: GeoPoint? = null

    val registeredAlerts get() = alertToClip.size

    fun start() {
        scope.launch { location.proximityEvents.collect { onProximity(it) } }
        scope.launch {
            location.fixes.collect { f ->
                val last = lastSyncAt
                if (last == null || Geo.distanceM(last, f.point) > resyncDistanceM) sync(f.point)
            }
        }
    }

    /** Re-registers alerts around [center]; also call after clips are added, removed or toggled. */
    suspend fun sync(center: GeoPoint) {
        lastSyncAt = center
        val radius = settings.proximityRadiusM.toFloat()
        val wanted = clips.all().filter { it.proximityEnabled }
            .sortedBy { Geo.distanceM(center, GeoPoint(it.lat, it.lng)) }
            .take(maxAlerts)
        val wantedIds = wanted.map { it.id }.toSet()
        for ((clipId, alertId) in clipToAlert.toMap()) {
            if (clipId !in wantedIds) { location.removeProximityAlert(alertId); clipToAlert.remove(clipId); alertToClip.remove(alertId) }
        }
        for (c in wanted) {
            if (c.id in clipToAlert) continue
            val id = location.addProximityAlert(GeoPoint(c.lat, c.lng), radius) ?: continue
            clipToAlert[c.id] = id; alertToClip[id] = c.id
        }
    }

    suspend fun onProximity(ev: ProximityEvent) {
        if (!ev.enter) return
        val clip = alertToClip[ev.alertId]?.let { clips.get(it) } ?: return
        val now = clock.nowMs()
        val today = clock.dateTime().toLocalDate()
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val state = ReplayState(
            nowMs = now, lessonActive = lesson.active.value, turnSignalOn = guards.turnSignalOn,
            lastHarshDecelMs = guards.lastHarshDecelMs, playedToday = clips.countPlayedSince(startOfDay),
            speechBusy = arbiter.busy, askedTodayClipIds = askedToday.filterValues { it == today }.keys,
        )
        val d = ReplayPolicy.decide(clip, state)
        if (d is ReplayDecision.Deny) { log("replay", "skip clip ${clip.id}: ${d.reason}"); return }

        val question = question(clip, now)
        if (!arbiter.play(SpeechRequest(SpeechPriority.MEMORY, text = question))) return
        askedToday[clip.id] = today
        val answer = stt.listenForPhrase(answerTimeoutMs)
        if (!Phrases.isYes(answer)) { events?.add("🔇 추억 재생 안 함"); return }
        val file = File(clip.audioPath)
        if (!file.exists()) return
        if (arbiter.play(SpeechRequest(SpeechPriority.MEMORY, file = file, caption = clip.title))) {
            clips.setLastPlayed(clip.id, clock.nowMs())
            events?.add("💛 추억 재생: ${clip.title}")
        }
    }

    private suspend fun question(c: MemoryClip, now: Long): String {
        val whenText = RelativeTime.describe(c.createdAt, now, clock.zone())
        val p = prompts.fill(
            PromptNames.PLACE_INTRO,
            mapOf("title" to c.title, "summary" to c.summary, "speaker" to speakerName(c), "date" to whenText, "place" to c.placeName, "today" to clock.dateTime().toLocalDate().toString()),
        )
        val text = runCatching { extractJsonObject(llm.completeJson(p.system, p.user))?.optString("text") }.getOrNull()?.trim()
        return text?.takeIf { it.isNotEmpty() && it.length <= 60 } ?: "여기, $whenText 의 추억이 있어요. 들어볼까요?"
    }

    private suspend fun speakerName(c: MemoryClip): String = people.get(c.personId)?.name ?: "가족"
}

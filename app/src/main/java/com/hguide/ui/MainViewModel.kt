package com.hguide.ui

import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hguide.AppContainer
import com.hguide.audio.Wav
import com.hguide.core.GeoPoint
import com.hguide.core.PlaceType
import com.hguide.core.SpeechPriority
import com.hguide.core.TripContext
import com.hguide.data.MemoryClip
import com.hguide.data.Person
import com.hguide.data.Place
import com.hguide.core.PersonRole
import com.hguide.voice.SpeechRequest
import kotlinx.coroutines.Dispatchers
import com.hguide.core.DrivingEventType
import com.hguide.core.Geo
import com.hguide.navi.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainViewModel(private val c: AppContainer) : ViewModel() {
    private val trip get() = c.tripController

    val parked get() = trip.parked
    val stationary get() = trip.stationary
    val tripId get() = trip.currentTripId
    val tripContext get() = trip.context
    val recap get() = trip.lastRecap
    val familyState get() = c.family.state
    val consentPrompt get() = c.family.prompt
    val familyAsking get() = c.family.asking
    val aiVoice get() = c.arbiter.aiVoice
    val events get() = c.events.lines
    val lessonActive get() = c.lesson.active
    val demo get() = c.demo
    val speaking get() = c.arbiter.speaking
    val destination get() = c.navi.destination
    fun now() = c.clock.dateTime()

    // ---- demo autoplay (fake-gateway builds) ----
    private val _autoplay = MutableStateFlow(0)
    /** Bumped to start the ~30 s scripted demo (see DemoAutoplay.kt). */
    val autoplayRequests: StateFlow<Int> = _autoplay.asStateFlow()
    fun startAutoplay() { if (c.demo != null) _autoplay.value++ }
    private val _scriptedPick = MutableStateFlow<String?>(null)
    /** Lets the autoplay script move the selection on the voice screen like a tap would. */
    val scriptedPick: StateFlow<String?> = _scriptedPick.asStateFlow()
    fun scriptPick(personId: String?) { _scriptedPick.value = personId }
    val fakeStt get() = c.fakeStt
    private val _greetVoice = MutableStateFlow(c.settings.greetingVoicePersonId)
    /** Whose voice greets and coaches (null = greetings off). */
    val greetVoice: StateFlow<String?> = _greetVoice.asStateFlow()

    val people: StateFlow<List<Person>> = c.db.personDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val clips: StateFlow<List<MemoryClip>> = c.db.memoryClipDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val places: StateFlow<List<Place>> = c.db.placeDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings get() = c.settings

    init {
        // The demo seeder may set the voice after this ViewModel exists; re-read whenever the family changes.
        viewModelScope.launch { people.collect { _greetVoice.value = c.settings.greetingVoicePersonId } }
    }

    // ---- family mode ----
    fun consent(personId: String, yes: Boolean) = c.family.respond(personId, yes)
    fun pauseRecording() = c.family.pause()
    fun resumeRecording() = c.family.resume()
    fun stopRecording() = c.family.stop()
    fun endTrip() = trip.endTripNow()
    fun lesson(on: Boolean) { if (on) c.lesson.start() else c.lesson.stop() }

    /** Re-asks everyone during a trip (e.g. after "다음에"). Recording still needs every rider's yes. */
    fun askFamily() = viewModelScope.launch {
        val id = trip.currentTripId.value ?: return@launch
        c.family.beginAsking(id, c.db.personDao().all())
    }
    fun consentAll(ids: List<String>) = ids.forEach { c.family.respond(it, true) }

    /** Driving events of the current (or last) trip, for the practice screen. */
    suspend fun lastTripEvents(): Map<DrivingEventType, Int> {
        val id = trip.currentTripId.value ?: c.db.tripDao().latest()?.id ?: return emptyMap()
        return c.db.drivingEventDao().forTrip(id).groupingBy { it.type }.eachCount()
    }

    fun distanceKm(d: Destination): Double? = trip.lastFix?.let { Geo.distanceM(it.point, d.point) / 1000.0 }

    // ---- playlist ----
    fun play(clip: MemoryClip) = viewModelScope.launch {
        c.arbiter.play(SpeechRequest(SpeechPriority.MEMORY, file = File(clip.audioPath), caption = clip.title))
    }
    fun stopPlayback() = c.arbiter.stopAll()
    fun delete(clip: MemoryClip) = viewModelScope.launch {
        File(clip.audioPath).delete()
        c.db.memoryClipDao().delete(clip)
        resync()
    }
    fun toggleProximity(clip: MemoryClip) = viewModelScope.launch {
        c.db.memoryClipDao().setProximity(clip.id, !clip.proximityEnabled)
        resync()
    }
    private suspend fun resync() = c.replay.sync(trip.lastFix?.point ?: GeoPoint(0.0, 0.0))

    // ---- places ----
    fun savePlaceHere(type: PlaceType): Boolean {
        val f = trip.lastFix ?: return false
        viewModelScope.launch { c.db.placeDao().upsert(Place(type.name, type, f.lat, f.lng, 300)) }
        return true
    }

    /** Address search. Geocoder may be missing on AAOS builds; returns false then. */
    suspend fun savePlaceByAddress(type: PlaceType, address: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching false
            @Suppress("DEPRECATION")
            val hit = Geocoder(c.appContext, Locale.KOREA).getFromLocationName(address, 1)?.firstOrNull() ?: return@runCatching false
            c.db.placeDao().upsert(Place(type.name, type, hit.latitude, hit.longitude, 300))
            true
        }.getOrDefault(false)
    }

    // ---- settings ----
    fun setGreetingVoice(personId: String?) { c.settings.greetingVoicePersonId = personId; _greetVoice.value = personId }
    fun previewVoice(personId: String) = viewModelScope.launch {
        c.voiceSpeaker.speakAs(personId, "오늘도 안전하게 운전하자.", SpeechPriority.GREETING)
    }
    fun setHours(mStart: Int, mEnd: Int, eStart: Int, eEnd: Int) {
        c.settings.morningStartHour = mStart; c.settings.morningEndHour = mEnd
        c.settings.eveningStartHour = eStart; c.settings.eveningEndHour = eEnd
    }
    fun addPerson(name: String, role: PersonRole) = viewModelScope.launch {
        c.db.personDao().upsert(Person(java.util.UUID.randomUUID().toString(), name.trim(), role, false, c.clock.nowMs()))
    }

    // ---- voice ----
    /** Records the three enrollment sentences (called while parked; UI shows each sentence). */
    fun enroll(personId: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching {
            val dir = File(c.baseDir, "voice_tmp").apply { mkdirs() }
            val src = c.newAudioSource()
            val files = try {
                c.enrollment.sentences.mapIndexed { i, _ -> File(dir, "${personId}_$i.wav").also { Wav.write(it, src.read(4_000)) } }
            } finally { src.close() }
            c.enrollment.enroll(personId, files)
            dir.deleteRecursively()
        }.isSuccess
        onDone(ok)
    }
    fun approveClone(personId: String, onDone: (Boolean) -> Unit) = viewModelScope.launch { onDone(c.enrollment.approveClone(personId).isSuccess) }
    fun revokeClone(personId: String) = viewModelScope.launch { c.enrollment.revokeClone(personId) }
    fun enrolled(personId: String, cb: (Boolean) -> Unit) = viewModelScope.launch { cb(c.db.voiceProfileDao().get(personId)?.revokedAt == null && c.db.voiceProfileDao().get(personId) != null) }

    /** "직접 남기기": the parent records their own greeting; it plays instead of any generated one. */
    fun recordCustomGreeting(personId: String, ctx: TripContext, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching {
            val src = c.newAudioSource()
            val pcm = try { src.read(5_000) } finally { src.close() }
            val f = File(c.baseDir, "greetings/${personId}_${ctx.name}.wav").also { Wav.write(it, pcm) }
            c.settings.setCustomGreetingPath(personId, ctx, f.path)
        }.isSuccess
        onDone(ok)
    }
    fun clearCustomGreeting(personId: String, ctx: TripContext) {
        c.settings.customGreetingPath(personId, ctx)?.let { File(it).delete() }
        c.settings.setCustomGreetingPath(personId, ctx, null)
    }

    // ---- privacy ----
    fun deleteAllRecordings() = viewModelScope.launch { c.privacy.deleteAllRecordings(); resync() }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(c) as T
    }
}

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
import kotlinx.coroutines.flow.SharingStarted
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
    val aiVoice get() = c.arbiter.aiVoice
    val events get() = c.events.lines
    val lessonActive get() = c.lesson.active
    val demo get() = c.demo

    val people: StateFlow<List<Person>> = c.db.personDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val clips: StateFlow<List<MemoryClip>> = c.db.memoryClipDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val places: StateFlow<List<Place>> = c.db.placeDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings get() = c.settings

    // ---- family mode ----
    fun consent(personId: String, yes: Boolean) = c.family.respond(personId, yes)
    fun pauseRecording() = c.family.pause()
    fun resumeRecording() = c.family.resume()
    fun stopRecording() = c.family.stop()
    fun endTrip() = trip.endTripNow()
    fun lesson(on: Boolean) { if (on) c.lesson.start() else c.lesson.stop() }

    // ---- playlist ----
    fun play(clip: MemoryClip) = viewModelScope.launch {
        c.arbiter.play(SpeechRequest(SpeechPriority.MEMORY, file = File(clip.audioPath)))
    }
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
    fun setGreetingVoice(personId: String?) { c.settings.greetingVoicePersonId = personId }
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

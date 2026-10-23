package com.hguide

import com.hguide.core.DrivingEventType
import com.hguide.core.PlaceType
import com.hguide.core.TripContext
import com.hguide.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MemPersonDao : PersonDao {
    val rows = MutableStateFlow<List<Person>>(emptyList())
    override suspend fun upsert(p: Person) { rows.value = rows.value.filter { it.id != p.id } + p }
    override suspend fun get(id: String) = rows.value.firstOrNull { it.id == id }
    override suspend fun all() = rows.value.sortedBy { it.createdAt }
    override fun observeAll(): Flow<List<Person>> = rows
    override suspend fun deleteAll() { rows.value = emptyList() }
}

class MemVoiceProfileDao : VoiceProfileDao {
    val rows = mutableMapOf<String, VoiceProfile>()
    override suspend fun upsert(p: VoiceProfile) { rows[p.personId] = p }
    override suspend fun get(personId: String) = rows[personId]
    override suspend fun all() = rows.values.toList()
    override suspend fun delete(personId: String) { rows.remove(personId) }
    override suspend fun deleteAll() = rows.clear()
}

class MemTripDao : TripDao {
    val rows = mutableListOf<Trip>()
    private var next = 1L
    override suspend fun insert(t: Trip): Long { val id = next++; rows += t.copy(id = id); return id }
    override suspend fun update(t: Trip) { rows.replaceAll { if (it.id == t.id) t else it } }
    override suspend fun get(id: Long) = rows.firstOrNull { it.id == id }
    override fun observeRecent(limit: Int): Flow<List<Trip>> = MutableStateFlow(rows.takeLast(limit))
    override suspend fun latest() = rows.lastOrNull()
    override suspend fun deleteAll() = rows.clear()
}

class MemAudioChunkDao : AudioChunkDao {
    val rows = mutableListOf<AudioChunk>()
    private var next = 1L
    override suspend fun insert(c: AudioChunk): Long { val id = next++; rows += c.copy(id = id); return id }
    override suspend fun forTrip(tripId: Long) = rows.filter { it.tripId == tripId }.sortedBy { it.startMs }
    override suspend fun createdBefore(ts: Long) = rows.filter { it.createdAt < ts }
    override suspend fun delete(id: Long) { rows.removeAll { it.id == id } }
    override suspend fun all() = rows.toList()
    override suspend fun deleteAll() = rows.clear()
}

class MemUtteranceDao : UtteranceDao {
    val rows = mutableListOf<Utterance>()
    private var next = 1L
    override suspend fun insertAll(list: List<Utterance>) { list.forEach { rows += it.copy(id = next++) } }
    override suspend fun forTrip(tripId: Long) = rows.filter { it.tripId == tripId }.sortedBy { it.startMs }
    override suspend fun recentBy(personId: String, limit: Int) = rows.filter { it.personId == personId }.sortedByDescending { it.id }.take(limit)
    override suspend fun deleteAll() = rows.clear()
}

class MemMemoryClipDao : MemoryClipDao {
    val rows = MutableStateFlow<List<MemoryClip>>(emptyList())
    private var next = 1L
    override suspend fun insert(c: MemoryClip): Long { val id = next++; rows.value = rows.value + c.copy(id = id); return id }
    override suspend fun update(c: MemoryClip) { rows.value = rows.value.map { if (it.id == c.id) c else it } }
    override suspend fun delete(c: MemoryClip) { rows.value = rows.value.filter { it.id != c.id } }
    override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }
    override fun observeAll(): Flow<List<MemoryClip>> = rows.map { it.sortedByDescending { c -> c.createdAt } }
    override suspend fun all() = rows.value
    override suspend fun countPlayedSince(ts: Long) = rows.value.count { (it.lastPlayedAt ?: -1L) >= ts }
    override suspend fun setProximity(id: Long, enabled: Boolean) { rows.value = rows.value.map { if (it.id == id) it.copy(proximityEnabled = enabled) else it } }
    override suspend fun setLastPlayed(id: Long, ts: Long) { rows.value = rows.value.map { if (it.id == id) it.copy(lastPlayedAt = ts) else it } }
    override suspend fun deleteAll() { rows.value = emptyList() }
}

class MemPlaceDao : PlaceDao {
    val rows = MutableStateFlow<List<Place>>(emptyList())
    override suspend fun upsert(p: Place) { rows.value = rows.value.filter { it.id != p.id } + p }
    override suspend fun get(type: PlaceType) = rows.value.firstOrNull { it.type == type }
    override fun observeAll(): Flow<List<Place>> = rows
    override suspend fun deleteAll() { rows.value = emptyList() }
}

class MemGreetingLogDao : GreetingLogDao {
    val rows = mutableListOf<GreetingLog>()
    private var next = 1L
    override suspend fun insert(g: GreetingLog): Long { val id = next++; rows += g.copy(id = id); return id }
    override suspend fun forDateAndContext(date: String, context: TripContext) = rows.firstOrNull { it.date == date && it.context == context }
    override suspend fun since(since: Long) = rows.filter { it.playedAt >= since }.sortedByDescending { it.playedAt }
    override suspend fun deleteAll() = rows.clear()
}

class MemDrivingEventDao : DrivingEventDao {
    val rows = mutableListOf<DrivingEvent>()
    private var next = 1L
    override suspend fun insert(e: DrivingEvent): Long { val id = next++; rows += e.copy(id = id); return id }
    override suspend fun countSince(tripId: Long, type: DrivingEventType, since: Long) = rows.count { it.tripId == tripId && it.type == type && it.ts >= since }
    override suspend fun forTrip(tripId: Long) = rows.filter { it.tripId == tripId }
    override suspend fun deleteAll() = rows.clear()
}

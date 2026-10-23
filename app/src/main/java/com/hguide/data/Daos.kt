package com.hguide.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hguide.core.DrivingEventType
import com.hguide.core.PlaceType
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: Person)
    @Query("SELECT * FROM Person WHERE id = :id") suspend fun get(id: String): Person?
    @Query("SELECT * FROM Person ORDER BY createdAt") suspend fun all(): List<Person>
    @Query("SELECT * FROM Person ORDER BY createdAt") fun observeAll(): Flow<List<Person>>
    @Query("DELETE FROM Person") suspend fun deleteAll()
}

@Dao
interface VoiceProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: VoiceProfile)
    @Query("SELECT * FROM VoiceProfile WHERE personId = :personId") suspend fun get(personId: String): VoiceProfile?
    @Query("SELECT * FROM VoiceProfile") suspend fun all(): List<VoiceProfile>
    @Query("DELETE FROM VoiceProfile WHERE personId = :personId") suspend fun delete(personId: String)
    @Query("DELETE FROM VoiceProfile") suspend fun deleteAll()
}

@Dao
interface TripDao {
    @Insert suspend fun insert(t: Trip): Long
    @Update suspend fun update(t: Trip)
    @Query("SELECT * FROM Trip WHERE id = :id") suspend fun get(id: Long): Trip?
    @Query("SELECT * FROM Trip ORDER BY startedAt DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<Trip>>
    @Query("SELECT * FROM Trip ORDER BY startedAt DESC LIMIT 1") suspend fun latest(): Trip?
    @Query("DELETE FROM Trip") suspend fun deleteAll()
}

@Dao
interface AudioChunkDao {
    @Insert suspend fun insert(c: AudioChunk): Long
    @Query("SELECT * FROM AudioChunk WHERE tripId = :tripId ORDER BY startMs") suspend fun forTrip(tripId: Long): List<AudioChunk>
    @Query("SELECT * FROM AudioChunk WHERE createdAt < :ts") suspend fun createdBefore(ts: Long): List<AudioChunk>
    @Query("DELETE FROM AudioChunk WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM AudioChunk") suspend fun all(): List<AudioChunk>
    @Query("DELETE FROM AudioChunk") suspend fun deleteAll()
}

@Dao
interface UtteranceDao {
    @Insert suspend fun insertAll(list: List<Utterance>)
    @Query("SELECT * FROM Utterance WHERE tripId = :tripId ORDER BY startMs") suspend fun forTrip(tripId: Long): List<Utterance>
    @Query("SELECT * FROM Utterance WHERE personId = :personId ORDER BY id DESC LIMIT :limit") suspend fun recentBy(personId: String, limit: Int): List<Utterance>
    @Query("DELETE FROM Utterance") suspend fun deleteAll()
}

@Dao
interface MemoryClipDao {
    @Insert suspend fun insert(c: MemoryClip): Long
    @Update suspend fun update(c: MemoryClip)
    @Delete suspend fun delete(c: MemoryClip)
    @Query("SELECT * FROM MemoryClip WHERE id = :id") suspend fun get(id: Long): MemoryClip?
    @Query("SELECT * FROM MemoryClip ORDER BY createdAt DESC") fun observeAll(): Flow<List<MemoryClip>>
    @Query("SELECT * FROM MemoryClip") suspend fun all(): List<MemoryClip>
    @Query("SELECT COUNT(*) FROM MemoryClip WHERE lastPlayedAt IS NOT NULL AND lastPlayedAt >= :ts") suspend fun countPlayedSince(ts: Long): Int
    @Query("UPDATE MemoryClip SET proximityEnabled = :enabled WHERE id = :id") suspend fun setProximity(id: Long, enabled: Boolean)
    @Query("UPDATE MemoryClip SET lastPlayedAt = :ts WHERE id = :id") suspend fun setLastPlayed(id: Long, ts: Long)
    @Query("DELETE FROM MemoryClip") suspend fun deleteAll()
}

@Dao
interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: Place)
    @Query("SELECT * FROM Place WHERE type = :type LIMIT 1") suspend fun get(type: PlaceType): Place?
    @Query("SELECT * FROM Place") fun observeAll(): Flow<List<Place>>
    @Query("DELETE FROM Place") suspend fun deleteAll()
}

@Dao
interface GreetingLogDao {
    @Insert suspend fun insert(g: GreetingLog): Long
    @Query("SELECT * FROM GreetingLog WHERE date = :date AND context = :context LIMIT 1") suspend fun forDateAndContext(date: String, context: com.hguide.core.TripContext): GreetingLog?
    @Query("SELECT * FROM GreetingLog WHERE playedAt >= :since ORDER BY playedAt DESC") suspend fun since(since: Long): List<GreetingLog>
    @Query("DELETE FROM GreetingLog") suspend fun deleteAll()
}

@Dao
interface DrivingEventDao {
    @Insert suspend fun insert(e: DrivingEvent): Long
    @Query("SELECT COUNT(*) FROM DrivingEvent WHERE tripId = :tripId AND type = :type AND ts >= :since") suspend fun countSince(tripId: Long, type: DrivingEventType, since: Long): Int
    @Query("SELECT * FROM DrivingEvent WHERE tripId = :tripId ORDER BY ts") suspend fun forTrip(tripId: Long): List<DrivingEvent>
    @Query("DELETE FROM DrivingEvent") suspend fun deleteAll()
}

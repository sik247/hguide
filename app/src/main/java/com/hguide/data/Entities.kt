package com.hguide.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hguide.core.DrivingEventType
import com.hguide.core.PersonRole
import com.hguide.core.PlaceType
import com.hguide.core.TripContext

@Entity
data class Person(
    @PrimaryKey val id: String,
    val name: String,
    val role: PersonRole,
    val voiceCloneApproved: Boolean = false,
    val createdAt: Long,
)

@Entity
data class VoiceProfile(
    @PrimaryKey val personId: String,
    val embeddingPath: String,
    val sampleCount: Int,
    val approvedAt: Long,
    val revokedAt: Long? = null,
)

@Entity
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val familyMode: Boolean = false,
    val consentPersonIds: List<String> = emptyList(),
    val context: TripContext = TripContext.OTHER,
)

@Entity(indices = [Index("tripId")])
data class AudioChunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val path: String,
    val startMs: Long,
    val endMs: Long,
    val lat: Double,
    val lng: Double,
    /** Driving state tag (spec F2): "PARKED" | "STATIONARY" | "MOVING". */
    val drivingState: String = "MOVING",
    val createdAt: Long = 0,
)

@Entity(indices = [Index("tripId")])
data class Utterance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val personId: String?,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val lat: Double,
    val lng: Double,
)

@Entity
data class MemoryClip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val personId: String,
    val title: String,
    val summary: String,
    val emotionTag: String,
    val audioPath: String,
    val startMs: Long,
    val endMs: Long,
    val lat: Double,
    val lng: Double,
    val placeName: String,
    val createdAt: Long,
    val proximityEnabled: Boolean = true,
    val lastPlayedAt: Long? = null,
)

@Entity
data class Place(
    @PrimaryKey val id: String, // "HOME" | "WORK"
    val type: PlaceType,
    val lat: Double,
    val lng: Double,
    val radiusM: Int = 300,
)

@Entity
data class GreetingLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val context: TripContext,
    val personId: String,
    val text: String,
    val audioPath: String?,
    val playedAt: Long,
)

@Entity(indices = [Index("tripId")])
data class DrivingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val type: DrivingEventType,
    val ts: Long,
    val lat: Double,
    val lng: Double,
)

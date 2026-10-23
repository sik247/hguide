package com.hguide.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.hguide.core.DrivingEventType
import com.hguide.core.PersonRole
import com.hguide.core.PlaceType
import com.hguide.core.TripContext

class Converters {
    @TypeConverter fun roleToString(v: PersonRole) = v.name
    @TypeConverter fun stringToRole(v: String) = PersonRole.valueOf(v)
    @TypeConverter fun ctxToString(v: TripContext) = v.name
    @TypeConverter fun stringToCtx(v: String) = TripContext.valueOf(v)
    @TypeConverter fun evToString(v: DrivingEventType) = v.name
    @TypeConverter fun stringToEv(v: String) = DrivingEventType.valueOf(v)
    @TypeConverter fun placeToString(v: PlaceType) = v.name
    @TypeConverter fun stringToPlace(v: String) = PlaceType.valueOf(v)
    @TypeConverter fun listToString(v: List<String>) = v.joinToString("\u001F")
    @TypeConverter fun stringToList(v: String) = if (v.isEmpty()) emptyList() else v.split("\u001F")
}

@Database(
    entities = [
        Person::class, VoiceProfile::class, Trip::class, AudioChunk::class, Utterance::class,
        MemoryClip::class, Place::class, GreetingLog::class, DrivingEvent::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class HguideDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun tripDao(): TripDao
    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun utteranceDao(): UtteranceDao
    abstract fun memoryClipDao(): MemoryClipDao
    abstract fun placeDao(): PlaceDao
    abstract fun greetingLogDao(): GreetingLogDao
    abstract fun drivingEventDao(): DrivingEventDao

    companion object {
        fun build(context: Context): HguideDatabase =
            Room.databaseBuilder(context, HguideDatabase::class.java, "hguide.db").build()
    }
}

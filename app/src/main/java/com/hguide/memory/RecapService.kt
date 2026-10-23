package com.hguide.memory

import com.hguide.core.Clock
import com.hguide.core.GeoPoint
import com.hguide.core.log
import com.hguide.data.MemoryClip
import com.hguide.data.MemoryClipDao
import com.hguide.data.PersonDao
import com.hguide.data.TripDao
import com.hguide.data.UtteranceDao
import java.io.File

data class RecapResult(val tripId: Long, val clips: List<MemoryClip>)

/** End of trip: transcript → highlights → audio clips saved to the playlist. */
class RecapService(
    private val trips: TripDao,
    private val utterances: UtteranceDao,
    private val people: PersonDao,
    private val extractor: HighlightExtractor,
    private val cutter: ClipCutter,
    private val clips: MemoryClipDao,
    private val placeNamer: PlaceNamer,
    private val clock: Clock,
    private val baseDir: File,
) {
    suspend fun finishTrip(tripId: Long, destinationName: String? = null): RecapResult {
        val trip = trips.get(tripId) ?: return RecapResult(tripId, emptyList())
        if (trip.endedAt == null) trips.update(trip.copy(endedAt = clock.nowMs()))
        val rows = utterances.forTrip(tripId)
        if (rows.isEmpty()) return RecapResult(tripId, emptyList())

        val names = people.all().associate { it.id to it.name }
        val first = rows.first(); val last = rows.last()
        val startPlace = placeNamer.name(GeoPoint(first.lat, first.lng))
        val endPlace = placeNamer.name(GeoPoint(last.lat, last.lng), destinationName)
        val date = java.time.Instant.ofEpochMilli(trip.startedAt).atZone(clock.zone()).toLocalDate().toString()

        val saved = mutableListOf<MemoryClip>()
        for ((i, h) in extractor.extract(date, startPlace, endPlace, rows, names).withIndex()) {
            val out = File(baseDir, "clips/trip${tripId}_$i.wav")
            if (!cutter.cut(tripId, h.startMs, h.endMs, out)) { log("recap", "no audio for clip $i"); continue }
            val clip = MemoryClip(
                tripId = tripId, personId = h.personId, title = h.title, summary = h.summary, emotionTag = h.emotion,
                audioPath = out.path, startMs = h.startMs, endMs = h.endMs, lat = h.lat, lng = h.lng,
                placeName = placeNamer.name(GeoPoint(h.lat, h.lng), destinationName), createdAt = clock.nowMs(),
            )
            saved += clip.copy(id = clips.insert(clip))
        }
        return RecapResult(tripId, saved)
    }
}

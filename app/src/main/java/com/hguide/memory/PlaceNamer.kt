package com.hguide.memory

import com.hguide.core.Geo
import com.hguide.core.GeoPoint
import com.hguide.core.PlaceType
import com.hguide.data.PlaceDao

/** Human-readable place label without a geocoder: home/work if close, else the navi destination name, else coordinates. */
class PlaceNamer(private val places: PlaceDao) {
    suspend fun name(point: GeoPoint, destinationName: String? = null): String {
        for (t in listOf(PlaceType.HOME, PlaceType.WORK)) {
            val p = places.get(t) ?: continue
            if (Geo.distanceM(point, GeoPoint(p.lat, p.lng)) <= maxOf(p.radiusM, 500)) return if (t == PlaceType.HOME) "집 근처" else "회사 근처"
        }
        return destinationName ?: "%.3f, %.3f".format(point.lat, point.lng)
    }
}

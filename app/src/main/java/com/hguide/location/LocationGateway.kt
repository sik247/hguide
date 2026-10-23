package com.hguide.location

import com.hguide.core.Fix
import com.hguide.core.Geo
import com.hguide.core.GeoPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ProximityEvent(val alertId: String, val enter: Boolean)

interface LocationGateway {
    val supported: Boolean
    val fixes: SharedFlow<Fix>
    val proximityEvents: SharedFlow<ProximityEvent>
    fun start()
    fun stop()
    /** Registers a proximity alert (ENTER). Returns the service-assigned alert id, or null on failure. */
    suspend fun addProximityAlert(center: GeoPoint, radiusM: Float): String?
    fun removeProximityAlert(alertId: String)
}

/** Emits proximity ENTER/EXIT itself when a pushed fix crosses a registered circle, like the real service would. */
class FakeLocationGateway : LocationGateway {
    override var supported = true
    private val _fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 64)
    private val _prox = MutableSharedFlow<ProximityEvent>(extraBufferCapacity = 64)
    override val fixes = _fixes.asSharedFlow()
    override val proximityEvents = _prox.asSharedFlow()

    private data class Alert(val id: String, val center: GeoPoint, val radius: Float, var inside: Boolean = false)
    private val alerts = linkedMapOf<String, Alert>()
    private var next = 1
    var maxAlerts = Int.MAX_VALUE
    var lastFix: Fix? = null
        private set

    val activeAlertCount get() = alerts.size

    override fun start() {}
    override fun stop() {}

    fun emit(fix: Fix) {
        lastFix = fix
        _fixes.tryEmit(fix)
        for (a in alerts.values) {
            val inside = Geo.distanceM(fix.point, a.center) <= a.radius
            if (inside != a.inside) {
                a.inside = inside
                _prox.tryEmit(ProximityEvent(a.id, inside))
            }
        }
    }

    override suspend fun addProximityAlert(center: GeoPoint, radiusM: Float): String? {
        if (alerts.size >= maxAlerts) return null
        val id = "alert-${next++}"
        alerts[id] = Alert(id, center, radiusM, inside = lastFix?.let { Geo.distanceM(it.point, center) <= radiusM } ?: false)
        return id
    }

    override fun removeProximityAlert(alertId: String) { alerts.remove(alertId) }
}

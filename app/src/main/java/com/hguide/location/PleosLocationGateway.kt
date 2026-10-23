package com.hguide.location

import android.content.Context
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import com.hguide.core.Fix
import com.hguide.core.GeoPoint
import com.hguide.core.log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** FusedLocation SDK 1.1.3. Signatures verified against the published AAR. */
class PleosLocationGateway(context: Context) : LocationGateway {
    private val fl = FusedLocation(context)
    private val _fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 64)
    private val _prox = MutableSharedFlow<ProximityEvent>(extraBufferCapacity = 64)
    override val fixes: SharedFlow<Fix> = _fixes.asSharedFlow()
    override val proximityEvents: SharedFlow<ProximityEvent> = _prox.asSharedFlow()
    override var supported = false
        private set

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) return
            _fixes.tryEmit(Fix(location.latitude, location.longitude, location.heading, location.timestampMillis))
        }
        override fun onError(e: Exception) = log("location", "error: ${e.javaClass.simpleName}")
    }

    override fun start() {
        fl.initialize()
        supported = fl.isSupported()
        if (!supported) { log("location", "FusedLocation not supported"); return }
        fl.registerFusedLocationCallback(listener)
    }

    override fun stop() {
        runCatching { fl.unregisterFusedLocationCallback(listener) }
        runCatching { fl.release() }
    }

    override suspend fun addProximityAlert(center: GeoPoint, radiusM: Float): String? {
        if (!supported) return null
        return suspendCancellableCoroutine { cont ->
            // TODO(verify-api): maximum number of simultaneous proximity alerts is undocumented (callers cap it).
            val req = ProximityAlertRequest(center.lat, center.lng, radiusM, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT))
            fl.addProximityAlert(req, object : ProximityAlertListener {
                override fun onRegistered(alertId: String) { if (cont.isActive) cont.resume(alertId) }
                override fun onTransition(event: ProximityAlertEvent) {
                    _prox.tryEmit(ProximityEvent(event.alertId, event.transition == ProximityTransition.ENTER))
                }
                override fun onError(e: Exception) {
                    log("location", "proximity error: ${e.javaClass.simpleName}")
                    if (cont.isActive) cont.resume(null)
                }
            })
        }
    }

    override fun removeProximityAlert(alertId: String) { runCatching { fl.removeProximityAlert(alertId) } }
}

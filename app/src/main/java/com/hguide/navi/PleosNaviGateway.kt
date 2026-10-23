package com.hguide.navi

import android.content.Context
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.hguide.core.GeoPoint
import com.hguide.core.log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** NaviHelper 3.0.5. Read-only use: destination + navigation status. */
class PleosNaviGateway(context: Context) : NaviGateway {
    private val navi = NaviHelper(context)
    private val _dest = MutableStateFlow<Destination?>(null)
    private val _status = MutableStateFlow(NaviStatus.INACTIVE)
    override val destination: StateFlow<Destination?> = _dest.asStateFlow()
    override val status: StateFlow<NaviStatus> = _status.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(info: DestinationInfo) {
            _dest.value = Destination(GeoPoint(info.latitude, info.longitude), info.name)
        }
        override fun onDestinationChanged() { navi.getDestinationInfo() }
        override fun onRouteCancelled() { _dest.value = null }
        override fun onDestinationArrived(info: ai.pleos.playground.navi.data.DestinationArrivedInfo) { _dest.value = null }
        override fun onNavigationStatus(status: NavigationStatus) {
            _status.value = when (status) {
                NavigationStatus.ROUTE_DRIVE -> NaviStatus.ROUTE_DRIVE
                NavigationStatus.FREE_DRIVE -> NaviStatus.FREE_DRIVE
                else -> NaviStatus.INACTIVE
            }
            if (status == NavigationStatus.ROUTE_DRIVE) navi.getDestinationInfo()
        }
        override fun onError(code: ai.pleos.playground.navi.constants.NaviErrorCode) = log("navi", "error: $code")
    }

    override fun start() {
        // TODO(verify-api): NaviHelper manifest permission is not documented on the public pages.
        navi.initialize({ navi.addListener(listener); navi.getDestinationInfo() }, { t -> log("navi", "init failed: ${t.javaClass.simpleName}") })
    }

    override fun stop() {
        runCatching { navi.removeListener(listener) }
        runCatching { navi.release() }
    }
}

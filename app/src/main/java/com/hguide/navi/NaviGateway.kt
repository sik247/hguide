package com.hguide.navi

import com.hguide.core.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NaviStatus { INACTIVE, FREE_DRIVE, ROUTE_DRIVE }
data class Destination(val point: GeoPoint, val name: String?)

interface NaviGateway {
    val destination: StateFlow<Destination?>
    val status: StateFlow<NaviStatus>
    fun start()
    fun stop()
}

class FakeNaviGateway : NaviGateway {
    private val _dest = MutableStateFlow<Destination?>(null)
    private val _status = MutableStateFlow(NaviStatus.INACTIVE)
    override val destination = _dest.asStateFlow()
    override val status = _status.asStateFlow()
    override fun start() {}
    override fun stop() {}
    fun setDestination(d: Destination?) {
        _dest.value = d
        _status.value = if (d == null) NaviStatus.FREE_DRIVE else NaviStatus.ROUTE_DRIVE
    }
}

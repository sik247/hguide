package com.hguide.vehicle

import com.hguide.core.TripGear
import com.hguide.core.TurnSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VehicleSnapshot(
    val gear: TripGear = TripGear.UNKNOWN,
    val turnSignal: TurnSignal = TurnSignal.OFF,
    val brakePedal: Boolean = false,
) {
    /** PARK stands in for the parking brake. TODO(verify-api): parking-brake status, if Pleos exposes one. */
    val parked get() = gear == TripGear.PARK
}

/** Which Vehicle domains the car actually supports (checkXXXCapabilities). Unsupported => feature disabled only. */
data class VehicleCaps(
    val gear: Boolean = false,
    val turnSignal: Boolean = false,
    val brakePedal: Boolean = false,
)

interface VehicleGateway {
    val state: StateFlow<VehicleSnapshot>
    val capabilities: StateFlow<VehicleCaps>
    fun start()
    fun stop()
}

class FakeVehicleGateway : VehicleGateway {
    private val _state = MutableStateFlow(VehicleSnapshot(gear = TripGear.PARK))
    private val _caps = MutableStateFlow(VehicleCaps(gear = true, turnSignal = true, brakePedal = true))
    override val state = _state.asStateFlow()
    override val capabilities = _caps.asStateFlow()
    override fun start() {}
    override fun stop() {}

    fun setGear(g: TripGear) { _state.value = _state.value.copy(gear = g) }
    fun setTurnSignal(s: TurnSignal) { _state.value = _state.value.copy(turnSignal = s) }
    fun setBrakePedal(b: Boolean) { _state.value = _state.value.copy(brakePedal = b) }
    fun setCaps(c: VehicleCaps) { _caps.value = c }
}

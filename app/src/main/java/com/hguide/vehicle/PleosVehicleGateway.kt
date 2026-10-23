package com.hguide.vehicle

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import com.hguide.core.TripGear
import com.hguide.core.TurnSignal
import com.hguide.core.log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Vehicle SDK 2.3.2 (ai.pleos.playground:Vehicle). Signatures verified against the published AAR.
 * Read-only: no CONTROL permission or set* call is used.
 */
class PleosVehicleGateway(private val context: Context) : VehicleGateway {
    private val _state = MutableStateFlow(VehicleSnapshot())
    private val _caps = MutableStateFlow(VehicleCaps())
    override val state: StateFlow<VehicleSnapshot> = _state.asStateFlow()
    override val capabilities: StateFlow<VehicleCaps> = _caps.asStateFlow()

    private var vehicle: Vehicle? = null

    private fun readable(t: AccessType?) = t == AccessType.READ || t == AccessType.READ_WRITE

    private val signalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
            _state.value = _state.value.copy(turnSignal = value.toApp())
        }
        override fun onFailed(e: Exception) = log("vehicle", "turnSignal failed: ${e.javaClass.simpleName}")
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(value: Boolean?) {
            _state.value = _state.value.copy(brakePedal = value == true)
        }
        override fun onFailed(e: Exception) = log("vehicle", "brake failed: ${e.javaClass.simpleName}")
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _state.value = _state.value.copy(gear = gear.toApp())
        }
        override fun onFailed(e: Exception) = log("vehicle", "gear failed: ${e.javaClass.simpleName}")
    }

    override fun start() {
        val v = Vehicle(context).also { it.initialize() }
        vehicle = v

        v.getTurnSignal().checkTurnSignalStateCapabilities(
            { cfg ->
                val ok = readable(cfg.accessType)
                _caps.value = _caps.value.copy(turnSignal = ok)
                if (ok) {
                    v.getTurnSignal().registerTurnSignalState(signalListener)
                    v.getTurnSignal().getCurrentTurnSignalState(
                        { s -> _state.value = _state.value.copy(turnSignal = s.toApp()) },
                        { e -> log("vehicle", "turnSignal get failed: ${e.javaClass.simpleName}") },
                    )
                }
            },
            { e -> log("vehicle", "turnSignal capability failed: ${e.javaClass.simpleName}") },
        )

        v.getBrake().checkBrakePedalStatusCapabilities(
            { cfg ->
                val ok = readable(cfg.accessType)
                _caps.value = _caps.value.copy(brakePedal = ok)
                if (ok) {
                    v.getBrake().registerBrakePedalStatus(brakeListener)
                    v.getBrake().getCurrentBrakePedalStatus(
                        { b -> _state.value = _state.value.copy(brakePedal = b == true) },
                        { e -> log("vehicle", "brake get failed: ${e.javaClass.simpleName}") },
                    )
                }
            },
            { e -> log("vehicle", "brake capability failed: ${e.javaClass.simpleName}") },
        )

        // TODO(verify-api): Gear has no checkXXXCapabilities in 2.3.2; support is inferred from the first successful read.
        v.getGear().registerGearSelection(gearListener)
        v.getGear().getCurrentGearSelectionState(
            { g ->
                _caps.value = _caps.value.copy(gear = true)
                _state.value = _state.value.copy(gear = g.toApp())
            },
            { e -> log("vehicle", "gear get failed: ${e.javaClass.simpleName}") },
        )
    }

    override fun stop() {
        val v = vehicle ?: return
        runCatching {
            v.getTurnSignal().unregisterTurnSignalState(signalListener)
            v.getBrake().unregisterBrakePedalStatus(brakeListener)
            v.getGear().unregisterGearSelection(gearListener)
        }
        runCatching { v.release() } // after callbacks have been unregistered (SDK warns against releasing mid-callback)
        vehicle = null
    }

    private fun TurnSignalValue?.toApp() = when (this) {
        TurnSignalValue.LEFT -> TurnSignal.LEFT
        TurnSignalValue.RIGHT -> TurnSignal.RIGHT
        else -> TurnSignal.OFF
    }

    private fun VehicleGear?.toApp() = when (this) {
        VehicleGear.GEAR_PARK -> TripGear.PARK
        VehicleGear.GEAR_REVERSE -> TripGear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> TripGear.NEUTRAL
        VehicleGear.GEAR_UNKNOWN, null -> TripGear.UNKNOWN
        else -> TripGear.DRIVE
    }
}

# Pleos SDK API notes

Public docs only describe entry points; **signatures below were read from the published artifacts**
(`https://nexus-playground.pleos.ai/repository/maven-releases/`, no access request needed) with `javap`, and the app
compiles against them. Items marked ⚠ are not confirmed.

```kotlin
// settings.gradle.kts → maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
implementation("ai.pleos.playground:Vehicle:2.3.2")
implementation("ai.pleos.playground:FusedLocation:1.1.3")
implementation("ai.pleos.playground:NaviHelper:3.0.5")
implementation("ai.pleos.playground:TextToSpeech:3.3.1")
implementation("ai.pleos.playground:SpeechToText:3.3.1")
```

## Vehicle (`ai.pleos.playground.vehicle`)
- `Vehicle(context)`, `initialize()`, `release()`; domain getters `getBrake() getTurnSignal() getGear() getLight() getHVAC() getDrivingMode() getOdometer() getCarInfo() getSafety() …`
- Async style: `onComplete` / `onFailed` lambdas (`Function1`), listeners extend `VehicleListener { onFailed(Exception) }`.
- **TurnSignal**: `checkTurnSignalStateCapabilities(onComplete:(CapabilityConfig)->Unit, onFailed)`, `getCurrentTurnSignalState(…)`, `registerTurnSignalState(TurnSignalStateListener)` / `unregister…`; `TurnSignalStateListener.onTurnSignalStateUpdated(TurnSignalValue?)`; `TurnSignalValue { OFF, RIGHT, LEFT }`.
- **Brake**: `checkBrakePedalStatusCapabilities`, `getCurrentBrakePedalStatus(Boolean?)`, `registerBrakePedalStatus(BrakePedalStatusListener)` — pedal only.
- **Gear**: `getCurrentGearSelectionState(VehicleGear)`, `registerGearSelection(GearSelectionListener.onSelected(VehicleGear?))`; `VehicleGear { GEAR_PARK, GEAR_REVERSE, GEAR_NEUTRAL, GEAR_DRIVE, GEAR_1..9, GEAR_UNKNOWN }`. No capability check.
- `CapabilityConfig.accessType : AccessType { NONE, READ, WRITE, READ_WRITE }`.
- Permissions (docs): Brake/DrivingMode `pleos.car.permission.CAR_DRIVE`, TurnSignal/Light `…CAR_LIGHTS`, HVAC `…CAR_CLIMATE`.

## FusedLocation (`ai.pleos.playground.location.fused`)
`FusedLocation(context)`, `initialize()`, `release()`, `isSupported()`, `getLastKnownLocation(listener)`, `registerFusedLocationCallback(FusedLocationListener{onChanged(LocationData?), onError})`, `addProximityAlert(ProximityAlertRequest(lat, lng, radiusM: Float, Set<ProximityTransition>), ProximityAlertListener{onRegistered(alertId), onTransition(ProximityAlertEvent), onError})`, `removeProximityAlert(alertId)`, `showLocationAccessBlockedPopup()`. `LocationData(lat, lng, altitude, heading, confidence, timestampMillis)` — **no speed**. Permission `pleos.car.permission.FUSED_LOCATION`.

## NaviHelper (`ai.pleos.playground.navi.helper`, data in `…navi.data`)
`NaviHelper(context)`, `initialize()` / `initialize(onSuccess, onFailure)`, `release()`, `addListener / removeListener(NaviHelperEventListener)`, `getDestinationInfo()`, `getRouteStateInfo()`, `getCurrentLocationInfo()`, `requestRoute(RouteInfo)` … Listener (default methods): `onDestinationInfo(DestinationInfo{latitude, longitude, name, …})`, `onDestinationChanged()`, `onNavigationStatus(NavigationStatus{INACTIVE, INITIALIZING, FREE_DRIVE, ROUTE_DRIVE})`, `onRouteCancelled()`, `onDestinationArrived(...)`, `onDrivingInfo`, `onError(NaviErrorCode)`. The app only reads.

## TextToSpeech / SpeechToText (`ai.pleos.playground.tts|stt`)
- TTS: `TextToSpeech(context, Mode{HYBRID,SERVER,ON_DEVICE})`, `initialize(OnInitializeListener)`, `addEventListener(EventListener{onStart,onReady,onDone,onError(ErrorType),onStop,onUpdatedRms})`, `speak(text, Boolean)`, `stop()`, `setLocale`, `setSpeakerType`, `setAudioAttributes`, `registerApp(...)` for server mode.
- STT: `SpeechToText(context, Mode)`, `initialize(OnInitializeListener)`, `addListener(ResultListener{onUpdated(text, isFinal), onUpdatedEpdData, onStartedRecognition, onEndedRecognition, onError, onReady, onUpdatedRms})`, `request()`, `stop()`, `sendAudio(ByteArray)`, `completeAudioSend()`, `setLocale`, `registerApp(...)`. Result data classes carry `Speaker(speakerId)` but `ResultListener` does not expose it → diarization stays a separate interface.

## Not confirmed / design consequences
1. ⚠ **Parking brake**: no API in Vehicle 2.3.2 → `Gear.GEAR_PARK` is used as "parked" (screen allowed only then).
2. ⚠ **Ignition / power state**: `IgnitionState` enum exists but no accessor → trip start = app start + first fix + gear leaves PARK.
3. ⚠ **Vehicle speed**: none in Vehicle or `LocationData` → speed/decel derived from consecutive fixes (`MotionTracker`).
4. ⚠ **Max simultaneous proximity alerts**: undocumented → app caps at 20 nearest clips and re-syncs every 2 km.
5. ⚠ `TextToSpeech.speak(String, Boolean)` — meaning of the Boolean; `false` assumed.
6. ⚠ `SpeechToText.request()` + `sendAudio()` semantics and buffer limits; whether mic can be shared with the recorder for the "기록 그만" voice command.
7. ⚠ `registerApp(...)` credentials for server STT/TTS (HYBRID falls back on-device).
8. ⚠ Manifest permission for NaviHelper, and for Gear (assumed `CAR_DRIVE`).
9. ⚠ Behaviour on the real Pleos Emulator + Virtual Console — not yet run.

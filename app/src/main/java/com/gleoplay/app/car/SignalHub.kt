package com.gleoplay.app.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import com.gleoplay.app.core.DrivingSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 각 신호를 어디서 받고 있는지(화면 하단 '신호 출처' 표시용) */
data class SignalSources(
    val gear: String = "없음",
    val uxRestriction: String = "없음",
    val speed: String = "없음",
    val brake: String = "없음",
    val turnSignal: String = "없음",
    val drivingTime: String = "없음",
    val telltale: String = "없음",
    val navi: String = "없음",
)

/** 시연 도구(T1)가 덮어쓰는 값. null 이면 실제 신호를 그대로 쓴다. */
data class DemoOverrides(
    val parked: Boolean? = null,
    val tbtDistanceM: Int? = null,
    val naviSpeaking: Boolean = false,
    val hardBrake: Boolean = false,
    val drivingMinutes: Int? = null,
    val driverAttention: Boolean = false,
    val minutesToArrival: Int? = null,
)

/**
 * 차량 신호를 모아 [DrivingSnapshot] 하나로 만든다.
 * 모든 신호는 '받을 수 있으면 쓰고, 못 받으면 비워 둔다'. 실패해도 앱은 계속 동작한다.
 */
class SignalHub(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val _snapshot = MutableStateFlow(DrivingSnapshot())
    val snapshot: StateFlow<DrivingSnapshot> = _snapshot.asStateFlow()
    private val _sources = MutableStateFlow(SignalSources())
    val sources: StateFlow<SignalSources> = _sources.asStateFlow()
    private val _arrived = MutableStateFlow(0)
    /** 내비 도착 이벤트 횟수(바뀌면 가족 카드 안내) */
    val arrived: StateFlow<Int> = _arrived.asStateFlow()

    var demo = DemoOverrides()
        set(value) { field = value; publish() }

    // 실제 신호 원값
    private var gear: VehicleGear? = null
    private var uxRestricted: Boolean? = null
    private var speedKmh: Float? = null
    private var brakePressed = false
    private var brakeAtMs = 0L
    private val speedHistory = ArrayDeque<Pair<Long, Float>>()
    private var turnSignalOn = false
    private var drivingMinutes: Int? = null
    private var attentionOn = false
    private var tbtDistance: Int? = null
    private var cautionAhead = false
    // 도착 남은 시간: TripInfo.eta 의 단위가 문서에 없어 MVP 에서는 시연 값만 쓴다
    private var minutesToArrival: Int? = null

    private var vehicle: Vehicle? = null
    private var navi: NaviHelper? = null
    private var car: Car? = null
    private val naviPoll = object : Runnable {
        override fun run() {
            runCatching { navi?.getTBTInfo() }
            main.postDelayed(this, 10_000)
        }
    }

    fun start() {
        startPleosVehicle()
        startAaos()
        startNavi()
        publish()
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        runCatching { vehicle?.release() }
        runCatching { navi?.release() }
        runCatching { car?.disconnect() }
    }

    private fun src(update: SignalSources.() -> SignalSources) { _sources.value = _sources.value.update() }

    // ───────────── Pleos Vehicle SDK ─────────────
    private fun startPleosVehicle() {
        val v = runCatching { Vehicle(context).also { it.initialize() } }
            .onFailure { Log.w(TAG, "Vehicle SDK 초기화 실패: $it") }.getOrNull() ?: return
        vehicle = v

        runCatching {
            v.getGear().registerGearSelection(object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) = post { this@SignalHub.gear = gear; src { copy(gear = "Vehicle SDK") } }
                override fun onFailed(e: Exception) { Log.w(TAG, "gear: $e") }
            })
        }.onFailure { Log.w(TAG, "gear 등록 실패: $it") }

        runCatching {
            v.getBrake().checkBrakePedalStatusCapabilities({ cfg ->
                if (cfg.accessType == AccessType.READ || cfg.accessType == AccessType.READ_WRITE) {
                    v.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
                        override fun onBrakePedalStatusUpdated(pressed: Boolean?) = post {
                            brakePressed = pressed == true
                            if (brakePressed) brakeAtMs = android.os.SystemClock.elapsedRealtime()
                            src { copy(brake = "Vehicle SDK") }
                        }
                        override fun onFailed(e: Exception) { Log.w(TAG, "brake: $e") }
                    })
                }
            }, { Log.w(TAG, "brake capability: $it") })
        }

        runCatching {
            v.getTurnSignal().checkTurnSignalStateCapabilities({ cfg ->
                if (cfg.accessType != AccessType.NONE) {
                    v.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
                        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = post {
                            turnSignalOn = value != null && value != TurnSignalValue.OFF; src { copy(turnSignal = "Vehicle SDK") }
                        }
                        override fun onFailed(e: Exception) { Log.w(TAG, "turn: $e") }
                    })
                }
            }, { Log.w(TAG, "turn capability: $it") })
        }

        runCatching {
            v.getOdometer().checkTripCurrentDrivingTimeCapabilities({ cfg ->
                if (cfg.accessType != AccessType.NONE) {
                    v.getOdometer().registerTripCurrentDrivingTime(object : DrivingTimeListener {
                        // 단위: SDK 문서에 명시 없음 → 초로 보고 분으로 바꾼다(실차 확인 필요)
                        override fun onDrivingTimeUpdated(value: Int?) = post {
                            drivingMinutes = value?.let { it / 60 }; src { copy(drivingTime = "Vehicle SDK") }
                        }
                        override fun onFailed(e: Exception) { Log.w(TAG, "driving time: $e") }
                    })
                }
            }, { Log.w(TAG, "driving time capability: $it") })
        }

        runCatching {
            v.getSafety().checkTelltalesCapabilities({ cfg ->
                if (cfg.accessType != AccessType.NONE) {
                    v.getSafety().registerTelltales(object : TelltaleInfoListener {
                        override fun onTelltaleInfoUpdated(info: Telltales?) = post {
                            attentionOn = info?.telltales.orEmpty().any {
                                it.type == TelltaleType.DRIVER_ATTENTION && it.state != TelltaleState.OFF
                            }
                            src { copy(telltale = "Vehicle SDK") }
                        }
                        override fun onFailed(e: Exception) { Log.w(TAG, "telltale: $e") }
                    })
                }
            }, { Log.w(TAG, "telltale capability: $it") })
        }
    }

    // ───────────── AAOS Car API ─────────────
    private fun startAaos() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return
        car = runCatching {
            Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (!ready) return@createCar
                runCatching {
                    val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    uxRestricted = ux.currentCarUxRestrictions?.isRequiresDistractionOptimization
                    ux.registerListener { r -> post { uxRestricted = r.isRequiresDistractionOptimization } }
                    src { copy(uxRestriction = "AAOS") }
                }.onFailure { Log.w(TAG, "ux restriction: $it") }
                runCatching {
                    val props = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                    props.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
                        override fun onChangeEvent(value: CarPropertyValue<*>) {
                            val ms = value.value as? Float ?: return
                            post { onSpeed(ms * 3.6f) }
                        }
                        override fun onErrorEvent(propId: Int, zone: Int) {}
                    }, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
                    src { copy(speed = "AAOS") }
                }.onFailure { Log.w(TAG, "speed: $it") }
            }
        }.onFailure { Log.w(TAG, "Car 연결 실패: $it") }.getOrNull()
    }

    private fun onSpeed(kmh: Float) {
        val now = android.os.SystemClock.elapsedRealtime()
        speedKmh = kmh
        speedHistory.addLast(now to kmh)
        while (speedHistory.isNotEmpty() && now - speedHistory.first().first > 2_000) speedHistory.removeFirst()
    }

    // ───────────── NaviHelper SDK ─────────────
    private fun startNavi() {
        val n = runCatching { NaviHelper(context) }.getOrNull() ?: return
        runCatching {
            n.initialize({
                navi = n
                n.addListener(object : NaviHelperEventListener {
                    override fun onTBTInfo(tbtInfo: List<TBTInfo>) = post {
                        tbtDistance = tbtInfo.firstOrNull()?.distance?.toInt(); src { copy(navi = "NaviHelper") }
                    }
                    override fun onCautionPointInfo(cautionPoints: List<CautionPoint>) = post {
                        cautionAhead = cautionPoints.any { it.remainDistance < 500 }
                    }
                    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = post {
                        _arrived.value = _arrived.value + 1
                    }
                })
                main.post(naviPoll)
            }, { Log.w(TAG, "NaviHelper 초기화 실패: $it") })
        }
    }

    private fun post(block: () -> Unit) = main.post { block(); publish() }.let { }

    /** 1초마다 UI·엔진이 부르기도 한다(쿨다운 해제 반영) */
    fun publish() {
        val now = android.os.SystemClock.elapsedRealtime()
        val realParked = gear?.let { it == VehicleGear.GEAR_PARK } ?: (uxRestricted == false || uxRestricted == null)
        val drop = speedHistory.firstOrNull()?.second?.minus(speedKmh ?: 0f) ?: 0f
        val realHardBrake = brakePressed && now - brakeAtMs < 2_000 && (speedKmh == null || drop >= 15f)
        val d = demo
        _snapshot.value = DrivingSnapshot(
            parked = d.parked ?: realParked,
            uxRestricted = if (d.parked == false) true else if (d.parked == true) false else (uxRestricted ?: false),
            speedKmh = speedKmh ?: if (d.parked == false) 95f else null,
            tbtDistanceM = d.tbtDistanceM ?: tbtDistance,
            naviSpeaking = d.naviSpeaking,
            cautionAhead = cautionAhead,
            hardBrake = d.hardBrake || realHardBrake,
            turnSignalOn = turnSignalOn,
            drivingMinutes = d.drivingMinutes ?: drivingMinutes,
            driverAttentionOn = d.driverAttention || attentionOn,
            minutesToArrival = d.minutesToArrival ?: minutesToArrival,
        )
    }

    companion object { private const val TAG = "SignalHub" }
}

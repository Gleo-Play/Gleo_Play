package com.gleoplay.app.core

/**
 * 차가 알려 주는 "지금 상황" 한 장.
 * null 은 "이 차에서 그 신호를 받을 수 없음(미지원·미연결)"을 뜻한다.
 */
data class DrivingSnapshot(
    /** 기어가 P 인가. 기어를 모르면 주행 제한 해제 여부로 대신 판단한다. */
    val parked: Boolean = true,
    /** AAOS CarUxRestrictions: 주행 제한이 걸려 있는가 */
    val uxRestricted: Boolean = false,
    val speedKmh: Float? = null,
    /** 다음 회전·분기 안내까지 남은 거리(m). NaviHelper TBT */
    val tbtDistanceM: Int? = null,
    /** 내비가 음성 안내를 하고 있는가(오디오 포커스 손실로도 감지) */
    val naviSpeaking: Boolean = false,
    /** 단속·주의 구간이 가까운가. NaviHelper CautionPoint */
    val cautionAhead: Boolean = false,
    /**
     * 급제동인가. 차량 계층에서 "브레이크 페달 + 2초 안에 15km/h 이상 감속"으로 판정한다.
     * 차속을 모르는 차에서는 페달 신호만으로 판정한다(보수적).
     */
    val hardBrake: Boolean = false,
    /** 방향지시등이 켜졌는가 */
    val turnSignalOn: Boolean = false,
    /** 이번 주행 연속 운전 시간(분). Vehicle SDK Odometer */
    val drivingMinutes: Int? = null,
    /** 운전자 주의 경고등(DRIVER_ATTENTION telltale)이 켜졌는가 — 지원 차종만 */
    val driverAttentionOn: Boolean = false,
    /** 도착까지 남은 시간(분). NaviHelper TripInfo */
    val minutesToArrival: Int? = null,
)

sealed class GateDecision(val reason: String) {
    /** 주차 중: 화면·음성 모두 사용 가능 */
    object Parked : GateDecision("주차 중")
    /** 주행 중이지만 지금은 한 문장 말해도 되는 순간 */
    object Go : GateDecision("말해도 됨")
    /** 길 안내·주의 구간 등: 말하지 않고 기다린다 */
    class Wait(reason: String) : GateDecision(reason)
    /** 급제동·방향지시등·내비 음성: 즉시 멈춘다 */
    class Stop(reason: String) : GateDecision(reason)
    /** 장시간 주행·경고등·도착 임박: 대화를 마무리한다 */
    class WrapUp(reason: String) : GateDecision(reason)

    val canSpeak: Boolean get() = this is Parked || this is Go
    override fun toString(): String = "${this::class.simpleName}($reason)"
}

/**
 * 안전 게이트 — "무엇을 말할지"보다 "언제 말하지 않을지"를 정한다.
 * 위에서부터 먼저 걸리는 규칙을 따른다(보수적 순서).
 */
class SafetyGate(
    private val tbtQuietDistanceM: Int = 1000,
    private val longDriveMinutes: Int = 110,
    private val arrivalWrapMinutes: Int = 10,
    // 급제동·방향지시등·내비 음성이 끝난 뒤 다시 말을 꺼내기까지 기다리는 시간(게임 흐름을 고려해 10초)
    private val stopCooldownMs: Long = 10_000,
    private val citySpeedKmh: Float = 30f,
) {
    private var lastStopAtMs: Long = Long.MIN_VALUE / 2

    fun evaluate(s: DrivingSnapshot, nowMs: Long): GateDecision {
        if (s.parked && !s.uxRestricted) return GateDecision.Parked

        if (s.hardBrake) { lastStopAtMs = nowMs; return GateDecision.Stop("급제동") }
        if (s.turnSignalOn) { lastStopAtMs = nowMs; return GateDecision.Stop("방향 전환") }
        if (s.naviSpeaking) { lastStopAtMs = nowMs; return GateDecision.Stop("내비 안내") }
        if (nowMs - lastStopAtMs < stopCooldownMs) return GateDecision.Wait("잠시 쉬는 중")

        if (s.driverAttentionOn) return GateDecision.WrapUp("주의 경고등")
        val minutes = s.drivingMinutes
        if (minutes != null && minutes >= longDriveMinutes) return GateDecision.WrapUp("장시간 주행")

        val tbt = s.tbtDistanceM
        if (tbt != null && tbt < tbtQuietDistanceM) return GateDecision.Wait("길 안내")
        if (s.cautionAhead) return GateDecision.Wait("주의 구간")

        val arrival = s.minutesToArrival
        if (arrival != null && arrival <= arrivalWrapMinutes) return GateDecision.WrapUp("도착 임박")

        // 내비 정보가 없을 때: 시내 저속 구간은 회전이 잦다고 보고 기다린다.
        val speed = s.speedKmh
        if (tbt == null && speed != null && speed > 0f && speed < citySpeedKmh) return GateDecision.Wait("시내 구간")

        return GateDecision.Go
    }
}

package com.gleoplay.app.core

/**
 * 차량 상황(안전 게이트 판단)에 따라 게임을 멈출지, 다시 이어 갈지 정한다.
 * - 멈출 때는 이유를 사람이 읽을 문장으로 돌려준다(화면 표시용. 주행 중에는 소리 내지 않는다).
 * - 다른 음성(내비 안내·Gleo 호출·전화)에 밀려 멈췄으면, 그 음성이 끝날 시간을 주고 이어 간다.
 */
object PausePolicy {
    /** 다른 음성에 밀려 멈춘 뒤 다시 말을 꺼내기까지 기다리는 시간 */
    const val AFTER_INTERRUPT_MS = 8_000L

    /** 지금 멈춰야 하면 그 이유 문장, 아니면 null */
    fun pauseReason(
        d: GateDecision,
        driverAttentionOn: Boolean,
        drivingMode: DrivingMode,
        drowsyLink: Boolean,
    ): String? {
        val parked = d is GateDecision.Parked
        return when {
            !parked && drivingMode == DrivingMode.PAUSE -> "정차하면 이어서 할게요"
            d is GateDecision.Stop -> stopText(d.reason)
            d is GateDecision.Wait -> waitText(d.reason)
            d is GateDecision.WrapUp && driverAttentionOn -> if (drowsyLink) "졸음 주의! 다음 휴게소에서 쉬었다 가요" else null
            d is GateDecision.WrapUp -> wrapText(d.reason)
            else -> null
        }
    }

    /** 멈춰 있는 게임을 지금 이어 가도 되는가 */
    fun canResume(d: GateDecision, reason: String?, nowMs: Long, interruptedAtMs: Long): Boolean =
        reason == null && d.canSpeak && nowMs - interruptedAtMs >= AFTER_INTERRUPT_MS

    private fun stopText(r: String) = when (r) {
        "급제동" -> "급제동! 운전에 집중할게요"
        "방향 전환" -> "방향 전환 중이라 잠시 멈출게요"
        "내비 안내" -> "내비 안내 중이라 잠시 멈출게요"
        else -> "$r — 잠시 멈출게요"
    }

    private fun waitText(r: String) = when (r) {
        "잠시 쉬는 중" -> "운전에 집중했다가 곧 이어서 할게요"
        "길 안내" -> "길 안내가 가까워서 잠시 기다릴게요"
        "주의 구간" -> "주의 구간이라 잠시 기다릴게요"
        "시내 구간" -> "회전이 잦은 시내 구간이라 잠시 기다릴게요"
        else -> "$r — 잠시 기다릴게요"
    }

    private fun wrapText(r: String) = when (r) {
        "장시간 주행" -> "오래 달렸어요. 다음 휴게소에서 쉬었다 가요"
        "도착 임박" -> "곧 도착이에요. 정차하면 결과를 보여 드릴게요"
        else -> "$r — 잠시 쉬었다 가요"
    }
}

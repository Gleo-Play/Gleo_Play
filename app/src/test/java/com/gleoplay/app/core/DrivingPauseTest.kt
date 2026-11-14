package com.gleoplay.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 주행/정차 전환과 안전 멈춤 규칙 */
class DrivingPauseTest {
    private val driving = DrivingSnapshot(parked = false, uxRestricted = true, speedKmh = 90f)

    private fun reason(d: GateDecision, attention: Boolean = false, mode: DrivingMode = DrivingMode.VOICE_ONLY, drowsy: Boolean = true) =
        PausePolicy.pauseReason(d, attention, mode, drowsy)

    @Test fun `정차 중에는 멈추지 않는다`() {
        assertNull(reason(GateDecision.Parked))
        assertEquals(GateDecision.Parked, SafetyGate().evaluate(DrivingSnapshot(parked = true), 0))
    }

    @Test fun `주행 중이어도 기어 P에 주행 제한이 걸려 있으면 정차로 보지 않는다`() {
        val d = SafetyGate().evaluate(DrivingSnapshot(parked = true, uxRestricted = true, speedKmh = 90f), 0)
        assertTrue(d is GateDecision.Go)
    }

    @Test fun `음성 전용이면 주행 중에도 계속, 정차 시에만이면 멈춘다`() {
        assertNull(reason(GateDecision.Go))
        assertEquals("정차하면 이어서 할게요", reason(GateDecision.Go, mode = DrivingMode.PAUSE))
    }

    @Test fun `방향지시등·급제동·내비 음성이면 즉시 멈추고 문구가 자연스럽다`() {
        val g = SafetyGate()
        val turn = g.evaluate(driving.copy(turnSignalOn = true), 0)
        assertTrue(turn is GateDecision.Stop)
        assertEquals("방향 전환 중이라 잠시 멈출게요", reason(turn))
        assertEquals("급제동! 운전에 집중할게요", reason(g.evaluate(driving.copy(hardBrake = true), 0)))
        assertEquals("내비 안내 중이라 잠시 멈출게요", reason(g.evaluate(driving.copy(naviSpeaking = true), 0)))
    }

    @Test fun `멈춤 신호가 끝나도 10초 동안은 기다렸다가 이어 간다`() {
        val g = SafetyGate()
        g.evaluate(driving.copy(turnSignalOn = true), 0)
        val after = g.evaluate(driving, 5_000)
        assertTrue(after is GateDecision.Wait)
        assertEquals("운전에 집중했다가 곧 이어서 할게요", reason(after))   // "잠시 쉬는 중 중이라" 같은 문구가 아님
        assertTrue(g.evaluate(driving, 10_001) is GateDecision.Go)
    }

    @Test fun `회전 안내가 가까우면 기다린다`() {
        val d = SafetyGate().evaluate(driving.copy(tbtDistanceM = 500), 0)
        assertEquals("길 안내가 가까워서 잠시 기다릴게요", reason(d))
    }

    @Test fun `졸음 주의 경고등은 연동이 켜져 있을 때만 멈춘다`() {
        val d = SafetyGate().evaluate(driving.copy(driverAttentionOn = true), 0)
        assertEquals("졸음 주의! 다음 휴게소에서 쉬었다 가요", reason(d, attention = true))
        assertNull(reason(d, attention = true, drowsy = false))
    }

    @Test fun `다른 음성에 밀려 멈췄으면 8초 동안은 다시 말하지 않는다`() {
        assertFalse(PausePolicy.canResume(GateDecision.Go, null, nowMs = 5_000, interruptedAtMs = 0))
        assertTrue(PausePolicy.canResume(GateDecision.Go, null, nowMs = 8_000, interruptedAtMs = 0))
        assertFalse(PausePolicy.canResume(GateDecision.Stop("급제동"), null, nowMs = 60_000, interruptedAtMs = 0))
    }
}

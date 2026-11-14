package com.gleoplay.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FollowUpTest {
    private val potato = Choice("감자", "🥔")
    private val corn = Choice("옥수수", "🌽")
    private val sea = Choice("바다", "🌊")
    private val mountain = Choice("산", "⛰️")

    @Test fun `받침에 맞춰 조사를 붙인다`() {
        assertEquals("감자를 먹고 옥수수는 남겼다", FollowUp.fill("{w을} 먹고 {l은} 남겼다", potato, corn, 0, null))
        assertEquals("산으로 가요, 바다로 말고", FollowUp.fill("{w으로} 가요, {l로} 말고", sea, mountain, 1, null))
        assertEquals("강원도에서 감자와 옥수수", FollowUp.fill("{r}에서 {w와} {l}", potato, corn, 0, "강원도"))
        assertEquals("서울로", FollowUp.fill("{w로}", Choice("서울", ""), corn, 0, null))   // ㄹ 받침은 "로"
    }

    @Test fun `직접 쓴 이어지는 질문이 있으면 그것을 쓴다`() {
        val pair = BalancePair("x", "음식", Difficulty.NORMAL, potato, corn, talk = "{w} 요리 중에 뭐가 제일 좋아요?")
        assertEquals("옥수수 요리 중에 뭐가 제일 좋아요?", FollowUp.make(pair, potato, corn, 1, 0, 1, null))
    }

    @Test fun `지역이 없으면 지역 틀은 쓰지 않는다`() {
        val pair = BalancePair("x", "지역", Difficulty.NORMAL, potato, corn)
        repeat(20) { i ->
            val s = FollowUp.make(pair, potato, corn, 0, 1, 0, null, Random(i))
            assertTrue(s, !s.contains("{") && !s.contains("이번  여행"))
        }
    }

    @Test fun `같은 틀이 연달아 나오지 않게 한다`() {
        val pair = BalancePair("x", "음식", Difficulty.NORMAL, potato, corn)
        val recent = mutableListOf<String>()
        val made = (0 until 4).map { FollowUp.make(pair, potato, corn, 0, 1, 0, null, Random(7), recent) }
        assertEquals(4, made.toSet().size)
    }

    @Test fun `소수 의견이 있으면 그쪽 이유부터 듣는다`() {
        val pair = BalancePair("x", "음식", Difficulty.NORMAL, potato, corn)
        val s = FollowUp.make(pair, potato, corn, 0, 2, 1, null)
        assertTrue(s, s.startsWith("옥수수를 고른 분도 있네요"))
    }

    @Test fun `이야기 중 명령은 짧은 말만 인정한다`() {
        assertEquals(Heard.Skip, AnswerParser.command("다음"))
        assertEquals(Heard.Skip, AnswerParser.command("다음 주제로 넘어가자"))
        assertEquals(Heard.Skip, AnswerParser.command("넘어가요"))
        assertEquals(Heard.Stop, AnswerParser.command("그만해"))
        assertEquals(Heard.Stop, AnswerParser.command("그만하자"))
        assertEquals(Heard.Nothing, AnswerParser.command("다음에 가자"))
        assertEquals(Heard.Nothing, AnswerParser.command("그만 먹어"))
        assertEquals(Heard.Nothing, AnswerParser.command("나는 감자가 좋아"))
    }
}

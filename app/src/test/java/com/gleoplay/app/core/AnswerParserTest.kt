package com.gleoplay.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerParserTest {
    private fun p(text: String, a: String = "감자", b: String = "옥수수") = AnswerParser.parse(text, a, b)

    @Test fun `선택지 이름과 끝말`() {
        assertEquals(Heard.Pick(0, true), p("감자요"))
        assertEquals(Heard.Pick(1, true), p("저는 옥수수 할래요!"))
        assertEquals(Heard.Pick(0, true), p("감 자"))
    }

    @Test fun `말고 보다 싫어`() {
        assertEquals(Heard.Pick(1, true), p("감자 말고 옥수수"))
        assertEquals(Heard.Pick(0, true), p("옥수수보다 감자"))
        assertEquals(Heard.Pick(1, true), p("감자는 싫어"))
        assertEquals(Heard.Pick(0, true), p("옥수수 아니고 감자"))
    }

    @Test fun `순서어`() {
        assertEquals(Heard.Pick(0, false), p("첫 번째요"))
        assertEquals(Heard.Pick(1, false), p("두번째"))
        assertEquals(Heard.Pick(1, false), p("오른쪽 거"))
        assertEquals(Heard.Pick(0, false), p("1번"))
    }

    @Test fun `둘 다 몰라 다음 그만`() {
        assertEquals(Heard.Both, p("둘 다!"))
        assertEquals(Heard.Unsure, p("음 몰라요"))
        assertEquals(Heard.Unsure, p("아무거나"))
        assertEquals(Heard.Skip, p("다음 거"))
        assertEquals(Heard.Stop, p("그만할래"))
        assertEquals(Heard.Nothing, p("어 음 잠깐만"))
        assertEquals(Heard.Nothing, p(""))
    }

    @Test fun `여러 단어 선택지의 핵심 단어`() {
        assertEquals(Heard.Pick(0, false), p("라면이지", "평생 라면만", "평생 밥만"))
        assertEquals(Heard.Pick(1, false), p("밥!", "평생 라면만", "평생 밥만"))
    }

    @Test fun `이름을 다 말하지 않고 핵심 단어만 말해도 알아듣는다`() {
        assertEquals(Heard.Pick(0, false), p("김치", "김치찌개", "된장찌개"))
        assertEquals(Heard.Pick(1, false), p("된장이요", "김치찌개", "된장찌개"))
        assertEquals(Heard.Pick(0, false), p("가까운 데가 좋아", "가까운 곳 자주", "먼 곳 가끔"))
        assertEquals(Heard.Pick(1, false), p("먼 곳", "가까운 곳 자주", "먼 곳 가끔"))
        assertEquals(Heard.Pick(1, false), p("가끔이지", "가까운 곳 자주", "먼 곳 가끔"))
        assertEquals(Heard.Pick(0, false), p("하늘", "하늘을 나는 능력", "투명 인간"))
        assertEquals(Heard.Pick(1, false), p("나는 투명", "하늘을 나는 능력", "투명 인간"))
        assertEquals(Heard.Pick(0, false), p("덮고 자야지", "이불 덮고 자기", "이불 차고 자기"))
        assertEquals(Heard.Pick(0, false), p("계획", "계획 꼼꼼히", "즉흥 여행"))
        assertEquals(Heard.Pick(0, false), p("짜장", "짜장면", "짬뽕"))
        assertEquals(Heard.Pick(1, false), p("비빔", "물냉면", "비빔냉면"))
    }

    @Test fun `양쪽에 다 있는 말이나 엉뚱한 말은 고른 것으로 치지 않는다`() {
        assertEquals(Heard.Nothing, p("이불", "이불 덮고 자기", "이불 차고 자기"))
        assertEquals(Heard.Nothing, p("찌개", "김치찌개", "된장찌개"))
        assertEquals(Heard.Nothing, p("먼저 말할게", "가까운 곳 자주", "먼 곳 가끔"))
        assertEquals(Heard.Nothing, p("평생", "평생 라면만", "평생 밥만"))
    }

    @Test fun `핵심 단어 뒤에 싫다는 말이 오면 반대쪽`() {
        assertEquals(Heard.Pick(1, false), p("매일 하는 건 싫어", "매일 정리", "한 번에 몰아서 정리"))
        assertEquals(Heard.Pick(0, false), p("매일 해야지", "매일 정리", "한 번에 몰아서 정리"))
    }

    @Test fun `여러 사람이 이어서 말한 대답을 표로 나눈다`() {
        assertEquals(listOf(0, 1, 0), AnswerParser.votes("바다 산 바다", "바다", "산"))
        assertEquals(listOf(1, 0), AnswerParser.votes("된장! 김치!", "김치찌개", "된장찌개"))
        assertEquals(emptyList<Int>(), AnswerParser.votes("바다 바다", "바다", "산"))          // 한쪽만 → 한 사람으로 본다
        assertEquals(emptyList<Int>(), AnswerParser.votes("바다 말고 산", "바다", "산"))       // 한 사람의 대답
        assertEquals(emptyList<Int>(), AnswerParser.votes("바다보다 산", "바다", "산"))
        assertEquals(emptyList<Int>(), AnswerParser.votes("바다랑 산 둘 다", "바다", "산"))
    }

    @Test fun `STT 오인식 유사 발음`() {
        assertEquals(Heard.Pick(1, false), p("곤드래밥", "오징어순대", "곤드레밥"))
        assertEquals(Heard.Pick(0, false), p("오징어 순데요", "오징어순대", "곤드레밥"))
    }
}

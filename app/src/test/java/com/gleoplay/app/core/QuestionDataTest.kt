package com.gleoplay.app.core

import com.gleoplay.app.data.QuestionLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 질문 데이터 검수: 개수·중복·금칙어·연령 규칙(연령 맞춤 모드) */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class QuestionDataTest {
    private val bank: List<BalancePair> = QuestionLoader.parse(File("src/main/assets/questions.json").readText())

    /** 아이와 함께 듣는 질문에 나오면 안 되는 말(성적·외모·다툼·술·위험 운전·창밖 행동) */
    private val banned = listOf(
        "술", "소주", "맥주", "음주", "성적", "시험 점수", "외모", "못생", "뚱뚱", "살찌", "다이어트",
        "이혼", "싸움", "때리", "욕설", "창밖", "손 내밀", "몸 내밀", "과속", "신호 위반", "칼치기",
    )

    @Test fun `질문은 150개 이상이고 id가 겹치지 않는다`() {
        assertTrue("질문 수 ${bank.size}", bank.size >= 150)
        assertEquals(bank.size, bank.map { it.id }.toSet().size)
    }

    @Test fun `같은 질문 쌍이 두 번 나오지 않는다`() {
        val keys = bank.map { setOf(it.a.label, it.b.label) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun `모든 선택지에 이름과 이미지가 있고 양쪽이 다르다`() {
        bank.forEach {
            assertTrue(it.id, it.a.label.isNotBlank() && it.b.label.isNotBlank() && it.a.emoji.isNotBlank() && it.b.emoji.isNotBlank())
            assertTrue(it.id, it.a.label != it.b.label)
        }
    }

    @Test fun `금칙어가 없다`() {
        bank.forEach { p ->
            val text = listOfNotNull(p.a.label, p.b.label, p.reaction, p.talk).joinToString(" ")
            banned.forEach { w -> assertTrue("${p.id}: '$w' 포함 → $text", !text.contains(w)) }
        }
    }

    @Test fun `연애 질문은 모두 10대 이상이다`() {
        bank.filter { it.category == ADULT_CATEGORY }.forEach { assertTrue(it.id, it.minAge >= 2) }
    }

    @Test fun `어떤 아이가 타도 카테고리마다 한 게임(10라운드)을 채울 수 있다`() {
        for (kid in KidBand.entries) {
            for (cat in listOf("음식", "여행", "취미", "일상", ADULT_CATEGORY)) {
                if (kid.hasKid && cat == ADULT_CATEGORY) continue
                val n = QuestionPool.allowed(bank, kid).count { it.region == null && it.category == cat }
                assertTrue("${kid.label}·$cat: $n 개", n >= ROUNDS_PER_GAME)
            }
        }
    }

    @Test fun `연령대 전용 질문이 미취학·초등·10대마다 30개씩 있고 이어지는 질문이 붙어 있다`() {
        for (kid in listOf(KidBand.PRESCHOOL, KidBand.ELEMENTARY, KidBand.TEEN)) {
            val mine = bank.filter { it.band == kid }
            assertEquals(kid.label, 30, mine.size)
            mine.forEach { p ->
                assertTrue(p.id, p.category in listOf("음식", "여행", "취미", "일상"))
                assertTrue(p.id, p.minAge <= kid.maxAge)
                val talk = FollowUp.make(p, p.a, p.b, 0, 1, 0, null)
                assertTrue("${p.id}: $talk", talk.isNotBlank() && !talk.contains("{") && !talk.contains("}"))
                assertEquals(p.id, talk, FollowUp.fill(p.talk!!, p.a, p.b, 0, null))
            }
        }
    }

    @Test fun `연령대 전용 질문은 그 연령대에만 나오고 다른 질문보다 먼저 나온다`() {
        for (kid in listOf(KidBand.PRESCHOOL, KidBand.ELEMENTARY, KidBand.TEEN)) {
            val order = QuestionPool.order(bank, GameConfig(kidBand = kid))
            assertTrue(kid.label, order.take(ROUNDS_PER_GAME).all { it.band == kid })
            assertTrue(kid.label, order.none { it.band != null && it.band != kid })
        }
        assertTrue(QuestionPool.order(bank, GameConfig(kidBand = KidBand.NONE)).none { it.band != null })
    }

    @Test fun `한 번 나온 질문은 준비한 질문을 다 돌 때까지 다시 나오지 않는다`() {
        val kid = KidBand.ELEMENTARY
        val total = QuestionPool.allowed(bank, kid).count { it.region == null }
        val seen = mutableListOf<String>()
        repeat(total / ROUNDS_PER_GAME) { game ->
            val round = QuestionPool.order(bank, GameConfig(kidBand = kid, recent = seen.toList()), kotlin.random.Random(game)).take(ROUNDS_PER_GAME)
            round.forEach { assertTrue("${game + 1}번째 게임에서 ${it.id} 가 다시 나옴", it.id !in seen) }
            seen += round.map { it.id }
        }
        assertEquals(seen.size, seen.toSet().size)
        assertEquals(total % ROUNDS_PER_GAME, QuestionPool.freshCount(bank, kid, null, seen))
        // 다 돈 뒤에는 가장 오래전에 나온 것부터 다시 나온다
        val all = QuestionPool.allowed(bank, kid).filter { it.region == null }.map { it.id }
        val again = QuestionPool.order(bank, GameConfig(kidBand = kid, recent = all)).take(3).map { it.id }
        assertEquals(all.take(3), again)
    }

    @Test fun `카테고리를 고르면 그 카테고리의 안 나온 질문부터, 다 떨어지면 그 카테고리의 오래된 질문을 낸다`() {
        val food = QuestionPool.allowed(bank, KidBand.NONE).filter { it.region == null && it.category == "음식" }.map { it.id }
        val seen = food.dropLast(2)                       // 음식은 2개만 남음
        val order = QuestionPool.order(bank, GameConfig(category = "음식", recent = seen)).take(4).map { it.id }
        assertEquals(food.takeLast(2).toSet(), order.take(2).toSet())
        assertEquals(seen.take(2), order.drop(2))          // 다른 카테고리로 넘어가지 않고 음식의 오래된 것부터
    }

    @Test fun `지역마다 특별 게임이 5개 이상 있다`() {
        bank.filter { it.region != null }.groupBy { it.region }.forEach { (r, list) ->
            assertTrue("$r: ${list.size}개", list.size >= 5)
        }
    }
}

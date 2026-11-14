package com.gleoplay.app.core

import kotlin.random.Random

/**
 * 결과 뒤에 던지는 "이어지는 질문". 밸런스 게임은 말문을 여는 계기일 뿐이고,
 * 이 질문으로 가족이 서로의 기억·이유·생각을 나누게 하는 것이 목적이다.
 *
 * 고르는 순서
 * 1) 질문 데이터에 직접 쓴 이어지는 질문(talk)이 있으면 그것
 * 2) 의견이 갈렸으면: 서로 이유를 말하고 설득해 보기
 * 3) 아무도 고르지 않았으면: 둘 중 하나에 얽힌 이야기 자유롭게
 * 4) 그 밖에는 카테고리별 질문 틀 중 하나(최근에 쓴 틀은 피한다)
 *
 * 틀 안의 표기: {w} 이긴 쪽, {l} 진 쪽, {r} 지역 이름. 뒤에 조사를 붙이면 받침에 맞춰 바꾼다
 * (예: {w을} → "감자를"/"막국수를", {w으로} → "바다로"/"산으로").
 */
object FollowUp {
    private val BY_CATEGORY: Map<String, List<String>> = mapOf(
        "음식" to listOf(
            "{w을} 마지막으로 먹은 게 언제였어요? 제일 맛있게 먹었던 곳도 이야기해 봐요.",
            "우리 가족 중에 {w을} 제일 좋아하는 사람은 누구일까요? 한 사람씩 맞혀 봐요.",
            "{l} 편도 할 말이 있죠? {l}의 좋은 점을 한 가지씩 말해 볼까요?",
            "다음 가족 외식을 {w으로} 한다면, 어디서 먹고 싶은지 이야기해 봐요.",
        ),
        "여행" to listOf(
            "{w} 하면 떠오르는 우리 가족 여행 추억, 기억나는 거 있어요?",
            "다음 가족 여행을 {w으로} 간다면, 꼭 하고 싶은 일을 한 가지씩 말해 볼까요?",
            "{l}의 좋은 점도 한 가지씩 찾아볼까요?",
            "지금까지 가 본 곳 중에 가장 기억에 남는 곳은 어디예요? 한 사람씩 이야기해 봐요.",
        ),
        "취미" to listOf(
            "{w}, 직접 해 본 적 있어요? 처음 해 봤을 때 이야기를 들려주세요.",
            "가족이 다 같이 {w을} 한다면 누가 제일 잘할까요? 이유도 말해 봐요.",
            "{l}의 재미를 아는 사람이 있다면 소개해 주세요.",
            "요즘 새로 해 보고 싶은 것이 있다면 한 가지씩 말해 볼까요?",
        ),
        "일상" to listOf(
            "왜 {w} 쪽인지, 이유를 한 가지씩 들려주세요.",
            "가족 중에 {l} 쪽인 사람도 있나요? 서로 어떤지 이야기해 봐요.",
            "{w와} 관련해서 최근에 있었던 일 하나만 이야기해 볼까요?",
            "어릴 때도 {w} 쪽이었어요? 그때 이야기를 들려주세요.",
        ),
        "연애" to listOf(
            "{w} 하면 떠오르는 추억이 있어요? 처음 만났을 때 이야기도 좋아요.",
            "서로가 {w와} {l} 중에 뭘 고를지 맞혀 볼까요?",
            "가장 기억에 남는 데이트나 선물이 있다면 이야기해 봐요.",
        ),
        "지역" to listOf(
            "{w}, 먹어 보거나 가 본 적 있어요? 그때 이야기를 들려주세요.",
            "이번 {r} 여행에서 {w}에 도전해 볼까요? 언제 하면 좋을지 이야기해 봐요.",
            "{r} 하면 떠오르는 추억이 있어요? 한 사람씩 이야기해 봐요.",
        ),
    )

    private val GENERAL = listOf(
        "{w을} 고른 이유를 한 사람씩 들려주세요.",
        "{w} 하면 떠오르는 추억이 있어요? 자유롭게 이야기해 봐요.",
    )

    /** 이어지는 질문 한 문장. recentTemplates 에 있는 틀은 되도록 피하고, 고른 틀을 그 목록에 더한다 */
    fun make(
        pair: BalancePair,
        a: Choice,
        b: Choice,
        winner: Int,
        votesA: Int,
        votesB: Int,
        region: String?,
        random: Random = Random.Default,
        recentTemplates: MutableList<String> = mutableListOf(),
    ): String {
        pair.talk?.let { return fill(it, a, b, winner, region) }
        if (votesA > 0 && votesB > 0 && votesA == votesB) {
            return "의견이 갈렸네요! ${a.label}${eulReul(a.label)} 고른 분, ${b.label}${eulReul(b.label)} 고른 분, " +
                "한 사람씩 이유를 말해 주세요. 상대편을 설득해 봐도 좋아요."
        }
        if (winner < 0) {
            return "${a.label}${waGwa(a.label)} ${b.label}, 둘 중 하나에 얽힌 이야기가 있으면 자유롭게 나눠 보세요."
        }
        if (votesA > 0 && votesB > 0) {
            val w = if (winner == 0) a else b
            val l = if (winner == 0) b else a
            return "${l.label}${eulReul(l.label)} 고른 분도 있네요! ${l.label} 편의 이유를 먼저 들어 보고, ${w.label} 편도 이야기해 봐요."
        }
        var pool = BY_CATEGORY[pair.category] ?: GENERAL
        if (region == null) pool = pool.filterNot { it.contains("{r}") }.ifEmpty { GENERAL }
        val fresh = pool.filterNot { it in recentTemplates }.ifEmpty { pool }
        val t = fresh[random.nextInt(fresh.size)]
        recentTemplates += t
        if (recentTemplates.size > 6) recentTemplates.removeAt(0)
        return fill(t, a, b, winner, region)
    }

    /** 아무도 말을 꺼내지 않을 때 한 번 더 건네는, 대답하기 쉬운 질문 */
    private val NUDGE_WIN = listOf(
        "어렵게 생각하지 않아도 돼요. {w} 하면 제일 먼저 떠오르는 사람이 누구예요?",
        "그럼 뒷자리부터 말해 볼까요? {w을} 고른 이유를 한 가지만 들려주세요.",
        "{w와} {l}, 요즘은 어느 쪽이 더 끌려요? 짧게만 말해도 좋아요.",
    )
    private val NUDGE_DRAW = listOf(
        "고르기 어려웠죠? 그럼 가볍게, {w} 하면 뭐가 제일 먼저 떠올라요?",
        "{w와} {l} 중에 최근에 더 가까이했던 쪽은 어디예요? 한 사람만 말해 줘도 좋아요.",
    )

    /** 양쪽에 표가 있을 때: 한쪽을 짚어서 먼저 말하게 한다 */
    private val NUDGE_SPLIT = listOf(
        "그럼 {l을} 고른 분부터 먼저 한마디 해 볼까요? 이유 한 가지만 들려주세요.",
        "누가 먼저 말할지 정하기 어렵죠? {w을} 고른 분이 먼저 설득해 보세요.",
    )

    fun nudge(a: Choice, b: Choice, winner: Int, votesA: Int, votesB: Int, random: Random = Random.Default): String {
        val pool = when {
            votesA > 0 && votesB > 0 -> NUDGE_SPLIT
            winner >= 0 -> NUDGE_WIN
            else -> NUDGE_DRAW
        }
        return fill(pool[random.nextInt(pool.size)], a, b, winner, null)
    }

    fun fill(t: String, a: Choice, b: Choice, winner: Int, region: String?): String {
        val w = (if (winner == 1) b else a).label
        val l = (if (winner == 1) a else b).label
        return Regex("\\{([wlr])([^}]*)\\}").replace(t) { m ->
            val word = when (m.groupValues[1]) { "w" -> w; "l" -> l; else -> region ?: "" }
            word + josa(word, m.groupValues[2])
        }
    }

    private fun josa(word: String, j: String): String = when (j) {
        "" -> ""
        "을", "를" -> eulReul(word)
        "은", "는" -> eunNeun(word)
        "이", "가" -> iGa(word)
        "와", "과" -> waGwa(word)
        "으로", "로" -> euroRo(word)
        else -> j
    }
}

private fun batchimOf(w: String): Int {
    val c = w.lastOrNull() ?: return 0
    if (c !in '가'..'힣') return 0
    return (c.code - 0xAC00) % 28
}
fun iGa(w: String) = if (batchimOf(w) != 0) "이" else "가"
fun waGwa(w: String) = if (batchimOf(w) != 0) "과" else "와"
/** 받침이 없거나 ㄹ 받침이면 "로" */
fun euroRo(w: String) = batchimOf(w).let { if (it == 0 || it == 8) "로" else "으로" }

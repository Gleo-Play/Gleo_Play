package com.gleoplay.app.core

/**
 * 한 게임의 하이라이트(게임 끝 화면·게임 기록에 보여 준다).
 * 대화 내용은 저장하지 않으므로, 이야기한 시간과 표만으로 만든다.
 */
data class Highlights(
    /** 가장 오래 이야기한 주제(이야기를 나눈 주제가 없으면 null) */
    val longest: RoundResult?,
    /** 의견이 갈린 주제 수(양쪽에 표가 있음) */
    val split: Int,
    /** 모두 같은 것을 고른 주제 수(두 표 이상이 한쪽에만) */
    val agreed: Int,
) {
    val isEmpty: Boolean get() = longest == null && split == 0 && agreed == 0
}

fun highlightsOf(results: List<RoundResult>): Highlights = Highlights(
    longest = results.filter { it.talkSec >= BalanceGame.MIN_TALKED_SEC }.maxByOrNull { it.talkSec },
    split = results.count { it.votesA > 0 && it.votesB > 0 },
    agreed = results.count { (it.votesA == 0) != (it.votesB == 0) && it.votesA + it.votesB >= 2 },
)

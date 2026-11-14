package com.gleoplay.app.core

import kotlin.random.Random

/**
 * 한 게임에 낼 질문을 고르고 순서를 정한다.
 *
 * 1) 탈 수 있는 질문만: 아이 연령에 맞는 것, 아이가 타면 '연애' 잠금, 연령대 전용 질문은 그 연령대에만.
 * 2) 좋아할 순서: 연령대 전용 → 고른 난이도 → 같은 카테고리의 다른 난이도 → (카테고리를 골랐다면) 다른 카테고리.
 * 3) 한 번 나온 질문은 다시 내지 않는다: 같은 카테고리 안에서 아직 안 나온 질문을 모두 낸 뒤에야,
 *    가장 오래전에 나온 것부터 다시 낸다(카드 한 벌을 다 돌리고 다시 섞는 방식).
 * 4) 지역에 있으면 그 지역 특별 주제를 앞쪽 3개까지(역시 안 나온 것부터).
 */
object QuestionPool {
    const val REGIONAL_ROUNDS = 3

    /** 이 탑승 구성으로 낼 수 있는 질문 */
    fun allowed(bank: List<BalancePair>, kid: KidBand): List<BalancePair> = bank.filter {
        it.minAge <= kid.maxAge &&
            !(kid.hasKid && it.category == ADULT_CATEGORY) &&
            (it.band == null || it.band == kid)
    }

    fun order(bank: List<BalancePair>, cfg: GameConfig, random: Random = Random.Default): List<BalancePair> {
        val seenAt = cfg.recent.withIndex().associate { it.value to it.index }
        /** 안 나온 것 먼저(섞어서), 나온 것은 오래된 순서로 */
        fun deck(list: List<BalancePair>): List<BalancePair> {
            val (fresh, seen) = list.partition { it.id !in seenAt }
            return fresh + seen.sortedBy { seenAt.getValue(it.id) }
        }
        val ok = allowed(bank, cfg.kidBand)
        val regional = if (cfg.region != null) deck(ok.filter { it.region == cfg.region }.shuffled(random)) else emptyList()
        val normal = ok.filter { it.region == null }
        val inCat = (if (cfg.category != null) normal.filter { it.category == cfg.category } else normal).ifEmpty { normal }
        val mine = inCat.filter { it.band != null }
        val general = inCat - mine.toSet()
        val byLevel = general.filter { it.level == cfg.difficulty }
        val liked = mine.shuffled(random) + byLevel.shuffled(random) + (general - byLevel.toSet()).shuffled(random)
        val others = (normal - inCat.toSet()).let { rest -> rest.filter { it.band != null }.shuffled(random) + rest.filter { it.band == null }.shuffled(random) }
        return (regional.take(REGIONAL_ROUNDS) + deck(liked) + deck(others)).distinctBy { it.id }
    }

    /** 아직 한 번도 안 나온 질문 수(지역 특별 주제 제외) */
    fun freshCount(bank: List<BalancePair>, kid: KidBand, category: String?, seen: Collection<String>): Int {
        val s = seen.toSet()
        return allowed(bank, kid).count { it.region == null && (category == null || it.category == category) && it.id !in s }
    }
}

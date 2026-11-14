package com.gleoplay.app.core

/** 음성 대답 해석 결과 */
sealed class Heard {
    /** 0 = A, 1 = B. exact = 선택지 이름이 그대로 들렸는가(유사 발음·순서어가 아님) */
    data class Pick(val side: Int, val exact: Boolean) : Heard()
    object Both : Heard()
    object Unsure : Heard()
    object Skip : Heard()
    object Stop : Heard()
    object Nothing : Heard()
}

/**
 * 짧은 한국어 대답을 2지선다 선택으로 해석한다.
 * - 띄어쓰기·문장부호·끝말("요", "할래", "이지")을 무시한다.
 * - "X 말고 Y", "X보다 Y", "X 싫어"를 처리한다.
 * - STT가 한두 글자 틀려도(곤드래밥 ↔ 곤드레밥) 음절 유사도로 맞춘다.
 */
object AnswerParser {
    private val STOP = listOf("그만할래", "그만하자", "그만해", "그만", "게임종료", "종료", "끝내자", "끝낼래")
    private val SKIP = listOf("다음문제", "다음거", "다음으로", "다음", "패스", "건너뛰", "넘어가", "넘겨")
    private val BOTH = listOf("둘다", "두개다", "둘모두", "다좋아", "둘다좋")
    private val UNSURE = listOf("몰라", "모르겠", "글쎄", "고민", "어렵", "애매", "아무거나", "상관없")
    private val FIRST = listOf("첫번째", "첫번쨰", "첫째", "1번", "일번", "앞에거", "앞에꺼", "앞에것", "왼쪽", "에이")
    private val SECOND = listOf("두번째", "두번쨰", "둘째", "2번", "이번거", "뒤에거", "뒤에꺼", "뒤에것", "오른쪽")
    private val NEG_AFTER = listOf("말고", "아니고", "아니야", "아니", "싫어", "별로", "빼고")

    fun parse(raw: String?, a: String, b: String): Heard {
        if (raw.isNullOrBlank()) return Heard.Nothing
        val t = norm(raw)
        if (t.isEmpty()) return Heard.Nothing
        if (STOP.any { t.contains(it) }) return Heard.Stop

        val na = norm(a)
        val nb = norm(b)
        val ia = t.lastIndexOf(na).takeIf { na.isNotEmpty() && it >= 0 }
        val ib = t.lastIndexOf(nb).takeIf { nb.isNotEmpty() && it >= 0 }

        // 선택지 이름이 그대로 들린 경우
        if (ia != null && ib == null) return Heard.Pick(negated(t, ia + na.length, 0), true)
        if (ib != null && ia == null) return Heard.Pick(negated(t, ib + nb.length, 1), true)
        if (ia != null && ib != null) {
            // 둘 다 들림: "X 말고 Y" / "X보다 Y" → 앞쪽이 진다. 그 외에는 마지막에 말한 쪽
            val first = if (ia < ib) 0 else 1
            val firstEnd = (if (first == 0) ia + na.length else ib!! + nb.length)
            val between = t.substring(firstEnd, minOf(t.length, firstEnd + 4))
            if (NEG_AFTER.any { between.startsWith(it) } || between.startsWith("보다")) return Heard.Pick(1 - first, true)
            if (BOTH.any { t.contains(it) }) return Heard.Both
            return Heard.Pick(1 - first, true)
        }

        if (BOTH.any { t.contains(it) }) return Heard.Both
        if (SKIP.any { t.contains(it) }) return Heard.Skip
        if (FIRST.any { t.contains(it) }) return Heard.Pick(0, false)
        if (SECOND.any { t.contains(it) }) return Heard.Pick(1, false)

        // 선택지의 핵심 단어("평생 라면만" → "라면", "김치찌개" → "김치", "가까운 곳 자주" → "가까운")가 한쪽에만 들린 경우
        val ka = keyHit(t, keys(a, b))
        val kb = keyHit(t, keys(b, a))
        if ((ka != null) != (kb != null)) {
            val side = if (ka != null) 0 else 1
            return Heard.Pick(if (negatedSoon(t, (ka ?: kb)!!)) 1 - side else side, false)
        }

        // 유사 발음(STT 오인식 보정)
        val sa = similarity(t, na)
        val sb = similarity(t, nb)
        val best = maxOf(sa, sb)
        if (best >= 0.6 && kotlin.math.abs(sa - sb) >= 0.2) return Heard.Pick(if (sa > sb) 0 else 1, false)

        if (UNSURE.any { t.contains(it) }) return Heard.Unsure
        return Heard.Nothing
    }

    /**
     * 이야기 나누는 중에 들린 말에서 명령만 찾는다("다음", "넘어가자", "그만하자").
     * 가족끼리 하는 대화("그만 먹어", "다음에 가자")를 명령으로 오해하지 않도록, 끝말을 뗀 말 전체가 명령어와 같을 때만 인정한다.
     */
    fun command(raw: String?): Heard {
        if (raw.isNullOrBlank()) return Heard.Nothing
        var t = norm(raw)
        for (s in listOf("요", "자", "줘", "해")) t = t.removeSuffix(s)
        if (t.isEmpty()) return Heard.Nothing
        if (t in CMD_STOP) return Heard.Stop
        if (t in CMD_SKIP) return Heard.Skip
        return Heard.Nothing
    }

    private val CMD_STOP = setOf("그만", "그만하", "그만할래", "게임그만", "게임종료", "종료", "끝내", "끝낼래", "게임끝", "게임끝내")
    private val CMD_SKIP = setOf(
        "다음", "다음주제", "다음주제로", "다음주제로가", "다음주제로넘어가", "다음문제", "다음거", "다음꺼", "다음으로", "다음으로가",
        "다음으로넘어가", "넘어가", "넘어갈래", "넘겨", "패스", "건너뛰", "건너뛸래", "건너뛰기", "건너뛸게", "넘어갈게", "넘길게", "다음거로", "다음걸로",
    )

    /** "감자 싫어" 처럼 선택지 바로 뒤에 부정어가 오면 반대쪽 */
    private fun negated(t: String, end: Int, side: Int): Int {
        var after = t.substring(minOf(end, t.length), minOf(t.length, end + 5))
        if (after.isNotEmpty() && after[0] in "은는이가을를도") after = after.substring(1) // "감자는 싫어"
        return if (NEG_AFTER.any { after.startsWith(it) }) 1 - side else side
    }

    fun norm(s: String): String = s.lowercase()
        .replace(Regex("[\\s\\p{Punct}·…~!?.,'\"“”‘’]"), "")

    private fun keyWords(label: String): List<String> = label.split(Regex("\\s+"))
        .map { norm(it).removeSuffix("만").removeSuffix("에").removeSuffix("로") }
        .filter { it.isNotEmpty() }

    /**
     * 선택지 이름을 다 말하지 않아도 알아듣기 위한 핵심 단어 목록.
     * - 낱말 그대로("라면", "밥")
     * - 낱말의 앞부분 두 글자 이상("김치찌개" → "김치", "가까운" → "가까", "하늘을" → "하늘")
     * 상대 선택지에도 들어 있는 말("평생", "찌개", "이불")은 구분이 안 되므로 뺀다. 긴 것부터 찾는다.
     */
    private fun keys(label: String, other: String): List<String> {
        val no = norm(other)
        val words = keyWords(label)
        val out = LinkedHashSet<String>()
        for (w in words) {
            // 한 글자 낱말("밥", "산")은 짧은 선택지에서만 쓴다. 긴 선택지의 "먼"은 "먼저" 같은 말에 걸리기 쉽다
            if (w.length >= 2 || words.size <= 2) out += w
            for (len in w.length - 1 downTo 2) out += w.substring(0, len)
        }
        // 낱말을 이어 말한 앞부분("먼 곳 가끔" → "먼곳")
        val nl = norm(label)
        for (len in nl.length - 1 downTo 2) out += nl.substring(0, len)
        return out.filter { it.isNotEmpty() && !no.contains(it) && it !in COMMON }.sortedByDescending { it.length }
    }

    /** 대답할 때 흔히 붙는 말. 선택지 안에 있어도("하늘을 나는 능력"의 "나는") 핵심 단어로 쓰지 않는다 */
    private val COMMON = setOf(
        "나는", "저는", "나도", "저도", "내가", "제가", "우리", "그냥", "진짜", "하는", "있는", "없는", "좋은", "좋아",
        "먼저", "이거", "그거", "저거", "이번", "아니", "그게", "근데", "일단", "역시", "당연",
    )

    /** 핵심 단어가 들렸으면 그 말이 끝나는 위치, 아니면 null */
    private fun keyHit(t: String, keys: List<String>): Int? {
        for (k in keys) { val i = t.lastIndexOf(k); if (i >= 0) return i + k.length }
        return null
    }

    /** 핵심 단어 뒤 가까운 곳에 부정어가 오면("매일 하는 건 싫어") 반대쪽 */
    private fun negatedSoon(t: String, end: Int): Boolean {
        val after = t.substring(minOf(end, t.length), minOf(t.length, end + 8))
        return NEG_AFTER.any { after.contains(it) }
    }

    /**
     * 여러 사람이 이어서 대답한 한 문장("바다 산 바다")에서 표를 순서대로 뽑는다.
     * 양쪽 선택지가 모두 들렸고 "말고·보다·둘 다" 같은 말이 없을 때만 여러 표로 본다(한 사람이 두 선택지를 그냥 나열하지는 않으므로).
     * 그 밖에는 빈 목록(한 사람의 대답으로 parse 가 처리한다).
     */
    fun votes(raw: String?, a: String, b: String): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        val t = norm(raw)
        if (NEG_AFTER.any { t.contains(it) } || t.contains("보다") || BOTH.any { t.contains(it) } || STOP.any { t.contains(it) }) return emptyList()
        val hits = mutableListOf<Pair<Int, Int>>() // 위치, 쪽
        fun collect(word: String, side: Int) {
            if (word.isEmpty()) return
            var i = t.indexOf(word)
            while (i >= 0) { hits += i to side; i = t.indexOf(word, i + word.length) }
        }
        val na = norm(a); val nb = norm(b)
        // 이름 전체가 없으면 가장 긴 핵심 단어로 센다
        collect(if (t.contains(na)) na else keys(a, b).firstOrNull { t.contains(it) } ?: "", 0)
        collect(if (t.contains(nb)) nb else keys(b, a).firstOrNull { t.contains(it) } ?: "", 1)
        if (hits.none { it.second == 0 } || hits.none { it.second == 1 }) return emptyList()
        return hits.sortedBy { it.first }.map { it.second }
    }

    /** text 안에서 label 길이만큼의 구간 중 가장 비슷한 것의 음절 유사도(0..1) */
    fun similarity(text: String, label: String): Double {
        if (label.isEmpty() || text.isEmpty()) return 0.0
        var best = 0.0
        val n = label.length
        for (len in maxOf(1, n - 1)..(n + 1)) {
            if (len > text.length) continue
            for (i in 0..(text.length - len)) {
                val w = text.substring(i, i + len)
                val d = levenshtein(w, label)
                val s = 1.0 - d.toDouble() / maxOf(w.length, n)
                if (s > best) best = s
            }
        }
        if (text.length < n - 1) best = 1.0 - levenshtein(text, label).toDouble() / n
        return best
    }

    private fun levenshtein(x: String, y: String): Int {
        val dp = IntArray(y.length + 1) { it }
        for (i in 1..x.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..y.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (x[i - 1] == y[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[y.length]
    }
}

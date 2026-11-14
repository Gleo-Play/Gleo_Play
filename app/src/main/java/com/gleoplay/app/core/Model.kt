package com.gleoplay.app.core

/** 음성 출력(앱 TTS) */
interface Speaker {
    fun speak(text: String, onDone: () -> Unit)
    fun stop()
}

/** 음성 입력(짧게 한 번 듣기) */
interface Ear {
    val available: Boolean
    /** 마이크·음성 인식을 쓸 수 없는 이유(권한 거부, 마이크 오류 등). 문제가 없으면 null */
    val problem: String? get() = null
    /** 한 번 듣기. onPartial 은 말하는 도중의 중간 결과, onResult 는 최종 결과(못 들으면 null) */
    fun listenOnce(onPartial: (String) -> Unit, onResult: (String?) -> Unit)
    fun cancel()
}

/** 시간 지연 실행(테스트에서는 즉시/수동 실행으로 바꾼다) */
interface Scheduler {
    fun after(ms: Long, block: () -> Unit)
    fun cancelAll()
}

enum class Difficulty(val label: String) { EASY("쉬움"), NORMAL("보통"), HARD("어려움") }
enum class ProgressMode(val label: String) { KEEP("선택 유지"), FRESH("매번 새롭게") }
/** 이야기 나누기: 결과 뒤에 이어지는 질문을 던지고 가족이 이야기하는 동안 기다린다(기본). 게임만: 결과 뒤 바로 다음 문제 */
enum class TalkMode(val label: String) { TALK("이야기 나누기"), GAME_ONLY("게임만") }
enum class TalkStyle(val label: String) { NATURAL("자연스럽게"), BRIGHT("발랄하게"), CALM("차분하게") }
enum class Language(val label: String) { AUTO("자동 감지"), KOREAN("한국어") }
enum class DrivingMode(val label: String) { VOICE_ONLY("음성 전용"), PAUSE("정차 시에만") }
enum class WheelButton(val label: String) { ANSWER_SKIP("응답/건너뛰기"), OFF("사용 안 함") }

val WAIT_OPTIONS = listOf(10, 15, 30, 45, 60)
const val ROUNDS_PER_GAME = 10

data class Choice(val label: String, val emoji: String)

/** 2지선다 한 쌍 */
data class BalancePair(
    val id: String,
    val category: String,
    val level: Difficulty,
    val a: Choice,
    val b: Choice,
    /** 지역 특별 게임이면 지역 이름(예: "강원도") */
    val region: String? = null,
    /** 지역 특별 게임의 리액션(사투리 등) */
    val reaction: String? = null,
    /** 연령: 0 누구나 · 1 초등 이상 · 2 10대 이상 · 3 어른만 */
    val minAge: Int = 0,
    /** 직접 쓴 이어지는 질문({w} 이긴 쪽, {l} 진 쪽, {r} 지역). 없으면 카테고리 틀을 쓴다 */
    val talk: String? = null,
    /** 연령대 전용 질문이면 그 연령대(미취학·초등·10대). 그 아이가 탔을 때만, 다른 질문보다 먼저 낸다 */
    val band: KidBand? = null,
)

/** 함께 탄 아이(가장 어린 아이 기준). maxAge 이하 질문만 낸다 */
enum class KidBand(val label: String, val maxAge: Int) {
    NONE("없음", 3), PRESCHOOL("미취학", 0), ELEMENTARY("초등", 1), TEEN("10대", 2);
    val hasKid: Boolean get() = this != NONE
}

/** 아이가 타면 잠그는 카테고리 */
const val ADULT_CATEGORY = "연애"

data class GameConfig(
    val difficulty: Difficulty = Difficulty.NORMAL,
    val players: Int = 2,
    val progress: ProgressMode = ProgressMode.FRESH,
    val talkMode: TalkMode = TalkMode.TALK,
    val waitSec: Int = 15,
    val category: String? = null,
    val region: String? = null,
    val style: TalkStyle = TalkStyle.NATURAL,
    val voiceGuide: Boolean = true,
    val kidBand: KidBand = KidBand.NONE,
    /** 지금까지 나온 질문 id(오래된 것부터). 안 나온 질문을 다 낸 뒤에야, 가장 오래전에 나온 것부터 다시 낸다 */
    val recent: List<String> = emptyList(),
)

/** DISCUSS: 이어지는 질문을 던진 뒤 가족이 이야기하는 동안 조용히 기다리는 단계 */
enum class Phase { IDLE, ASKING, WAITING, RESULT, DISCUSS, PAUSED, FINISHED }

data class RoundResult(
    val round: Int,
    val a: Choice,
    val b: Choice,
    /** 0 = A, 1 = B, -1 = 무승부·넘어감 */
    val winner: Int,
    val votesA: Int,
    val votesB: Int,
    /** 이 주제로 이야기를 나눈 시간(초). 내용은 저장하지 않는다 */
    val talkSec: Int = 0,
    /** 던진 이어지는 질문 */
    val followUp: String = "",
)

/** 화면이 그리는 게임 상태 한 장 */
data class GameView(
    val phase: Phase = Phase.IDLE,
    val round: Int = 0,
    val total: Int = ROUNDS_PER_GAME,
    val question: String = "둘 중 더 좋아하는 것은?",
    val a: Choice? = null,
    val b: Choice? = null,
    /** 선택 유지 모드에서 지난 라운드 승자 쪽(왕관 표시) */
    val crownSide: Int? = null,
    val winnerSide: Int? = null,
    val votesA: Int = 0,
    val votesB: Int = 0,
    val secondsLeft: Int = 0,
    /** 이번 라운드에 대답한 사람 수 / 대답할 사람 수 */
    val answered: Int = 0,
    val players: Int = 1,
    val comment: String = "",
    val pausedReason: String? = null,
    /** 마지막으로 들은 말(화면에 "들은 말: …"로 표시) */
    val heard: String? = null,
    /** 마이크를 쓸 수 없을 때 안내 */
    val micNote: String? = null,
    val region: String? = null,
    val category: String? = null,
    val results: List<RoundResult> = emptyList(),
    /** 이어지는 질문(이야기 나누기) */
    val followUp: String = "",
    /** 이번 주제로 이야기한 시간(초) */
    val talkSec: Int = 0,
    /** 방금(몇 초 안에) 말소리가 들렸는가 */
    val talking: Boolean = false,
    /** 마이크 없이 이야기 나누기: 다음 주제까지 남은 초(마이크가 있으면 null) */
    val talkLeft: Int? = null,
    /** 이야기를 마치고 다음 주제로 넘어가는 중 */
    val movingOn: Boolean = false,
)

data class GameRecord(
    val timeMs: Long,
    val category: String?,
    val region: String?,
    val difficulty: Difficulty,
    val players: Int,
    val results: List<RoundResult>,
)

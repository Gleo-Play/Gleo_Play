package com.gleoplay.app.core

import kotlin.random.Random

/**
 * 밸런스 게임 진행 엔진(1게임 = 10라운드).
 *
 * 라운드 흐름: ASKING(질문 낭독) → WAITING(대답 대기) → RESULT(승자 + 이어지는 질문) → DISCUSS(이야기 나누기) → 다음 주제
 * - 밸런스 게임은 말문을 여는 계기다. 고르기 쉬운 2지선다로 모두 한 마디씩 하게 한 뒤,
 *   이어지는 질문(기억·이유·설득)을 던지고 가족이 이야기하는 동안 끼어들지 않고 기다린다.
 * - 이야기 나누기: 말소리가 들리는 동안은 계속 기다리고, 한동안 조용해지거나 누군가 "다음"이라고 하면 다음 주제로 간다.
 *   들은 말의 내용은 저장하지 않고, 이야기한 시간만 기록한다.
 * - 게임만 모드: 결과 뒤 바로 다음 문제(예전 방식).
 * - 인원 수만큼 대답(표)을 받거나 대기 시간이 끝나면 결과를 낸다.
 * - 선택 유지: 지난 라운드 승자가 남고(왕관), 새 도전자와 겨룬다.
 * - 차량 신호로 [pause]되면 말하던 것을 멈추고, [resume] 시 그 라운드를 다시 묻는다(이야기 중이었으면 조용히 이어서 기다린다).
 */
class BalanceGame(
    private val bank: List<BalancePair>,
    private val speaker: Speaker,
    private val ear: Ear,
    private val scheduler: Scheduler,
    private val random: Random = Random.Default,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val onChange: (GameView) -> Unit,
    private val onFinished: (GameRecord) -> Unit = {},
    /** 라운드에 낸 질문 id(최근 질문 기록용) */
    private val onPlayed: (String) -> Unit = {},
) {
    var view = GameView()
        private set
    var config = GameConfig()
        private set

    private var queue = ArrayDeque<BalancePair>()
    private val results = mutableListOf<RoundResult>()
    private var votesA = 0
    private var votesB = 0
    private var current: Pair<Choice, Choice>? = null
    private var champion: Choice? = null
    private var championSide: Int? = null
    private var resumePhase: Phase? = null
    private var answered = 0          // 이번 라운드에 대답한 사람 수
    private var reprompted = false    // 이번 라운드에 이미 다시 물었는가
    private var token = 0 // 비동기 콜백이 지난 라운드에서 온 것인지 구분
    private var currentPair: BalancePair? = null
    private val recentTemplates = mutableListOf<String>()
    private var talkTotal = 0      // 이번 주제로 이야기한 초
    private var silentSec = 0      // 마지막 말소리 뒤로 조용했던 초
    private var heardTalk = false  // 이번 주제에서 말소리가 한 번이라도 들렸는가
    private var leaving = false    // 이야기를 마치고 다음 주제로 넘어가는 중
    private var graceLeft = 2      // 이번 라운드에 대기 시간을 더 늘려 줄 수 있는 횟수
    private var nudged = false     // 이번 주제에서 이미 한 번 더 말을 걸었는가
    private var nudging = false    // 한 번 더 말을 거는 중(낭독 중)

    val running: Boolean get() = view.phase != Phase.IDLE && view.phase != Phase.FINISHED

    fun start(cfg: GameConfig) {
        stopAll()
        config = cfg
        results.clear()
        champion = null; championSide = null
        queue = ArrayDeque(buildPool(cfg))
        view = GameView(round = 0, region = cfg.region, category = cfg.category)
        nextRound()
    }

    /** 터치·시연 도구·운전대 버튼으로 한 표 넣기 */
    fun vote(side: Int) {
        // 질문을 읽는 중에 누르면 낭독을 끊고 바로 대답으로 받는다
        val interrupted = view.phase == Phase.ASKING
        if (interrupted) {
            stopAll()
            emit(view.copy(phase = Phase.WAITING, secondsLeft = config.waitSec))
        }
        if (view.phase != Phase.WAITING) return
        if (side == 0) votesA++ else votesB++
        countAnswer()
        // 아직 대답할 사람이 남았으면 이어서 듣는다
        if (interrupted && view.phase == Phase.WAITING) listenLoop(token)
    }

    /** "둘 다!" — 양쪽에 한 표씩, 대답한 사람은 한 명 */
    private fun voteBoth() {
        votesA++; votesB++
        countAnswer()
    }

    private fun countAnswer() {
        answered++
        val need = config.players.coerceAtLeast(1)
        // 아직 대답할 사람이 남았으면 다음 사람이 말할 시간을 넉넉히 남겨 둔다
        val left = if (answered < need) maxOf(view.secondsLeft, NEXT_ANSWER_SEC) else view.secondsLeft
        emit(view.copy(votesA = votesA, votesB = votesB, answered = answered, secondsLeft = left))
        if (answered >= need) finishRound()
    }

    /** 음성 대답을 해석해 처리한다. 선택·둘 다·건너뛰기·그만으로 처리했으면 true */
    fun hear(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val (a, b) = current ?: return false
        val h = AnswerParser.parse(text, a.label, b.label)
        if (h is Heard.Stop) { stop(); return true }
        if (view.phase != Phase.WAITING) return false
        emit(view.copy(heard = text.trim()))
        // 여러 사람이 이어서 대답한 한 문장("바다 산 바다")은 남은 인원만큼 표로 나눠 받는다
        val many = AnswerParser.votes(text, a.label, b.label)
        val remaining = config.players.coerceAtLeast(1) - answered
        if (many.size >= 2 && remaining >= 2) {
            for (side in many.take(remaining)) { if (view.phase == Phase.WAITING) vote(side) }
            return true
        }
        return when (h) {
            is Heard.Pick -> { vote(h.side); true }
            Heard.Both -> { voteBoth(); true }
            Heard.Skip -> { skip(); true }
            Heard.Unsure -> { reprompt(unsure = true); false }
            else -> { reprompt(unsure = false); false }
        }
    }

    /** 못 알아들었으면 라운드마다 한 번만 다시 묻는다 */
    private fun reprompt(unsure: Boolean) {
        if (reprompted || !config.voiceGuide) return
        reprompted = true
        val (a, b) = current ?: return
        val my = ++token
        ear.cancel()
        val head = if (unsure) "고민되죠? 하나만 골라 주세요." else "잘 못 알아들었어요."
        speaker.speak("$head ${a.label}${vs(a.label)} ${b.label}?") { if (my == token) listenLoop(my) }
    }

    fun skip() {
        if (view.phase == Phase.WAITING || view.phase == Phase.ASKING) {
            stopAll(); votesA = 0; votesB = 0; answered = 0; finishRound()
        } else if (view.phase == Phase.RESULT) {
            stopAll(); nextRound()
        } else if (view.phase == Phase.DISCUSS) {
            moveOn(spoken = false)
        }
    }

    fun stop() {
        if (!running) return
        if (view.phase == Phase.DISCUSS || resumePhase == Phase.DISCUSS) saveTalk()
        stopAll()
        finishGame(spoken = false)
    }

    /** 1초마다 호출: 대기 카운트다운 · 이야기 나누기 시간 재기 */
    fun tick() {
        when (view.phase) {
            Phase.WAITING -> {
                val left = view.secondsLeft - 1
                if (left <= 0) finishRound() else emit(view.copy(secondsLeft = left))
            }
            Phase.DISCUSS -> {
                if (nudging) return   // Gleo가 한 번 더 말을 거는 동안은 시간을 세지 않는다
                talkTotal++; silentSec++
                val noMic = view.talkLeft != null
                // 아무도 말을 꺼내지 않으면 바로 넘기지 않고, 더 쉬운 질문으로 한 번만 더 말을 건다
                if (!noMic && !heardTalk && !nudged && config.voiceGuide && silentSec >= NUDGE_SEC) { nudge(); return }
                val limit = when {
                    noMic -> NO_MIC_TALK_SEC
                    heardTalk || nudged -> SILENCE_SEC
                    else -> FIRST_SILENCE_SEC
                }
                if (silentSec >= limit) moveOn(spoken = true)
                else emit(view.copy(talkSec = talkTotal, talking = heardTalk && silentSec <= TALKING_SEC, talkLeft = if (noMic) limit - silentSec else null))
            }
            else -> {}
        }
    }

    fun pause(reason: String) {
        if (!running || view.phase == Phase.PAUSED) return
        resumePhase = view.phase
        stopAll()
        emit(view.copy(phase = Phase.PAUSED, pausedReason = reason))
    }

    /** 멈춘 채로 이유만 바뀌었을 때(방향지시등 → 잠시 쉬는 중 → 길 안내 대기 등) 화면 문구만 바꾼다 */
    fun updatePauseReason(reason: String) {
        if (view.phase == Phase.PAUSED && view.pausedReason != reason) emit(view.copy(pausedReason = reason))
    }

    fun resume() {
        if (view.phase != Phase.PAUSED) return
        val back = resumePhase
        resumePhase = null
        emit(view.copy(pausedReason = null))
        when {
            // 이야기 중이었으면 다시 말을 걸지 않고 조용히 이어서 기다린다
            back == Phase.DISCUSS -> startDiscussion(++token)
            back == Phase.RESULT && leaving -> nextRound()
            // 이어지는 질문을 말하다 멈췄으면 그 질문만 다시
            back == Phase.RESULT && config.talkMode == TalkMode.TALK && view.followUp.isNotEmpty() -> {
                val my = ++token
                emit(view.copy(phase = Phase.RESULT))
                if (config.voiceGuide) speaker.speak("이어서 이야기해 봐요. ${view.followUp}") { if (my == token) startDiscussion(my) }
                else startDiscussion(my)
            }
            back == Phase.RESULT -> nextRound()
            else -> askCurrent(again = true)
        }
    }

    // ───────────────────────── 내부 ─────────────────────────

    /** 이번 게임의 질문 순서(연령·카테고리·난이도에 맞추고, 한 번 나온 질문은 다 돌 때까지 다시 내지 않는다) */
    private fun buildPool(cfg: GameConfig): List<BalancePair> = QuestionPool.order(bank, cfg, random)

    private fun nextRound() {
        if (view.round >= ROUNDS_PER_GAME || queue.isEmpty()) { finishGame(spoken = true); return }
        val pair = queue.removeFirst()
        currentPair = pair
        leaving = false
        onPlayed(pair.id)
        val champ = champion
        current = if (config.progress == ProgressMode.KEEP && champ != null) {
            // 왕관 자리(지난 승자)는 같은 쪽에 두고, 새 도전자를 반대쪽에
            val challenger = if (pair.a.label != champ.label) pair.a else pair.b
            if (championSide == 0) champ to challenger else challenger to champ
        } else pair.a to pair.b
        votesA = 0; votesB = 0; answered = 0; reprompted = false; graceLeft = 2
        val (a, b) = current!!
        emit(
            view.copy(
                phase = Phase.ASKING, round = view.round + 1, a = a, b = b,
                crownSide = if (config.progress == ProgressMode.KEEP) championSide else null,
                winnerSide = null, votesA = 0, votesB = 0, secondsLeft = config.waitSec,
                comment = "", region = pair.region ?: config.region, heard = null, micNote = null,
                followUp = "", talkSec = 0, talking = false, talkLeft = null, movingOn = false,
                answered = 0, players = config.players.coerceAtLeast(1),
            )
        )
        pendingReaction = pair.reaction
        askCurrent(again = false)
    }

    private var pendingReaction: String? = null

    private fun askCurrent(again: Boolean) {
        val (a, b) = current ?: return
        val my = ++token
        emit(view.copy(phase = Phase.ASKING, secondsLeft = config.waitSec))
        val head = when {
            again -> "다시 갈게요. "
            view.round == 1 && view.region != null -> "${view.region} 특별 게임! "
            view.round == 1 -> "밸런스 게임 시작! "
            view.round == ROUNDS_PER_GAME -> "마지막 라운드! "
            else -> "${view.round}라운드! "
        }
        // 여럿이 탈 때는 첫 질문에서 한 번만, 겹쳐 말하지 않도록 안내한다
        val turn = if (!again && view.round == 1 && config.players >= 2) " 한 사람씩 차례로 대답해 주세요." else ""
        val line = "$head${a.label}${vs(a.label)} ${b.label}? ${view.question}$turn"
        if (config.voiceGuide) speaker.speak(line) { if (my == token) startWaiting() } else startWaiting()
    }

    private fun startWaiting() {
        emit(view.copy(phase = Phase.WAITING, secondsLeft = config.waitSec))
        listenLoop(token)
    }

    /**
     * 대기 시간 동안 짧게 여러 번 듣는다(인원 수만큼 대답이 모이면 끝).
     * - 중간 결과에 선택지 이름이 그대로 들리면 최종 결과를 기다리지 않고 바로 확정한다.
     * - 마이크를 쓸 수 없으면 듣기를 멈추고 터치 안내를 띄운다(대기 시간·카드 선택은 그대로).
     * - 인식이 곧바로 실패하면 1.5초 쉬었다가 다시 듣는다(빈 루프 방지).
     */
    private fun listenLoop(my: Int) {
        if (view.phase != Phase.WAITING || my != token) return
        if (!ear.available) { micUnavailable(); return }
        val started = clock()
        var decided = false
        ear.listenOnce(
            onPartial = { p ->
                if (decided || my != token || view.phase != Phase.WAITING) return@listenOnce
                val (a, b) = current ?: return@listenOnce
                // 대기 시간이 끝나 갈 때 누군가 말하는 중이면, 대답이 잘리지 않게 조금 더 기다린다(라운드마다 두 번까지)
                if (p.isNotBlank() && view.secondsLeft < ANSWER_GRACE_SEC && graceLeft > 0) {
                    graceLeft--
                    emit(view.copy(secondsLeft = ANSWER_GRACE_SEC))
                }
                val h = AnswerParser.parse(p, a.label, b.label)
                // 마지막 한 표일 때만 중간 결과로 바로 확정한다. 대답할 사람이 더 남았으면 듣기를 끊지 않고 끝까지 듣는다
                // (끊으면 이어서 말하던 다음 사람의 대답을 놓친다)
                val lastOne = config.players.coerceAtLeast(1) - answered <= 1
                if ((h is Heard.Pick && h.exact && lastOne) || h is Heard.Stop) {
                    decided = true
                    ear.cancel()
                    hear(p)
                    if (my == token && view.phase == Phase.WAITING) listenLoop(my)
                }
            },
            onResult = { text ->
                if (decided || my != token || view.phase != Phase.WAITING) return@listenOnce
                decided = true
                if (text.isNullOrBlank()) {
                    if (ear.problem != null) { micUnavailable(); return@listenOnce }
                    if (clock() - started < QUICK_FAIL_MS) scheduler.after(RETRY_DELAY_MS) { listenLoop(my) }
                    else listenLoop(my)
                    return@listenOnce
                }
                hear(text)
                if (my == token && view.phase == Phase.WAITING) listenLoop(my)
            },
        )
    }

    private fun micUnavailable() {
        val why = ear.problem ?: "마이크를 쓸 수 없어요"
        emit(view.copy(micNote = "$why · 카드를 눌러 골라 주세요"))
    }

    private fun finishRound() {
        if (view.phase != Phase.WAITING && view.phase != Phase.ASKING) return
        ear.cancel()
        val (a, b) = current ?: return
        val winner = when {
            votesA > votesB -> 0
            votesB > votesA -> 1
            else -> -1
        }
        val talk = config.talkMode == TalkMode.TALK
        val followUp = if (talk) {
            val pair = currentPair ?: BalancePair("", "", Difficulty.NORMAL, a, b)
            FollowUp.make(pair, a, b, winner, votesA, votesB, view.region, random, recentTemplates)
        } else ""
        results += RoundResult(view.round, a, b, winner, votesA, votesB, followUp = followUp)
        if (winner >= 0) {
            champion = if (winner == 0) a else b
            championSide = winner
        }
        val comment = if (talk) talkComment(a, b, winner) else comment(a, b, winner)
        val my = ++token
        emit(view.copy(phase = Phase.RESULT, winnerSide = winner.takeIf { it >= 0 }, comment = comment, followUp = followUp, secondsLeft = 0, results = results.toList()))
        val afterComment = {
            if (my == token) {
                if (talk) startDiscussion(my, fresh = true)
                else scheduler.after(NEXT_DELAY_MS) { if (my == token) nextRound() }
            }
        }
        val line = listOf(comment, followUp).filter { it.isNotBlank() }.joinToString(" ")
        if (config.voiceGuide) speaker.speak(line) { afterComment() } else afterComment()
    }

    /**
     * 이야기 나누기 시작(또는 멈췄다가 이어서). 앱은 말하지 않고 듣기만 한다.
     * 말소리(중간 결과 포함)가 들릴 때마다 조용한 시간을 0으로 되돌리고, 짧은 "다음"·"그만"만 명령으로 받는다.
     */
    private fun startDiscussion(my: Int, fresh: Boolean = false) {
        if (my != token) return
        if (fresh) { talkTotal = 0; heardTalk = false; nudged = false }
        silentSec = 0
        nudging = false
        val noMic = !ear.available || ear.problem != null
        emit(view.copy(phase = Phase.DISCUSS, talkSec = talkTotal, talking = false, talkLeft = if (noMic) NO_MIC_TALK_SEC else null, heard = null))
        if (!noMic) listenTalk(my)
    }

    private fun listenTalk(my: Int) {
        if (view.phase != Phase.DISCUSS || my != token) return
        val started = clock()
        ear.listenOnce(
            onPartial = { p -> if (my == token && view.phase == Phase.DISCUSS && p.isNotBlank()) voiceHeard() },
            onResult = { text ->
                if (my != token || view.phase != Phase.DISCUSS) return@listenOnce
                if (!text.isNullOrBlank()) {
                    voiceHeard()
                    when (AnswerParser.command(text)) {
                        Heard.Stop -> { stop(); return@listenOnce }
                        Heard.Skip -> { moveOn(spoken = true, asked = true); return@listenOnce }
                        else -> {}
                    }
                    listenTalk(my)
                } else if (ear.problem != null) {
                    // 마이크가 중간에 안 되면 시간으로만 넘어간다
                    silentSec = 0
                    emit(view.copy(talkLeft = NO_MIC_TALK_SEC))
                } else if (clock() - started < QUICK_FAIL_MS) {
                    scheduler.after(RETRY_DELAY_MS) { listenTalk(my) }
                } else listenTalk(my)
            },
        )
    }

    /**
     * 이어지는 질문 뒤에 아무도 말을 꺼내지 않을 때, 대답하기 더 쉬운 질문으로 한 번만 더 말을 건다.
     * 주제마다 한 번뿐이고, 그래도 조용하면 다음 주제로 넘어간다.
     */
    private fun nudge() {
        val (a, b) = current ?: return
        nudged = true
        nudging = true
        ear.cancel()
        val my = ++token
        val line = FollowUp.nudge(a, b, view.winnerSide ?: -1, votesA, votesB, random)
        emit(view.copy(followUp = line, talking = false))
        speaker.speak(line) {
            if (my == token) { nudging = false; silentSec = 0; listenTalk(my) }
        }
    }

    private fun voiceHeard() {
        silentSec = 0
        heardTalk = true
        if (!view.talking) emit(view.copy(talking = true))
    }

    /** 이번 주제의 이야기 시간을 결과에 남긴다 */
    private fun saveTalk() {
        val last = results.lastOrNull() ?: return
        // 말소리가 한 번도 없었으면 이야기한 것으로 치지 않는다(마이크를 못 쓸 때는 알 수 없으므로 시간 그대로)
        val sec = if (heardTalk || view.talkLeft != null) talkTotal else 0
        if (last.round == view.round) results[results.lastIndex] = last.copy(talkSec = sec)
    }

    /** 이야기 나누기를 마치고 다음 주제로 */
    private fun moveOn(spoken: Boolean, asked: Boolean = false) {
        if (view.phase != Phase.DISCUSS) return
        saveTalk()
        stopAll()
        val my = token
        // 이야기 단계를 먼저 끝낸다(넘어간다고 말하는 동안 tick 이 다시 넘기지 않도록)
        leaving = true
        emit(view.copy(phase = Phase.RESULT, results = results.toList(), talking = false, talkLeft = null, movingOn = true))
        val last = view.round >= ROUNDS_PER_GAME || queue.isEmpty()
        val line = when {
            !spoken || last -> null
            asked -> "네, 다음 주제로 갈게요."
            heardTalk -> "좋은 이야기였어요. 다음 주제로 갈게요."
            else -> "다음 주제로 넘어가 볼게요."
        }
        if (line != null && config.voiceGuide) speaker.speak(line) { if (my == token) nextRound() } else nextRound()
    }

    /** 이야기 나누기 모드의 결과 한 마디(짧게. 바로 뒤에 이어지는 질문이 온다) */
    private fun talkComment(a: Choice, b: Choice, winner: Int): String {
        val reaction = pendingReaction
        if (winner < 0) return if (votesA == 0 && votesB == 0) "이번엔 고르기 어려웠나 봐요." else "무승부예요!"
        val w = if (winner == 0) a else b
        val base = when (config.style) {
            TalkStyle.BRIGHT -> "와! ${w.label} 승리!"
            TalkStyle.CALM -> "이번에는 ${w.label}${iGa(w.label)} 골랐어요."
            TalkStyle.NATURAL -> "이번 라운드는 ${w.label}!"
        }
        return listOfNotNull(reaction, base).joinToString(" ")
    }

    private fun comment(a: Choice, b: Choice, winner: Int): String {
        if (winner < 0) return when (config.style) {
            TalkStyle.BRIGHT -> "앗, 무승부예요! 다음 판에서 승부를 가려 봐요!"
            TalkStyle.CALM -> "이번에는 승부가 나지 않았어요. 다음으로 넘어갈게요."
            TalkStyle.NATURAL -> "이번 라운드는 무승부예요. 다음으로 갈게요."
        }
        val w = if (winner == 0) a else b
        val l = if (winner == 0) b else a
        val reaction = pendingReaction
        val base = when (config.style) {
            TalkStyle.BRIGHT -> "와! ${w.label} 승리! ${l.label}${eunNeun(l.label)} 아쉽지만 다음 기회에!"
            TalkStyle.CALM -> "이번 라운드 승자는 ${w.label}예요."
            TalkStyle.NATURAL -> "이번 라운드 승자는 ${w.label}! ${w.label}${eulReul(w.label)} 고른 이유도 궁금하네요."
        }
        return listOfNotNull(reaction, base).joinToString(" ")
    }

    private fun finishGame(spoken: Boolean) {
        ++token
        val record = GameRecord(clock(), config.category, config.region, config.difficulty, config.players, results.toList())
        emit(view.copy(phase = Phase.FINISHED, results = results.toList(), pausedReason = null, comment = summary()))
        if (results.isNotEmpty()) onFinished(record)
        if (spoken && config.voiceGuide) speaker.speak(summary()) {}
    }

    private fun summary(): String {
        val talked = results.filter { it.talkSec >= MIN_TALKED_SEC }
        if (config.talkMode == TalkMode.TALK && talked.isNotEmpty()) {
            val min = results.sumOf { it.talkSec } / 60
            val time = if (min >= 1) "${min}분 동안 " else ""
            val longest = highlightsOf(results).longest?.takeIf { talked.size >= 2 }
            val best = longest?.let { " 가장 오래 나눈 건 ${it.a.label}${waGwa(it.a.label)} ${it.b.label} 이야기였어요." } ?: ""
            return "오늘은 ${talked.size}가지 주제로 ${time}이야기를 나눴어요.$best 다음에 또 같이 이야기해요!"
        }
        val wins = results.filter { it.winner >= 0 }.map { if (it.winner == 0) it.a.label else it.b.label }
        if (wins.isEmpty()) return "게임이 끝났어요. 다음에 또 같이 해요!"
        return "게임 끝! 오늘 가장 사랑받은 건 ${wins.groupingBy { it }.eachCount().maxBy { it.value }.key}! 다음에 또 같이 해요."
    }

    private fun stopAll() {
        token++
        speaker.stop(); ear.cancel(); scheduler.cancelAll()
    }

    private fun emit(v: GameView) { view = v; onChange(v) }

    companion object {
        const val NEXT_DELAY_MS = 2_500L
        const val QUICK_FAIL_MS = 1_000L
        const val RETRY_DELAY_MS = 1_500L
        /** 이야기가 시작된 뒤 이만큼 조용하면 다음 주제로 */
        const val SILENCE_SEC = 12
        /** 이어지는 질문 뒤 아무도 말하지 않을 때 기다리는 시간(음성 안내를 꺼서 다시 말을 걸 수 없을 때) */
        const val FIRST_SILENCE_SEC = 20
        /**
         * 이어지는 질문 뒤 이만큼 조용하면 더 쉬운 질문으로 한 번 더 말을 건다.
         * 생각할 시간을 주고, 음성 인식이 늦게 도착해도 말하는 도중에 끼어들지 않도록 넉넉히 둔다.
         */
        const val NUDGE_SEC = 15
        /** 마이크를 쓸 수 없을 때 이야기 시간(화면의 "다음 주제" 버튼으로 먼저 넘어갈 수 있다) */
        const val NO_MIC_TALK_SEC = 45
        /** 한 사람이 대답한 뒤, 다음 사람이 말할 수 있게 최소한 남겨 두는 시간 */
        const val NEXT_ANSWER_SEC = 8
        /** 대기 시간이 끝나 갈 때 말소리가 들리면 남은 시간을 이만큼으로 늘린다 */
        const val ANSWER_GRACE_SEC = 5
        /** 마지막 말소리 뒤 이 시간까지는 "이야기 중"으로 표시 */
        const val TALKING_SEC = 3
        /** 이만큼 이야기했으면 "이야기를 나눈 주제"로 센다 */
        const val MIN_TALKED_SEC = 10
    }
}

private fun hasBatchim(w: String): Boolean {
    val c = w.lastOrNull() ?: return false
    if (c !in '가'..'힣') return false
    return (c.code - 0xAC00) % 28 != 0
}
fun eulReul(w: String) = if (hasBatchim(w)) "을" else "를"
fun eunNeun(w: String) = if (hasBatchim(w)) "은" else "는"
private fun vs(w: String) = if (hasBatchim(w)) "이냐," else "냐,"

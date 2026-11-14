package com.gleoplay.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class BalanceGameTest {
    private val spoken = mutableListOf<String>()
    private val tasks = mutableListOf<() -> Unit>()
    private var listening: ((String?) -> Unit)? = null
    private var partial: ((String) -> Unit)? = null
    private var micProblem: String? = null
    private var listenCount = 0
    private var records = mutableListOf<GameRecord>()

    private val speaker = object : Speaker {
        var pending: (() -> Unit)? = null
        override fun speak(text: String, onDone: () -> Unit) { spoken += text; pending = onDone }
        override fun stop() { pending = null }
    }
    private val ear = object : Ear {
        override val available = true
        override val problem: String? get() = micProblem
        override fun listenOnce(onPartial: (String) -> Unit, onResult: (String?) -> Unit) {
            listening = onResult; partial = onPartial; listenCount++
        }
        override fun cancel() { listening = null; partial = null }
    }
    private val scheduler = object : Scheduler {
        override fun after(ms: Long, block: () -> Unit) { tasks += block }
        override fun cancelAll() { tasks.clear() }
    }

    private val bank = (1..12).map {
        BalancePair("q$it", if (it <= 6) "음식" else "여행", Difficulty.NORMAL, Choice("가$it", "🅰"), Choice("나$it", "🅱"))
    } + BalancePair("r1", "지역", Difficulty.NORMAL, Choice("감자", "🥔"), Choice("옥수수", "🌽"), region = "강원도", reaction = "포실포실!")

    private lateinit var game: BalanceGame

    @Before fun setUp() {
        game = BalanceGame(bank, speaker, ear, scheduler, Random(1), { 0L }, onChange = {}, onFinished = { records += it })
    }

    private fun finishSpeech() { speaker.pending?.invoke() }
    private fun runTasks() { val t = tasks.toList(); tasks.clear(); t.forEach { it() } }

    @Test fun `질문을 읽고 대답을 기다린다`() {
        game.start(GameConfig(players = 1))
        assertEquals(Phase.ASKING, game.view.phase)
        assertEquals(1, game.view.round)
        assertTrue(spoken.last().contains("밸런스 게임 시작"))
        finishSpeech()
        assertEquals(Phase.WAITING, game.view.phase)
        assertNotNull(listening)
    }

    @Test fun `음성 대답으로 승자가 정해진다`() {
        game.start(GameConfig(players = 1))
        finishSpeech()
        val a = game.view.a!!.label
        listening!!.invoke("저는 $a 할래요")
        assertEquals(Phase.RESULT, game.view.phase)
        assertEquals(0, game.view.winnerSide)
        assertTrue(game.view.comment.contains(a))
    }

    @Test fun `인원 수만큼 표가 모여야 결과가 나온다`() {
        game.start(GameConfig(players = 3))
        finishSpeech()
        game.vote(1); game.vote(0)
        assertEquals(Phase.WAITING, game.view.phase)
        game.vote(1)
        assertEquals(Phase.RESULT, game.view.phase)
        assertEquals(1, game.view.winnerSide)
        assertEquals(2, game.view.votesB)
    }

    @Test fun `질문을 읽는 중에 카드를 누르면 낭독을 끊고 바로 표로 받는다`() {
        game.start(GameConfig(players = 1))
        assertEquals(Phase.ASKING, game.view.phase)
        game.vote(1)
        assertEquals(Phase.RESULT, game.view.phase)
        assertEquals(1, game.view.winnerSide)
    }

    @Test fun `낭독 중 한 명이 누르면 나머지는 이어서 듣고, 끊긴 낭독은 다시 진행시키지 않는다`() {
        game.start(GameConfig(players = 2, talkMode = TalkMode.GAME_ONLY))
        game.vote(0)
        assertEquals(Phase.WAITING, game.view.phase)
        assertEquals(1, game.view.votesA)
        assertNotNull(listening)
        finishSpeech() // 끊긴 낭독의 늦은 완료 신호(이미 지워져 있어야 함)
        assertEquals(Phase.WAITING, game.view.phase)
        assertEquals(1, game.view.votesA)
        game.vote(0)
        assertEquals(Phase.RESULT, game.view.phase)
    }

    @Test fun `낭독 중 건너뛰기를 누르면 바로 결과로 넘어간다`() {
        game.start(GameConfig(players = 1, talkMode = TalkMode.GAME_ONLY))
        game.skip()
        assertEquals(Phase.RESULT, game.view.phase)
        assertNull(game.view.winnerSide)
    }

    @Test fun `대기 시간이 끝나면 무승부로 넘어간다`() {
        game.start(GameConfig(players = 2, waitSec = 10))
        finishSpeech()
        repeat(10) { game.tick() }
        assertEquals(Phase.RESULT, game.view.phase)
        assertNull(game.view.winnerSide)
    }

    @Test fun `대기 시간이 끝나 갈 때 말하는 중이면 조금 더 기다려 대답을 받는다`() {
        game.start(GameConfig(players = 1, waitSec = 10))
        finishSpeech()
        repeat(9) { game.tick() }
        assertEquals(1, game.view.secondsLeft)
        partial!!.invoke("두")                         // 말하기 시작(아직 무슨 말인지 모름)
        assertEquals(BalanceGame.ANSWER_GRACE_SEC, game.view.secondsLeft)
        repeat(2) { game.tick() }
        assertEquals(Phase.WAITING, game.view.phase)
        listening!!.invoke("두 번째요")
        assertEquals(Phase.RESULT, game.view.phase)
        assertEquals(1, game.view.winnerSide)
    }

    @Test fun `대기 시간은 라운드마다 두 번까지만 늘려 준다`() {
        game.start(GameConfig(players = 1, waitSec = 10))
        finishSpeech()
        repeat(9) { game.tick() }
        repeat(3) {
            partial!!.invoke("음")
            repeat(BalanceGame.ANSWER_GRACE_SEC - 1) { game.tick() }
        }
        game.tick()
        assertEquals(Phase.RESULT, game.view.phase)   // 계속 말소리만 나도 끝없이 기다리지 않는다
    }

    @Test fun `한 사람이 대답하면 다음 사람이 말할 시간을 남겨 둔다`() {
        game.start(GameConfig(players = 2, waitSec = 10))
        finishSpeech()
        repeat(9) { game.tick() }
        assertEquals(1, game.view.secondsLeft)
        game.vote(0)
        assertEquals(BalanceGame.NEXT_ANSWER_SEC, game.view.secondsLeft)
        assertEquals(1, game.view.answered)
        assertEquals(2, game.view.players)
        repeat(BalanceGame.NEXT_ANSWER_SEC - 1) { game.tick() }
        assertEquals(Phase.WAITING, game.view.phase)
        game.vote(1)
        assertEquals(Phase.RESULT, game.view.phase)
    }

    @Test fun `여러 사람이 이어서 말하면 한 번에 여러 표로 받는다`() {
        game.start(GameConfig(players = 4))
        finishSpeech()
        val a = game.view.a!!.label; val b = game.view.b!!.label
        listening!!.invoke("$a $b $a")
        assertEquals(Phase.WAITING, game.view.phase)
        assertEquals(3, game.view.answered)
        assertEquals(2, game.view.votesA); assertEquals(1, game.view.votesB)
        listening!!.invoke("$a 요")
        assertEquals(Phase.RESULT, game.view.phase)
        assertEquals(0, game.view.winnerSide)
    }

    @Test fun `대답할 사람이 더 남았으면 중간 결과로 듣기를 끊지 않는다`() {
        game.start(GameConfig(players = 2))
        finishSpeech()
        val a = game.view.a!!.label
        val before = listenCount
        partial!!.invoke(a)
        assertEquals(0, game.view.answered)            // 아직 확정하지 않음
        assertEquals(before, listenCount)              // 듣기를 다시 시작하지 않음
        listening!!.invoke(a)
        assertEquals(1, game.view.answered)
    }

    @Test fun `여럿이 탈 때는 첫 질문에서 한 사람씩 대답해 달라고 안내한다`() {
        game.start(GameConfig(players = 3))
        assertTrue(spoken.last().contains("한 사람씩"))
        finishSpeech(); game.vote(0); game.vote(0); game.vote(1); finishSpeech()
        game.skip()
        assertEquals(2, game.view.round)
        assertTrue(!spoken.last().contains("한 사람씩"))  // 첫 질문에서만
    }

    @Test fun `10라운드 뒤 게임이 끝나고 기록이 남는다`() {
        game.start(GameConfig(players = 1, talkMode = TalkMode.GAME_ONLY))
        repeat(ROUNDS_PER_GAME) {
            finishSpeech(); game.vote(0); finishSpeech(); runTasks()
        }
        assertEquals(Phase.FINISHED, game.view.phase)
        assertEquals(1, records.size)
        assertEquals(ROUNDS_PER_GAME, records[0].results.size)
    }

    @Test fun `선택 유지 모드에서는 승자가 다음 라운드에 남는다`() {
        game.start(GameConfig(players = 1, progress = ProgressMode.KEEP, talkMode = TalkMode.GAME_ONLY))
        finishSpeech()
        val winner = game.view.b!!
        game.vote(1); finishSpeech(); runTasks()
        assertEquals(2, game.view.round)
        assertEquals(winner, game.view.b)
        assertEquals(1, game.view.crownSide)
    }

    @Test fun `지역이 있으면 지역 특별 게임으로 시작한다`() {
        game.start(GameConfig(players = 1, region = "강원도"))
        assertEquals("감자", game.view.a!!.label)
        assertTrue(spoken.last().contains("강원도 특별 게임"))
        finishSpeech(); game.vote(0)
        assertTrue(game.view.comment.startsWith("포실포실!"))
    }

    @Test fun `일시정지 후 재개하면 같은 라운드를 다시 묻는다`() {
        game.start(GameConfig(players = 1))
        finishSpeech()
        game.pause("급제동")
        assertEquals(Phase.PAUSED, game.view.phase)
        assertNull(listening)
        game.resume()
        assertEquals(Phase.ASKING, game.view.phase)
        assertEquals(1, game.view.round)
        assertTrue(spoken.last().startsWith("다시 갈게요"))
    }

    @Test fun `멈춘 동안 이유가 바뀌면 문구만 바꾸고 계속 멈춰 있다`() {
        game.start(GameConfig(players = 1))
        finishSpeech()
        game.pause("방향 전환 중이라 잠시 멈출게요")
        val asked = spoken.size
        game.updatePauseReason("운전에 집중했다가 곧 이어서 할게요")
        assertEquals(Phase.PAUSED, game.view.phase)
        assertEquals("운전에 집중했다가 곧 이어서 할게요", game.view.pausedReason)
        assertEquals(asked, spoken.size)
    }

    @Test fun `그만이라고 하면 게임을 끝낸다`() {
        game.start(GameConfig(players = 1))
        finishSpeech()
        listening!!.invoke("그만할래")
        assertEquals(Phase.FINISHED, game.view.phase)
    }

    @Test fun `못 알아들으면 한 번만 다시 묻는다`() {
        game.start(GameConfig(players = 1))
        finishSpeech()
        listening!!.invoke("어 음 잠깐만")
        assertTrue(spoken.last().startsWith("잘 못 알아들었어요"))
        assertEquals(Phase.WAITING, game.view.phase)
        assertEquals("어 음 잠깐만", game.view.heard)
        finishSpeech()                       // 다시 묻기 끝 → 다시 듣기
        val asked = spoken.size
        listening!!.invoke("뭐라고")          // 두 번째로 못 알아들음 → 또 묻지 않고 계속 듣기
        assertEquals(asked, spoken.size)
        assertNotNull(listening)
    }

    @Test fun `몰라요에는 고민되죠 하고 다시 묻는다`() {
        game.start(GameConfig(players = 1))
        finishSpeech()
        listening!!.invoke("음 몰라요")
        assertTrue(spoken.last().startsWith("고민되죠"))
    }

    @Test fun `둘 다는 양쪽에 한 표씩, 대답은 한 명으로 센다`() {
        game.start(GameConfig(players = 2))
        finishSpeech()
        listening!!.invoke("둘 다!")
        assertEquals(Phase.WAITING, game.view.phase)
        assertEquals(1, game.view.votesA); assertEquals(1, game.view.votesB)
        listening!!.invoke("${game.view.b!!.label}요")
        assertEquals(Phase.RESULT, game.view.phase)
        assertEquals(1, game.view.winnerSide)
    }

    @Test fun `중간 결과에 선택지 이름이 들리면 바로 확정한다`() {
        game.start(GameConfig(players = 1))
        finishSpeech()
        partial!!.invoke(game.view.a!!.label)
        assertEquals(Phase.RESULT, game.view.phase)
        assertEquals(0, game.view.winnerSide)
    }

    @Test fun `중간 결과의 순서어는 최종 결과를 기다린다`() {
        game.start(GameConfig(players = 1))
        finishSpeech()
        partial!!.invoke("첫 번")
        assertEquals(Phase.WAITING, game.view.phase)
        listening!!.invoke("첫 번째요")
        assertEquals(0, game.view.winnerSide)
    }

    @Test fun `마이크 문제가 있으면 듣기를 멈추고 터치 안내를 띄운다`() {
        game.start(GameConfig(players = 1))
        finishSpeech()
        micProblem = "마이크 권한이 꺼져 있어요"
        val before = listenCount
        listening!!.invoke(null)
        assertEquals(before, listenCount)
        assertTrue(game.view.micNote!!.contains("카드를 눌러"))
        assertEquals(Phase.WAITING, game.view.phase)
        game.vote(1)
        assertEquals(Phase.RESULT, game.view.phase)
    }

    @Test fun `인식이 곧바로 실패하면 잠시 쉬었다 다시 듣는다`() {
        game.start(GameConfig(players = 1))
        finishSpeech()
        val before = listenCount
        listening!!.invoke(null)              // clock 이 0 이라 '곧바로 실패'
        assertEquals(before, listenCount)
        runTasks()
        assertEquals(before + 1, listenCount)
    }

    @Test fun `아이가 타면 나이에 맞지 않는 질문과 연애 카테고리를 빼고 낸다`() {
        val kidBank = listOf(
            BalancePair("k1", "연애", Difficulty.NORMAL, Choice("커플티", "👕"), Choice("커플링", "💍"), minAge = 2),
            BalancePair("k2", "일상", Difficulty.NORMAL, Choice("문자", "💬"), Choice("전화", "📞"), minAge = 1),
            BalancePair("k3", "일상", Difficulty.NORMAL, Choice("강아지", "🐶"), Choice("고양이", "🐱"), minAge = 0),
        )
        val seen = mutableListOf<String>()
        val g = BalanceGame(kidBank, speaker, ear, scheduler, Random(1), { 0L }, onChange = {}, onPlayed = { seen += it })
        g.start(GameConfig(players = 1, category = "연애", kidBand = KidBand.PRESCHOOL, talkMode = TalkMode.GAME_ONLY))
        repeat(3) { finishSpeech(); g.vote(0); finishSpeech(); runTasks() }
        assertEquals(listOf("k3"), seen)      // 미취학: 누구나(0) 질문만, 연애는 골라도 잠김
    }

    @Test fun `최근에 나온 질문은 뒤로 미룬다`() {
        // 음식 6개 중 3개를 최근에 냈다 → 같은 카테고리 안에서 새 질문 3개가 먼저, 최근 질문은 그 뒤
        val recent = (1..3).map { "q$it" }
        val seen = mutableListOf<String>()
        val g = BalanceGame(bank, speaker, ear, scheduler, Random(3), { 0L }, onChange = {}, onPlayed = { seen += it })
        g.start(GameConfig(players = 1, category = "음식", recent = recent, talkMode = TalkMode.GAME_ONLY))
        repeat(5) { finishSpeech(); g.vote(0); finishSpeech(); runTasks() }
        assertEquals(setOf("q4", "q5", "q6"), seen.take(3).toSet())
        assertTrue(seen.drop(3).take(2).all { it in recent })
    }

    @Test fun `카테고리를 고르면 그 카테고리부터 낸다`() {
        game.start(GameConfig(players = 1, category = "여행"))
        assertTrue(game.view.a!!.label.removePrefix("가").toInt() > 6)
    }

    // ───────── 이야기 나누기(기본 모드) ─────────

    /** 1라운드에서 A를 골라 결과·이어지는 질문까지 말하게 한 뒤 이야기 나누기에 들어간다 */
    private fun toDiscussion(cfg: GameConfig = GameConfig(players = 1)) {
        game.start(cfg)
        finishSpeech(); game.vote(0)
        assertEquals(Phase.RESULT, game.view.phase)
        finishSpeech()
        assertEquals(Phase.DISCUSS, game.view.phase)
    }

    @Test fun `결과 뒤에 이어지는 질문을 던지고 이야기 나누기에 들어간다`() {
        toDiscussion()
        assertTrue(game.view.followUp.isNotBlank())
        assertTrue(spoken.last().contains(game.view.followUp))
        assertTrue(!game.view.followUp.contains("{"))
        assertNotNull(listening)                      // 말소리를 듣고 있다
        assertEquals(1, game.view.round)
    }

    @Test fun `이야기하는 동안은 기다리고, 조용해지면 다음 주제로 간다`() {
        toDiscussion()
        listening!!.invoke("음, 나는")                 // 누군가 말을 꺼냈다
        repeat(3) {
            repeat(BalanceGame.SILENCE_SEC - 1) { game.tick() }
            listening!!.invoke("나는 지난 여름에 먹었던 게 생각나")   // 말소리 → 조용한 시간 0
        }
        assertEquals(Phase.DISCUSS, game.view.phase)
        assertTrue(game.view.talkSec >= 3 * (BalanceGame.SILENCE_SEC - 1))
        val asked = spoken.size
        repeat(BalanceGame.SILENCE_SEC) { game.tick() }
        assertEquals(asked + 1, spoken.size)
        assertTrue(spoken.last().contains("다음 주제"))
        finishSpeech()
        assertEquals(2, game.view.round)
        assertTrue(game.view.results.first().talkSec >= 3 * (BalanceGame.SILENCE_SEC - 1))
    }

    /** 아무도 말하지 않아 다음 주제로 넘어간다고 말하기 시작할 때까지(중간에 한 번 더 말을 건다) */
    private fun silentUntilMoveOn() {
        repeat(BalanceGame.NUDGE_SEC) { game.tick() }
        finishSpeech()                                // 한 번 더 건넨 질문 낭독 끝
        repeat(BalanceGame.SILENCE_SEC) { game.tick() }
    }

    @Test fun `아무도 말을 꺼내지 않으면 더 쉬운 질문으로 한 번 더 말을 건다`() {
        toDiscussion()
        val first = game.view.followUp
        val asked = spoken.size
        repeat(BalanceGame.NUDGE_SEC) { game.tick() }
        assertEquals(asked + 1, spoken.size)
        assertEquals(Phase.DISCUSS, game.view.phase)
        assertTrue(game.view.followUp != first && !game.view.followUp.contains("{"))
        assertEquals(spoken.last(), game.view.followUp)
        assertNull(listening)                          // 말을 거는 동안은 듣지 않는다
        repeat(30) { game.tick() }                     // 말을 거는 동안은 시간을 세지 않는다
        assertEquals(Phase.DISCUSS, game.view.phase)
        finishSpeech()
        assertNotNull(listening)
        listening!!.invoke("아 그거 할머니 생각난다")
        repeat(BalanceGame.SILENCE_SEC - 1) { game.tick() }
        assertEquals(Phase.DISCUSS, game.view.phase)
        assertEquals(asked + 1, spoken.size)           // 한 주제에 한 번만 다시 말을 건다
        assertEquals(first, game.view.results.first().followUp) // 기록에는 처음 질문이 남는다
    }

    @Test fun `누군가 말을 시작했으면 다시 말을 걸지 않는다`() {
        toDiscussion()
        val asked = spoken.size
        listening!!.invoke("음 나는 말이야")
        repeat(BalanceGame.SILENCE_SEC - 1) { game.tick() }
        assertEquals(asked, spoken.size)
        assertEquals(Phase.DISCUSS, game.view.phase)
    }

    @Test fun `음성 안내를 끄면 다시 말을 걸지 않고 기다렸다가 넘어간다`() {
        game.start(GameConfig(players = 1, voiceGuide = false))
        game.vote(0)
        assertEquals(Phase.DISCUSS, game.view.phase)
        repeat(BalanceGame.FIRST_SILENCE_SEC - 1) { game.tick() }
        assertEquals(Phase.DISCUSS, game.view.phase)
        assertTrue(spoken.isEmpty())
        game.tick()
        assertEquals(2, game.view.round)
    }

    @Test fun `넘어간다고 말하는 동안 시간이 흘러도 다시 넘기지 않는다`() {
        toDiscussion()
        silentUntilMoveOn()
        val asked = spoken.size
        repeat(5) { game.tick() }                     // 말하는 중(아직 끝나지 않음)
        assertEquals(asked, spoken.size)
        assertEquals(1, game.view.round)
        finishSpeech()
        assertEquals(2, game.view.round)
    }

    @Test fun `넘어간다고 말하다 멈추면 재개할 때 다음 주제로 간다`() {
        toDiscussion()
        silentUntilMoveOn()
        game.pause("급제동")
        game.resume()
        assertEquals(2, game.view.round)
    }

    @Test fun `중간 결과만 들려도 이야기 중으로 본다`() {
        toDiscussion()
        repeat(BalanceGame.NUDGE_SEC - 1) { game.tick() }
        partial!!.invoke("그러니까")
        repeat(BalanceGame.SILENCE_SEC - 1) { game.tick() }
        assertEquals(Phase.DISCUSS, game.view.phase)
    }

    @Test fun `아무도 말하지 않으면 잠시 기다렸다가 넘어간다`() {
        toDiscussion()
        repeat(BalanceGame.NUDGE_SEC) { game.tick() }
        finishSpeech()                                // 한 번 더 건넨 질문
        repeat(BalanceGame.SILENCE_SEC - 1) { game.tick() }
        assertEquals(Phase.DISCUSS, game.view.phase)
        game.tick()
        assertTrue(spoken.last().contains("다음 주제"))
        finishSpeech()
        assertEquals(2, game.view.round)
        assertEquals(0, game.view.results.first().talkSec) // 말소리가 없었으면 이야기 시간으로 치지 않는다
    }

    @Test fun `의견이 갈렸는데 조용하면 한쪽을 짚어서 먼저 말하게 한다`() {
        game.start(GameConfig(players = 2))
        finishSpeech(); game.vote(0); game.vote(1); finishSpeech()
        repeat(BalanceGame.NUDGE_SEC) { game.tick() }
        assertTrue(spoken.last().contains("고른 분"))
        assertTrue(!spoken.last().contains("고르기 어려웠"))
    }

    @Test fun `한 번 더 말을 거는 중에 멈췄다가 재개하면 조용히 이어서 듣는다`() {
        toDiscussion()
        repeat(BalanceGame.NUDGE_SEC) { game.tick() }
        game.pause("방향 전환 중이라 잠시 멈출게요")
        val asked = spoken.size
        game.resume()
        assertEquals(Phase.DISCUSS, game.view.phase)
        assertEquals(asked, spoken.size)
        assertNotNull(listening)
        repeat(BalanceGame.SILENCE_SEC) { game.tick() }
        assertTrue(spoken.last().contains("다음 주제"))
    }

    @Test fun `하이라이트는 가장 오래 이야기한 주제와 갈린 주제, 같은 선택을 센다`() {
        val a = Choice("부먹", "🥣"); val b = Choice("찍먹", "🥢")
        val h = highlightsOf(listOf(
            RoundResult(1, a, b, 0, 2, 0, talkSec = 30),
            RoundResult(2, a, b, -1, 1, 1, talkSec = 95),
            RoundResult(3, a, b, 1, 1, 2, talkSec = 5),
            RoundResult(4, a, b, 0, 1, 0, talkSec = 0),
        ))
        assertEquals(2, h.longest!!.round)
        assertEquals(2, h.split)
        assertEquals(1, h.agreed)
        assertTrue(highlightsOf(listOf(RoundResult(1, a, b, -1, 0, 0))).isEmpty)
    }

    @Test fun `다음이라고 하면 바로 다음 주제로 간다`() {
        toDiscussion()
        listening!!.invoke("다음 주제로 넘어가자")
        assertTrue(spoken.last().startsWith("네, 다음 주제로"))
        finishSpeech()
        assertEquals(2, game.view.round)
        assertEquals(Phase.ASKING, game.view.phase)
    }

    @Test fun `대화 속 다음이나 그만은 명령으로 받지 않는다`() {
        toDiscussion()
        listening!!.invoke("다음에 또 거기 가자")
        listening!!.invoke("이제 그만 먹어야지")
        assertEquals(Phase.DISCUSS, game.view.phase)
        assertEquals(1, game.view.round)
    }

    @Test fun `이야기 중에 그만이라고 하면 끝내고 이야기 시간을 기록한다`() {
        toDiscussion()
        repeat(5) { game.tick() }
        listening!!.invoke("그만하자")
        assertEquals(Phase.FINISHED, game.view.phase)
        assertEquals(5, records.single().results.single().talkSec)
    }

    @Test fun `화면의 다음 주제 버튼으로 넘어간다`() {
        toDiscussion()
        game.skip()
        assertEquals(2, game.view.round)
    }

    @Test fun `이야기 중에 멈췄다가 재개하면 말을 걸지 않고 이어서 기다린다`() {
        toDiscussion()
        repeat(4) { game.tick() }
        game.pause("방향 전환 중이라 잠시 멈출게요")
        assertNull(listening)
        val asked = spoken.size
        game.resume()
        assertEquals(Phase.DISCUSS, game.view.phase)
        assertEquals(asked, spoken.size)
        assertNotNull(listening)
        assertEquals(4, game.view.talkSec)
    }

    @Test fun `의견이 갈리면 서로 이유를 말해 보게 한다`() {
        game.start(GameConfig(players = 2))
        finishSpeech(); game.vote(0); game.vote(1)
        assertTrue(game.view.followUp.startsWith("의견이 갈렸네요"))
    }

    @Test fun `마이크를 쓸 수 없으면 시간을 정해 두고 이야기하게 한다`() {
        micProblem = "마이크 권한이 꺼져 있어요"
        toDiscussion()
        assertEquals(BalanceGame.NO_MIC_TALK_SEC, game.view.talkLeft)
        repeat(BalanceGame.NO_MIC_TALK_SEC - 1) { game.tick() }
        assertEquals(Phase.DISCUSS, game.view.phase)
        assertEquals(1, game.view.talkLeft)
        game.tick(); finishSpeech()
        assertEquals(2, game.view.round)
    }

    @Test fun `이야기를 나누면 끝날 때 나눈 주제 수를 말한다`() {
        game.start(GameConfig(players = 1))
        repeat(ROUNDS_PER_GAME) {
            finishSpeech(); game.vote(0); finishSpeech()
            listening!!.invoke("그건 말이야")
            repeat(BalanceGame.SILENCE_SEC) { game.tick() }
            finishSpeech()
        }
        assertEquals(Phase.FINISHED, game.view.phase)
        assertTrue(game.view.comment.contains("가지 주제로"))
        assertTrue(records.single().results.all { it.talkSec == BalanceGame.SILENCE_SEC })
    }
}

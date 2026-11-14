package com.gleoplay.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.gleoplay.app.car.SignalHub
import com.gleoplay.app.core.BalanceGame
import com.gleoplay.app.core.BalancePair
import com.gleoplay.app.core.DrivingMode
import com.gleoplay.app.core.DrivingSnapshot
import com.gleoplay.app.core.GameRecord
import com.gleoplay.app.core.GameView
import com.gleoplay.app.core.GateDecision
import com.gleoplay.app.core.PausePolicy
import com.gleoplay.app.core.Phase
import com.gleoplay.app.core.SafetyGate
import com.gleoplay.app.core.Scheduler
import com.gleoplay.app.data.QuestionLoader
import com.gleoplay.app.data.Store
import com.gleoplay.app.voice.VoiceIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GleoPlayApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this).also { it.start() }
    }
}

/** Handler 기반 지연 실행 */
class HandlerScheduler(private val h: Handler) : Scheduler {
    private val token = Any()
    override fun after(ms: Long, block: () -> Unit) { h.postAtTime(block, token, SystemClock.uptimeMillis() + ms) }
    override fun cancelAll() = h.removeCallbacksAndMessages(token)
}

/** 앱 전체 객체: 차량 신호 → 안전 게이트 → 밸런스 게임 */
class AppGraph(app: Application) {
    private val main = Handler(Looper.getMainLooper())
    private val ctx = app.applicationContext
    val store = Store(app)
    val signals = SignalHub(app)
    private val gate = SafetyGate()

    private val _game = MutableStateFlow(GameView())
    val game: StateFlow<GameView> = _game.asStateFlow()
    private val _decision = MutableStateFlow<GateDecision>(GateDecision.Parked)
    val decision: StateFlow<GateDecision> = _decision.asStateFlow()
    private val _records = MutableStateFlow<List<GameRecord>>(emptyList())
    val records: StateFlow<List<GameRecord>> = _records.asStateFlow()
    private val _region = MutableStateFlow<String?>(null)
    /** 현재 지역(위치 기반 문구·특별 게임) */
    val region: StateFlow<String?> = _region.asStateFlow()

    lateinit var voice: VoiceIO
        private set
    lateinit var engine: BalanceGame
        private set
    var bank: List<BalancePair> = emptyList()
        private set

    private var drowsyNotified = false
    private var interruptedAtMs = Long.MIN_VALUE / 2

    private val ticker = object : Runnable {
        override fun run() { evaluate(); engine.tick(); main.postDelayed(this, 1_000) }
    }

    fun start() {
        // 다른 음성(내비 안내·Gleo 호출·전화)에 오디오 포커스를 뺏기면 즉시 멈추고, 그 음성이 끝날 시간을 준다
        voice = VoiceIO(ctx, demoMode = BuildConfig.DEMO_TOOLS) {
            interruptedAtMs = SystemClock.elapsedRealtime()
            engine.pause("다른 음성 안내가 나와서 잠시 멈출게요")
        }
        bank = QuestionLoader.load(ctx)
        engine = BalanceGame(
            bank, voice, voice, HandlerScheduler(main),
            onChange = { _game.value = it },
            onFinished = { store.addRecord(it); _records.value = store.records() },
            onPlayed = { store.addRecent(it) },
        )
        _records.value = store.records()
        _region.value = store.region
        // JVM 화면 테스트(Robolectric)에는 Pleos 차량 서비스가 없으므로 연결하지 않는다
        if (android.os.Build.FINGERPRINT != "robolectric") {
            voice.start()
            signals.start()
        }
        main.post(ticker)
        if (BuildConfig.DEMO_TOOLS) registerDemoCommands()
    }

    /**
     * 시연 촬영용 숨은 명령(demo·debug 빌드에만). 화면에 시연 도구를 띄우지 않고 adb로 조작한다.
     * 예) adb shell am broadcast -a com.gleoplay.app.DEMO -p com.gleoplay.app --es cmd recorded --es arg first
     * cmd: recorded <first|second|both|unsure|skip|stop> · vote <0|1> · region <이름|none> · players <n> · wait <초> · kid <NONE|PRESCHOOL|ELEMENTARY|TEEN> · talk <TALK|GAME_ONLY> · start · skip · stop
     */
    private fun registerDemoCommands() {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                val arg = i.getStringExtra("arg")
                when (i.getStringExtra("cmd")) {
                    "recorded" -> playRecorded(arg ?: "first")
                    "vote" -> engine.vote(arg?.toIntOrNull() ?: 0)
                    "region" -> setRegion(arg?.takeIf { it != "none" })
                    "players" -> store.players = arg?.toIntOrNull() ?: 2
                    "wait" -> store.waitSec = arg?.toIntOrNull() ?: 15
                    "kid" -> store.kidBand = runCatching { com.gleoplay.app.core.KidBand.valueOf(arg ?: "NONE") }.getOrDefault(com.gleoplay.app.core.KidBand.NONE)
                    "start" -> startGame(arg)
                    "stop" -> engine.stop()
                    "skip" -> engine.skip()
                    "mictest" -> voice.micTest()
                    "reshuffle" -> store.clearRecent()   // 나온 주제 기록 지우기(처음부터 다시 섞기)
                    "talk" -> store.talkMode = runCatching { com.gleoplay.app.core.TalkMode.valueOf(arg ?: "TALK") }.getOrDefault(com.gleoplay.app.core.TalkMode.TALK)
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            ctx, receiver, android.content.IntentFilter("com.gleoplay.app.DEMO"), androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun setRegion(r: String?) { store.region = r; _region.value = r }

    /** 시연 도구: 녹음한 대답(PCM)을 실제 STT에 넣는다. 녹음 파일은 demo·debug 빌드에만 들어 있다 */
    fun playRecorded(name: String): Boolean {
        val pcm = runCatching { ctx.assets.open("answers/$name.pcm").use { it.readBytes() } }.getOrNull() ?: return false
        return voice.recognizeAudio(pcm)
    }

    fun startGame(category: String?) = engine.start(store.config(category))

    /** 지금 설정(아이 연령·카테고리)에서 아직 한 번도 안 나온 주제 수 */
    fun freshCount(category: String?): Int =
        com.gleoplay.app.core.QuestionPool.freshCount(bank, store.kidBand, category, store.recent)

    fun clearRecords() { store.clearRecords(); _records.value = emptyList() }

    /** 1초마다: 차량 상황에 따라 게임을 멈추거나 다시 이어 간다 */
    fun evaluate() {
        signals.publish()
        val snap: DrivingSnapshot = signals.snapshot.value
        val d = gate.evaluate(snap, SystemClock.elapsedRealtime())
        _decision.value = d
        if (!snap.driverAttentionOn) drowsyNotified = false
        if (!engine.running) return

        val now = SystemClock.elapsedRealtime()
        val reason = PausePolicy.pauseReason(d, snap.driverAttentionOn, store.drivingMode, store.drowsyLink)
        if (reason != null) {
            if (engine.view.phase != Phase.PAUSED) {
                engine.pause(reason)
                if (snap.driverAttentionOn && store.drowsyLink && !drowsyNotified) {
                    drowsyNotified = true
                    voice.speak("졸음 주의 신호가 켜졌어요. 게임은 잠시 멈추고, 가까운 휴게소에서 쉬었다 가요.") {}
                }
            } else {
                engine.updatePauseReason(reason)
            }
        } else if (engine.view.phase == Phase.PAUSED && PausePolicy.canResume(d, reason, now, interruptedAtMs)) {
            engine.resume()
        }
    }
}

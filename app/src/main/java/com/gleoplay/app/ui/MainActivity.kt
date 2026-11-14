package com.gleoplay.app.ui

import android.Manifest
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gleoplay.app.AppGraph
import com.gleoplay.app.BuildConfig
import com.gleoplay.app.GleoPlayApp
import com.gleoplay.app.car.DemoOverrides
import com.gleoplay.app.core.ADULT_CATEGORY
import com.gleoplay.app.core.Difficulty
import com.gleoplay.app.core.GateDecision
import com.gleoplay.app.core.Phase
import com.gleoplay.app.core.WheelButton

enum class Screen { MAIN, MODE, SETTINGS, RECORDS, GAME }

class MainActivity : ComponentActivity() {
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val graph get() = (application as GleoPlayApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
        setContent { GleoTheme { Root(graph) } }
    }

    /** 스티어링 휠 버튼(미디어 키): 응답 / 건너뛰기 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (graph.store.wheelButton == WheelButton.ANSWER_SKIP && graph.engine.running) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> { graph.engine.skip(); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { graph.engine.vote(0); return true }
                KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { graph.engine.vote(1); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Root(graph: AppGraph) {
    val game by graph.game.collectAsStateWithLifecycle()
    val decision by graph.decision.collectAsStateWithLifecycle()
    val records by graph.records.collectAsStateWithLifecycle()
    val region by graph.region.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.MAIN) }
    var category by remember { mutableStateOf<String?>(null) }
    var demoOpen by remember { mutableStateOf(false) }
    var players by remember { mutableIntStateOf(graph.store.players) }
    var difficulty by remember { mutableStateOf(graph.store.difficulty) }
    var kid by remember { mutableStateOf(graph.store.kidBand) }
    val parked = decision is GateDecision.Parked

    LaunchedEffect(game.phase) { if (game.phase != Phase.IDLE && game.phase != Phase.FINISHED) screen = Screen.GAME }

    Box(Modifier.fillMaxSize().background(GP.Bg)) {
        Column(Modifier.fillMaxSize()) {
            // 앱 상단: 로고(시연 빌드에서는 길게 눌러 시연 도구)
            Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Gleo Play", color = androidx.compose.ui.graphics.Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { if (BuildConfig.DEMO_TOOLS) demoOpen = !demoOpen }),
                )
                Spacer(Modifier.width(14.dp))
                Text("AI와 함께하는 밸런스 게임", color = GP.Sub, fontSize = 20.sp)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!parked) {
                    // 주행 중: 상태 한 줄 + 정지 버튼만(게임은 음성으로 이어 감)
                    DrivingPanel(game, onStop = { graph.engine.stop() })
                } else when (screen) {
                    Screen.MAIN -> MainScreen(region, category, kid, graph.freshCount(category), { category = it }, onStart = { graph.startGame(category) }) { screen = it }
                    Screen.MODE -> ModeScreen(graph) {
                        players = graph.store.players; difficulty = graph.store.difficulty; kid = graph.store.kidBand
                        if (kid.hasKid && category == ADULT_CATEGORY) category = null
                        screen = Screen.MAIN
                    }
                    Screen.SETTINGS -> SettingsScreen(graph) { screen = Screen.MAIN }
                    Screen.RECORDS -> RecordsScreen(records, { screen = Screen.MAIN }, { graph.clearRecords() })
                    Screen.GAME -> GameScreen(
                        game,
                        onVote = { graph.engine.vote(it) },
                        onSkip = { graph.engine.skip() },
                        onExit = { graph.engine.stop(); screen = Screen.MAIN },
                        onRestart = { graph.startGame(category) },
                    )
                }
            }
        }
        if (BuildConfig.DEMO_TOOLS && demoOpen) DemoDrawer(graph, Modifier.align(Alignment.CenterEnd)) { demoOpen = false }
    }
}

/** 시연 도구의 녹음 대답(assets/answers 폴더의 pcm, 16kHz·16bit·mono) */
private val RECORDED = linkedMapOf(
    "“첫 번째요”" to "first", "“두 번째요”" to "second", "“둘 다!”" to "both",
    "“음… 몰라요”" to "unsure", "“다음 거”" to "skip", "“그만할래”" to "stop",
)

/** 시연 도구(배포 빌드에 없음): 위치·주행·대답을 흉내 낸다 */
@Composable
fun DemoDrawer(graph: AppGraph, modifier: Modifier, onClose: () -> Unit) {
    var d by remember { mutableStateOf(graph.signals.demo) }
    val region by graph.region.collectAsStateWithLifecycle()
    fun set(n: DemoOverrides) { d = n; graph.signals.demo = n; graph.evaluate() }
    Column(
        modifier.width(440.dp).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xF2101426))
            .border(1.dp, GP.Line).padding(22.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("시연 도구 (배포 빌드에 없음)", color = GP.Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("현재 위치(위치 기반 특별 게임)", color = GP.Sub, fontSize = 18.sp)
        Grid(listOf("없음", "강원도", "제주도", "부산", "전라도", "충청도"), region ?: "없음") { graph.setRegion(if (it == "없음") null else it) }
        Text("기어", color = GP.Sub, fontSize = 18.sp)
        Grid(listOf("실제", "P", "D"), when (d.parked) { null -> "실제"; true -> "P"; false -> "D" }) {
            set(d.copy(parked = when (it) { "P" -> true; "D" -> false; else -> null }))
        }
        Text("가족의 대답 넣기(마이크 대신)", color = GP.Sub, fontSize = 18.sp)
        Grid(listOf("A 선택", "B 선택", "건너뛰기"), "") {
            when (it) { "A 선택" -> graph.engine.vote(0); "B 선택" -> graph.engine.vote(1); else -> graph.engine.skip() }
        }
        Text("녹음한 대답 → 실제 음성 인식(STT)", color = GP.Sub, fontSize = 18.sp)
        Grid(RECORDED.keys.toList(), "") { graph.playRecorded(RECORDED.getValue(it)) }
        Text("주행 상황", color = GP.Sub, fontSize = 18.sp)
        Grid(listOf("급제동(2초)", "내비 안내", "졸음 주의"), listOfNotNull(
            "내비 안내".takeIf { d.naviSpeaking }, "졸음 주의".takeIf { d.driverAttention },
        ).firstOrNull() ?: "") {
            when (it) {
                "급제동(2초)" -> {
                    set(d.copy(hardBrake = true))
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ set(graph.signals.demo.copy(hardBrake = false)) }, 2_000)
                }
                "내비 안내" -> set(d.copy(naviSpeaking = !d.naviSpeaking))
                else -> set(d.copy(driverAttention = !d.driverAttention))
            }
        }
        Spacer(Modifier.height(6.dp))
        Pill("닫기", filled = false, modifier = Modifier.fillMaxWidth(), onClick = onClose)
    }
}

@Composable
private fun Grid(options: List<String>, selected: String, onClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { o ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (o == selected) GP.Purple else GP.CardHi)
                            .clickable { onClick(o) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(o, color = androidx.compose.ui.graphics.Color.White, fontSize = 19.sp) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

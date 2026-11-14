package com.gleoplay.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gleoplay.app.AppGraph
import com.gleoplay.app.core.ADULT_CATEGORY
import com.gleoplay.app.core.Difficulty
import com.gleoplay.app.core.KidBand
import com.gleoplay.app.core.DrivingMode
import com.gleoplay.app.core.GameRecord
import com.gleoplay.app.core.Language
import com.gleoplay.app.core.ProgressMode
import com.gleoplay.app.core.TalkMode
import com.gleoplay.app.core.TalkStyle
import com.gleoplay.app.core.WAIT_OPTIONS
import com.gleoplay.app.core.WheelButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val CATEGORIES = listOf("음식", "여행", "취미", "일상", "연애")
private val CAT_EMOJI = mapOf("음식" to "🍔", "여행" to "🧳", "취미" to "🎨", "일상" to "☕", "연애" to "💜", "지역" to "📍")

// ───────────────────────── ① Main ─────────────────────────
@Composable
fun MainScreen(region: String?, category: String?, kid: KidBand, fresh: Int, onCategory: (String?) -> Unit, onStart: () -> Unit, onGo: (Screen) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GleoAvatar(96.dp)
            Spacer(Modifier.width(28.dp))
            Column {
                if (region != null) {
                    Text("📍 현재 ${region}에 있어요!", color = GP.PurpleSoft, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("${region} 특별 게임으로 시작해 볼까요?", color = GP.Text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("안녕하세요! 저는 Gleo예요", color = GP.PurpleSoft, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("밸런스 게임으로 가족 이야기를 시작해 볼까요?", color = GP.Text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        Row(Modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            MenuCard("🎮", "게임 시작", "고르고, 이야기 나누기 · 10개 주제", Modifier.weight(1f), primary = true, onClick = onStart)
            MenuCard("🎛️", "게임 모드", "난이도 · 인원 수 · 진행 방식", Modifier.weight(1f)) { onGo(Screen.MODE) }
            MenuCard("🏆", "게임 기록", "지난 게임 다시 보기", Modifier.weight(1f)) { onGo(Screen.RECORDS) }
            MenuCard("⚙️", "설정", "음성 · 주행 · 조작", Modifier.weight(1f)) { onGo(Screen.SETTINGS) }
        }
        Spacer(Modifier.height(40.dp))
        Text("추천 카테고리", color = GP.Sub, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CATEGORIES.forEach { c ->
                val locked = kid.hasKid && c == ADULT_CATEGORY
                if (locked) Pill("🔒 $c", filled = false)
                else Pill("${CAT_EMOJI[c]} $c", filled = c == category) { onCategory(if (c == category) null else c) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                kid.hasKid -> "아이(${kid.label})와 함께라 나이에 맞는 질문부터 내요 · ‘연애’는 잠겼어요"
                category == null -> "카테고리를 고르지 않으면 골고루 섞어서 내요"
                else -> "‘$category’ 질문으로 시작해요"
            } + if (fresh > 0) " · 아직 안 나온 주제 ${fresh}개" else " · 준비한 주제를 다 봤어요. 오래전에 나온 것부터 다시 나와요",
            color = GP.Dim, fontSize = 19.sp,
        )
    }
}

@Composable
private fun MenuCard(icon: String, title: String, desc: String, modifier: Modifier, primary: Boolean = false, onClick: () -> Unit) {
    NavyCard(modifier.fillMaxSize(), selected = primary, onClick = onClick) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)).background(if (primary) GP.Gleo else androidx.compose.ui.graphics.SolidColor(GP.CardHi)),
                contentAlignment = Alignment.Center,
            ) { Text(icon, fontSize = 42.sp) }
            Column {
                Text(title, color = GP.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(desc, color = GP.Sub, fontSize = 20.sp)
            }
        }
    }
}

// ───────────────────────── ② 게임 모드 ─────────────────────────
@Composable
fun ModeScreen(graph: AppGraph, onBack: () -> Unit) {
    val s = graph.store
    var diff by remember { mutableStateOf(s.difficulty) }
    var players by remember { mutableIntStateOf(s.players) }
    var kid by remember { mutableStateOf(s.kidBand) }
    var progress by remember { mutableStateOf(s.progress) }
    var talk by remember { mutableStateOf(s.talkMode) }
    var wait by remember { mutableIntStateOf(s.waitSec) }
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
        ScreenHeader("게임 모드", onBack)
        NavyCard(Modifier.fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingRow("난이도", "질문이 얼마나 고민될지 정해요") {
                    Segmented(Difficulty.entries, diff, { it.label }) { diff = it; s.difficulty = it }
                }
                Divider()
                SettingRow("인원", "함께 대답할 사람 수예요. 모두 대답하면 결과가 나와요") {
                    Stepper(players, "명", 1, 6) { players = it; s.players = it }
                }
                Divider()
                SettingRow("함께 탄 아이", if (kid.hasKid) "순한맛: 나이에 맞는 질문만, ‘연애’는 잠겨요" else "가장 어린 아이 기준으로 골라 주세요") {
                    Segmented(KidBand.entries, kid, { it.label }) { kid = it; s.kidBand = it }
                }
                Divider()
                SettingRow("진행 방식", "선택 유지: 이긴 쪽이 남아 새 도전자와 겨뤄요") {
                    Segmented(ProgressMode.entries, progress, { it.label }) { progress = it; s.progress = it }
                }
                Divider()
                SettingRow("대화 방식", "이야기 나누기: 결과 뒤에 이어지는 질문을 던지고, 가족이 이야기하는 동안 기다려요") {
                    Segmented(TalkMode.entries, talk, { it.label }) { talk = it; s.talkMode = it }
                }
                Divider()
                SettingRow("대기 시간", "대답을 기다리는 시간이에요") {
                    Segmented(WAIT_OPTIONS, wait, { "${it}초" }) { wait = it; s.waitSec = it }
                }
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(GP.Line))

// ───────────────────────── ③ 설정 ─────────────────────────
@Composable
fun SettingsScreen(graph: AppGraph, onBack: () -> Unit) {
    val s = graph.store
    var voice by remember { mutableStateOf(s.voiceGuide) }
    var style by remember { mutableStateOf(s.style) }
    var lang by remember { mutableStateOf(s.language) }
    var drive by remember { mutableStateOf(s.drivingMode) }
    var drowsy by remember { mutableStateOf(s.drowsyLink) }
    var wheel by remember { mutableStateOf(s.wheelButton) }
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
        ScreenHeader("설정", onBack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            NavyCard(Modifier.weight(1f)) {
                Column {
                    Text("🎙️ 음성 및 대화", color = GP.PurpleSoft, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    SettingRow("음성 안내", "질문과 결과를 소리로 읽어 줘요") { Toggle(voice) { voice = it; s.voiceGuide = it } }
                    Divider()
                    SettingRow("Gleo 대화 스타일") { Picker(TalkStyle.entries, style, { it.label }) { style = it; s.style = it } }
                    Divider()
                    SettingRow("언어") { Picker(Language.entries, lang, { it.label }) { lang = it; s.language = it } }
                }
            }
            NavyCard(Modifier.weight(1f)) {
                Column {
                    Text("🚗 주행 및 조작", color = GP.PurpleSoft, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    SettingRow("주행 중 진행") { Picker(DrivingMode.entries, drive, { it.label }) { drive = it; s.drivingMode = it } }
                    Divider()
                    SettingRow("졸음 감지 연동", "주의 경고가 켜지면 게임을 멈추고 휴식을 권해요") { Toggle(drowsy) { drowsy = it; s.drowsyLink = it } }
                    Divider()
                    SettingRow("스티어링 휠 버튼") { Picker(WheelButton.entries, wheel, { it.label }) { wheel = it; s.wheelButton = it } }
                }
            }
        }
    }
}

// ───────────────────────── ④ 게임 기록 ─────────────────────────
@Composable
fun RecordsScreen(records: List<GameRecord>, onBack: () -> Unit, onClear: () -> Unit) {
    val now = remember { Calendar.getInstance() }
    var year by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(now.get(Calendar.MONTH)) } // 0..11
    val cal = Calendar.getInstance()
    val inMonth = records.filter { cal.timeInMillis = it.timeMs; cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month }
    val byDay = inMonth.groupBy { SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(Date(it.timeMs)) }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
        ScreenHeader("게임 기록", onBack) {
            if (records.isNotEmpty()) Pill("기록 지우기", filled = false, onClick = onClear)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${year}년", color = GP.Sub, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(28.dp))
            MonthArrow("<") { if (month == 0) { month = 11; year-- } else month-- }
            Text("${month + 1}월", color = GP.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp), textAlign = TextAlign.Center)
            MonthArrow(">") { if (month == 11) { month = 0; year++ } else month++ }
            Spacer(Modifier.weight(1f))
            Text("이번 달 ${inMonth.size}게임", color = GP.Text, fontSize = 22.sp)
        }
        Spacer(Modifier.height(20.dp))
        if (byDay.isEmpty()) {
            NavyCard(Modifier.fillMaxWidth().height(320.dp)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗂️", fontSize = 64.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("이 달에는 게임 기록이 없어요", color = GP.Text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text("게임을 끝까지 하면 여기에 쌓여요", color = GP.Dim, fontSize = 20.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                byDay.forEach { (day, list) ->
                    item { Text(day, color = GP.PurpleSoft, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                    items(list) { RecordRow(it) }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(t: String, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(GP.CardHi).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(t, color = GP.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecordRow(r: GameRecord) {
    val wins = r.results.filter { it.winner >= 0 }.map { if (it.winner == 0) it.a else it.b }
    NavyCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(r.timeMs)), color = GP.Sub, fontSize = 22.sp, modifier = Modifier.width(150.dp))
            Column(Modifier.weight(1f)) {
                val title = listOfNotNull(r.region?.let { "📍 $it 특별" }, r.category?.let { "${CAT_EMOJI[it]} $it" }).ifEmpty { listOf("🎲 골고루") }.joinToString(" · ")
                Text(title, color = GP.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    wins.take(4).joinToString("  ") { "${it.emoji} ${it.label}" }.ifEmpty { "결정된 승자 없음" },
                    color = GP.Sub, fontSize = 20.sp, maxLines = 1,
                )
                val best = highlightLines(com.gleoplay.app.core.highlightsOf(r.results))
                if (best.isNotEmpty()) Text(best.joinToString("   "), color = GP.PurpleSoft, fontSize = 18.sp, maxLines = 1)
            }
            val talk = r.results.sumOf { it.talkSec }
            Column(horizontalAlignment = Alignment.End) {
                if (talk > 0) Text("💬 ${talkTime(talk)} 이야기", color = GP.PurpleSoft, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("${r.results.size}/10 · ${r.difficulty.label} · ${r.players}명", color = GP.Dim, fontSize = 20.sp)
            }
        }
    }
}

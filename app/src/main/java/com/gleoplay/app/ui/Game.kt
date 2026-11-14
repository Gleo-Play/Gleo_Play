package com.gleoplay.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gleoplay.app.core.Choice
import com.gleoplay.app.core.GameView
import com.gleoplay.app.core.Highlights
import com.gleoplay.app.core.highlightsOf
import com.gleoplay.app.core.Phase

// ───────────────────────── ⑤ 게임 화면 ─────────────────────────
@Composable
fun GameScreen(g: GameView, onVote: (Int) -> Unit, onSkip: () -> Unit, onExit: () -> Unit, onRestart: () -> Unit) {
    if (g.phase == Phase.FINISHED) { FinishView(g, onExit, onRestart); return }
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // 상단: 진행 pill + 진행 점 10개 + 왕관
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(GP.CardHi).clickable(onClick = onExit), contentAlignment = Alignment.Center) {
                Text("✕", color = GP.Text, fontSize = 30.sp)
            }
            Spacer(Modifier.width(20.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(GP.Purple).padding(horizontal = 26.dp, vertical = 10.dp)) {
                Text("${g.round}/${g.total}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(22.dp))
            ProgressDots(g.round, g.total, 18.dp)
            Spacer(Modifier.weight(1f))
            if (g.region != null) Pill("📍 ${g.region}", filled = false)
            Spacer(Modifier.width(14.dp))
            Text("👑", fontSize = 44.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            when {
                g.phase == Phase.DISCUSS -> "이야기 나눠 볼까요?"
                g.movingOn -> "다음 주제로 넘어갈게요"
                g.phase == Phase.RESULT && g.followUp.isNotEmpty() -> "이 주제로 이야기해 봐요"
                g.phase == Phase.RESULT && g.winnerSide == null -> "이번 라운드는 무승부!"
                g.phase == Phase.RESULT -> "이번 라운드 승자는!"
                else -> g.question
            },
            color = GP.Text, fontSize = 46.sp, fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            ChoiceCard(g.a, g, side = 0, Modifier.weight(1f).fillMaxHeight(), onVote)
            Box(Modifier.padding(horizontal = 26.dp).size(110.dp).clip(CircleShape).background(GP.Gleo), contentAlignment = Alignment.Center) {
                Text("VS", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
            }
            ChoiceCard(g.b, g, side = 1, Modifier.weight(1f).fillMaxHeight(), onVote)
        }
        Spacer(Modifier.height(22.dp))
        BottomStatus(g, onSkip)
    }
}

@Composable
fun ProgressDots(round: Int, total: Int, size: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(size / 2)) {
        repeat(total) { i ->
            val c = when {
                i < round - 1 -> GP.PurpleSoft
                i == round - 1 -> GP.Purple
                else -> GP.CardHi
            }
            Box(Modifier.size(if (i == round - 1) size * 1.3f else size).clip(CircleShape).background(c))
        }
    }
}

@Composable
private fun ChoiceCard(c: Choice?, g: GameView, side: Int, modifier: Modifier, onVote: (Int) -> Unit) {
    val decided = g.phase == Phase.RESULT || g.phase == Phase.DISCUSS
    val won = decided && g.winnerSide == side
    val lost = decided && g.winnerSide != null && g.winnerSide != side
    val shape = RoundedCornerShape(32.dp)
    val tint = if (side == 0) listOf(Color(0xFF2B3A78), Color(0xFF1B2140)) else listOf(Color(0xFF4B2A78), Color(0xFF1B2140))
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(tint))
            .border(if (won) 8.dp else 1.dp, if (won) GP.Purple else GP.Line, shape)
            .clickable(enabled = g.phase == Phase.ASKING || g.phase == Phase.WAITING) { onVote(side) }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 이미지 카드: 저작권 걱정 없는 이모지 일러스트
            Box(
                Modifier.size(260.dp).clip(RoundedCornerShape(28.dp)).background(Color(0x22FFFFFF)),
                contentAlignment = Alignment.Center,
            ) { Text(c?.emoji ?: "❔", fontSize = 150.sp) }
            Spacer(Modifier.height(22.dp))
            Text(c?.label ?: "", color = if (lost) GP.Dim else GP.Text, fontSize = 44.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            val votes = if (side == 0) g.votesA else g.votesB
            if (votes > 0 || decided) {
                Text("${votes}표", color = GP.PurpleSoft, fontSize = 26.sp)
            }
        }
        if (g.crownSide == side) Text("👑", fontSize = 48.sp, modifier = Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun BottomStatus(g: GameView, onSkip: () -> Unit) {
    NavyCard(Modifier.fillMaxWidth().height(if (g.followUp.isNotEmpty() && (g.phase == Phase.RESULT || g.phase == Phase.DISCUSS)) 190.dp else 150.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            GleoAvatar(80.dp)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                when (g.phase) {
                    Phase.ASKING -> Column {
                        Text("Gleo가 질문하고 있어요…", color = GP.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                        Text("바로 카드를 눌러도 돼요", color = GP.Sub, fontSize = 21.sp, maxLines = 1)
                    }
                    Phase.WAITING -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Waveform(active = true)
                        Spacer(Modifier.width(24.dp))
                        Column {
                            Text("지금, 답변을 기다리고 있어요...", color = GP.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                run {
                                    // 여럿이 대답할 때는 몇 명이 대답했는지 함께 보여 준다
                                    val who = if (g.players >= 2) " · ${g.answered}/${g.players}명 대답" else ""
                                    when {
                                        g.micNote != null -> "🎙️ ${g.micNote}$who · ${g.secondsLeft}초"
                                        g.heard != null -> "들은 말: “${g.heard}”$who · ${g.secondsLeft}초"
                                        g.players >= 2 -> "한 사람씩 말하거나 카드를 눌러 주세요$who · ${g.secondsLeft}초"
                                        else -> "카드를 누르거나 말로 대답해 주세요 · ${g.secondsLeft}초"
                                    }
                                },
                                color = if (g.micNote != null) GP.Gold else GP.Sub, fontSize = 21.sp, maxLines = 1,
                            )
                        }
                    }
                    Phase.RESULT -> if (g.movingOn) {
                        Text("💬 ${if (g.talkSec > 0) talkTime(g.talkSec) + " 동안 이야기했어요 · " else ""}다음 주제로 넘어갈게요", color = GP.PurpleSoft, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    } else Column {
                        Text(g.comment, color = if (g.followUp.isEmpty()) GP.Text else GP.PurpleSoft, fontSize = if (g.followUp.isEmpty()) 28.sp else 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        if (g.followUp.isNotEmpty()) Text(g.followUp, color = GP.Text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    }
                    Phase.DISCUSS -> Column {
                        Text(g.followUp, color = GP.Text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Spacer(Modifier.height(6.dp))
                        Text(discussLine(g), color = if (g.talking) GP.PurpleSoft else GP.Sub, fontSize = 21.sp, maxLines = 1)
                    }
                    Phase.PAUSED -> Text("⏸ ${g.pausedReason ?: "잠시 멈춤"}", color = GP.Gold, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    else -> Text("", color = GP.Text)
                }
            }
            when (g.phase) {
                Phase.ASKING, Phase.WAITING, Phase.RESULT -> Pill(if (g.followUp.isNotEmpty() && g.phase == Phase.RESULT) "다음 주제 ›" else "건너뛰기 ›", filled = false, onClick = onSkip)
                Phase.DISCUSS -> Pill("다음 주제 ›", filled = false, onClick = onSkip)
                else -> {}
            }
        }
    }
}

@Composable
private fun FinishView(g: GameView, onExit: () -> Unit, onRestart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        GleoAvatar(120.dp)
        Spacer(Modifier.height(20.dp))
        Text("게임 끝!", color = GP.Text, fontSize = 52.sp, fontWeight = FontWeight.Black)
        Text(g.comment, color = GP.Sub, fontSize = 28.sp, textAlign = TextAlign.Center)
        // 오늘의 하이라이트: 가장 오래 이야기한 주제, 의견이 갈린 주제, 모두 같은 선택
        val lines = highlightLines(highlightsOf(g.results))
        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { lines.forEach { Pill(it, filled = false) } }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            g.results.forEach { r ->
                val w = when (r.winner) { 0 -> r.a; 1 -> r.b; else -> null }
                NavyCard(Modifier.width(130.dp), selected = w != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("${r.round}R", color = GP.Dim, fontSize = 18.sp)
                        Text(w?.emoji ?: "🤝", fontSize = 44.sp)
                        Text(w?.label ?: "무승부", color = GP.Text, fontSize = 17.sp, maxLines = 1, textAlign = TextAlign.Center)
                        if (r.talkSec > 0) Text("💬 ${talkTime(r.talkSec)}", color = GP.PurpleSoft, fontSize = 15.sp, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Pill("한 판 더!", onClick = onRestart)
            Pill("메인으로", filled = false, onClick = onExit)
        }
    }
}


// ───────────────────────── ⑥ 주행 중 패널(3분할 오른쪽 1/3) ─────────────────────────
/**
 * 주행 중(기어 D) 화면 — 상태 한 줄 + 큰 정지 버튼 하나.
 * Pleos 심사(게임은 정차 중에만)와 도로교통법 제49조(운전 중 영상 표시·조작 금지)를 고려해
 * 이미지 카드·움직이는 파형·조작 버튼·진행 점은 보이지 않는다. 게임은 음성으로만 이어 간다.
 */
@Composable
fun DrivingPanel(g: GameView, onStop: () -> Unit) {
    val running = g.phase != Phase.IDLE && g.phase != Phase.FINISHED
    Row(Modifier.fillMaxSize()) {
        // 왼쪽 2/3: 내비 등 다른 앱 자리
        Box(Modifier.weight(2f).fillMaxHeight().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("주행 중에는 화면 없이 음성으로만 진행돼요", color = GP.Dim, fontSize = 24.sp)
        }
        Box(
            Modifier.weight(1f).fillMaxHeight().padding(18.dp).clip(RoundedCornerShape(28.dp))
                .background(GP.Card).border(1.dp, GP.Line, RoundedCornerShape(28.dp)).padding(28.dp)
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GleoAvatar(52.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("Gleo Play", color = GP.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                // 상태 한 줄(움직이지 않는 글자)
                val (line, color) = drivingStatus(g)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GP.CardHi).padding(vertical = 22.dp, horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(line, color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2) }
                Spacer(Modifier.weight(1f))
                if (running) {
                    // 큰 정지 버튼 하나(게임 끝내기). 그 밖의 조작은 정차 중에만
                    Box(
                        Modifier.size(140.dp).clip(CircleShape).background(GP.Red).clickable(onClick = onStop),
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)) }
                    Spacer(Modifier.height(12.dp))
                    Text("게임 끝내기 · 말로 \"그만\"도 돼요", color = GP.Sub, fontSize = 20.sp)
                } else {
                    Text("게임은 정차 중에 시작해요", color = GP.Sub, fontSize = 22.sp)
                }
            }
        }
    }
}

/** 주행 중 상태 한 줄 문구와 색 */
fun drivingStatus(g: GameView): Pair<String, Color> = when (g.phase) {
    Phase.ASKING -> "🔊 질문하는 중" to GP.Text
    Phase.WAITING -> "🎙 대답을 기다려요" to GP.PurpleSoft
    Phase.RESULT -> (if (g.movingOn) "🔊 다음 주제로 넘어가요" else if (g.followUp.isNotEmpty()) "🔊 이야깃거리를 드려요" else "🔊 결과를 알려 드려요") to GP.Text
    Phase.DISCUSS -> "💬 이야기 나누는 중" to GP.PurpleSoft
    Phase.PAUSED -> "⏸ ${g.pausedReason ?: "잠시 멈춤"}" to GP.Gold
    Phase.IDLE, Phase.FINISHED -> "주행 중이에요" to GP.Sub
}
/** 이야기 나누기 상태 한 줄 */
fun discussLine(g: GameView): String {
    val time = talkTime(g.talkSec)
    return when {
        g.talkLeft != null -> "💬 이야기 나누는 중 · ${g.talkLeft}초 뒤 다음 주제로 · 먼저 넘어가려면 \"다음 주제\""
        g.talking -> "💬 이야기 나누는 중 · $time · Gleo는 조용히 기다릴게요"
        g.talkSec == 0 -> "💬 편하게 이야기해 주세요 · 조용해지면 다음 주제로 넘어가요"
        else -> "💬 $time 째 · 조용해지면 다음 주제로, \"다음\"이라고 해도 돼요"
    }
}

/** 하이라이트를 화면에 보여 줄 짧은 문구로 */
fun highlightLines(h: Highlights): List<String> = listOfNotNull(
    h.longest?.let { "💬 가장 긴 이야기 · ${it.a.label} vs ${it.b.label} · ${talkTime(it.talkSec)}" },
    "🤔 의견이 갈린 주제 ${h.split}개".takeIf { h.split > 0 },
    "🤝 모두 같은 선택 ${h.agreed}개".takeIf { h.agreed > 0 },
)

/** 초 → "1분 20초" / "40초" */
fun talkTime(sec: Int): String = if (sec >= 60) "${sec / 60}분" + (if (sec % 60 > 0) " ${sec % 60}초" else "") else "${sec}초"
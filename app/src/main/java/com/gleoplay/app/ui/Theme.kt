package com.gleoplay.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** Gleo Play 색 — 강조 보라 / 카드 진남색 / 배경 #999999 → #000000 */
object GP {
    val Purple = Color(0xFF8B5CF6)
    val PurpleDeep = Color(0xFF6D28D9)
    val PurpleSoft = Color(0xFFC4B5FD)
    val Blue = Color(0xFF3B82F6)
    val Card = Color(0xFF1B2140)
    val CardHi = Color(0xFF262E57)
    val Line = Color(0xFF39437A)
    val Text = Color(0xFFF5F6FA)
    val Sub = Color(0xFFB4BAD1)
    val Dim = Color(0xFF7C84A3)
    val Gold = Color(0xFFFBBF24)
    val Red = Color(0xFFEF4444)
    val Bg = Brush.verticalGradient(listOf(Color(0xFF999999), Color(0xFF000000)))
    val Gleo = Brush.linearGradient(listOf(Purple, Blue))
}

@Composable
fun GleoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GP.Purple, background = Color.Black, surface = GP.Card,
            onPrimary = Color.White, onBackground = GP.Text, onSurface = GP.Text,
        ),
        content = content,
    )
}

/** Gleo 캐릭터 아바타(보라→파랑 원) */
@Composable
fun GleoAvatar(size: Dp = 72.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(GP.Gleo), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(size / 7)) {
            repeat(2) { Box(Modifier.size(width = size / 9, height = size / 5).clip(CircleShape).background(Color.White)) }
        }
    }
}

@Composable
fun NavyCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .clip(shape)
            .background(GP.Card)
            .border(if (selected) 4.dp else 1.dp, if (selected) GP.Purple else GP.Line, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(24.dp)
    ) { content() }
}

@Composable
fun Pill(text: String, filled: Boolean = true, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) GP.Purple else GP.CardHi)
            .border(1.dp, if (filled) GP.Purple else GP.Line, RoundedCornerShape(50))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 26.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = GP.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
}

/** 세그먼트 선택(쉬움/보통/어려움 등) */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(GP.CardHi).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) GP.Purple else Color.Transparent)
                    .clickable { onSelect(o) }
                    .padding(horizontal = 26.dp, vertical = 12.dp),
            ) { Text(label(o), color = if (on) Color.White else GP.Sub, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** − 2명 + */
@Composable
fun Stepper(value: Int, unit: String, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(GP.CardHi).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepBtn("−", value > min) { onChange(value - 1) }
        Text("$value$unit", color = GP.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center)
        StepBtn("+", value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(t: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) GP.Purple else GP.Card)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = if (enabled) Color.White else GP.Dim, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = on, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = GP.Purple, checkedThumbColor = Color.White, uncheckedTrackColor = GP.CardHi),
    )
}

/** 설정 한 줄: 제목·설명 / 오른쪽 컨트롤 */
@Composable
fun SettingRow(title: String, desc: String? = null, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = GP.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            if (desc != null) Text(desc, color = GP.Dim, fontSize = 19.sp)
        }
        control()
    }
}

/** 탭할 때마다 다음 값으로 바뀌는 선택 칸(= 드롭다운 대용) */
@Composable
fun <T> Picker(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(GP.CardHi).border(1.dp, GP.Line, RoundedCornerShape(14.dp))
            .clickable { onSelect(options[(options.indexOf(selected) + 1) % options.size]) }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label(selected), color = GP.Text, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Text("▾", color = GP.PurpleSoft, fontSize = 22.sp)
    }
}

/** 음성 파형(대답 대기 표시) */
@Composable
fun Waveform(active: Boolean, bars: Int = 18, height: Dp = 56.dp, color: Color = GP.PurpleSoft) {
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, 6.283f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "p")
    Row(Modifier.height(height), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(bars) { i ->
            val k = if (active) (0.35f + 0.65f * ((kotlin.math.sin(phase + i * 0.7f) + 1f) / 2f)) else 0.2f
            Box(Modifier.width(7.dp).fillMaxHeight(k).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, right: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(GP.CardHi).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = GP.Text, fontSize = 40.sp) }
        Spacer(Modifier.width(20.dp))
        Text(title, color = GP.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        right()
    }
}

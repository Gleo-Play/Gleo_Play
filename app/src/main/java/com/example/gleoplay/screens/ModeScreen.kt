package com.example.gleoplay.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.example.gleoplay.components.ChoicePills
import com.example.gleoplay.components.GradientButton
import com.example.gleoplay.components.PageTitle
import com.example.gleoplay.components.RoundAction
import com.example.gleoplay.components.SettingRow
import com.example.gleoplay.ui.theme.AppColors.White
import com.example.gleoplay.ui.theme.GleoPlayTheme

@Composable
fun ModeScreen(
    difficulty: String,
    onDifficulty: (String) -> Unit,
    players: Int,
    onPlayers: (Int) -> Unit,
    playStyle: String,
    onPlayStyle: (String) -> Unit,
    conversation: String,
    onConversation: (String) -> Unit,
    waitTime: String,
    onWaitTime: (String) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Home 화면과 동일한 1280 x 720 dp 기준으로 화면 비율에 맞춰 조정
        val scale = minOf(maxWidth / 1230.dp, maxHeight / 670.dp).coerceIn(0.72f, 1.35f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 50.dp,
                    vertical = 50.dp
                ),
            verticalArrangement = Arrangement.spacedBy(50.dp * scale)
        ) {
            PageTitle("게임 모드", onBack, scale)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp * scale))
                    .background(Color(0xFF15151D))
                    .padding(horizontal = 34.dp * scale, vertical = 18.dp * scale),
                verticalArrangement = Arrangement.spacedBy(10.dp * scale, Alignment.CenterVertically)
            ) {
                SettingRow("▥", "난이도", "게임의 난이도를 선택하세요", scale) {
                    ChoicePills(listOf("쉬움", "보통", "어려움"), difficulty, onDifficulty, scale)
                }
                SettingRow("♧", "인원", "플레이 인원을 선택하세요", scale) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp * scale)
                    ) {
                        RoundAction("−", scale) { onPlayers(players - 1) }
                        Text(
                            "${players}명",
                            color = White,
                            fontSize = 25.sp * scale,
                            fontWeight = FontWeight.Bold
                        )
                        RoundAction("+", scale) { onPlayers(players + 1) }
                    }
                }
                SettingRow("▷", "진행 방식", "플레이 방식을 선택하세요", scale) {
                    ChoicePills(listOf("선택 유지", "매번 새롭게"), playStyle, onPlayStyle, scale)
                }
                SettingRow("◖", "대화 방식", "Gleo와의 대화 방식을 선택하세요", scale) {
                    ChoicePills(listOf("질문만", "주고 받기"), conversation, onConversation, scale)
                }
                SettingRow("◴", "대기 시간", "보기를 선택하는 대기 시간을 정하세요", scale) {
                    ChoicePills(listOf("10초", "15초", "30초", "45초", "60초"), waitTime, onWaitTime, scale)
                }
            }

            GradientButton(
                "게임 시작",
                onStart,
                Modifier
                    .align(Alignment.End)
                    .width(220.dp * scale)
                    .height(62.dp * scale)
            )
        }
    }
}

@Preview(
    name = "Settings Screen",
    showBackground = true,
    widthDp = 1280,
    heightDp = 720
)
@Composable
fun ModeScreenPreview() {
    GleoPlayTheme {
        ModeScreen(
            difficulty = "보통",
            onDifficulty = {},

            players = 4,
            onPlayers = {},

            playStyle = "선택 유지",
            onPlayStyle = {},

            conversation = "주고 받기",
            onConversation = {},

            waitTime = "15초",
            onWaitTime = {},

            onBack = {},
            onStart = {}
        )
    }
}

package com.example.gleoplay.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gleoplay.R
import com.example.gleoplay.components.GradientButton
import com.example.gleoplay.components.PageTitle
import com.example.gleoplay.ui.theme.AppColors.White
import com.example.gleoplay.ui.theme.GleoPlayTheme

private val ModeBackground = Color(0xFF15151D)
private val ModePanel = Color(0xFF20202B)
private val ModePurple = Color(0xFF8500F5)
private val ModeUnselected = Color(0xFF393344)
private val ModeGray = Color(0xFFAAA6B5)

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
    onStart: () -> Unit,
    totalRounds: Int = 6,
    onTotalRounds: (Int) -> Unit = {}
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ModeBackground)
    ) {
        // 기준 해상도에 맞춰 화면 크기 조절
        val scale = (minOf(
            maxWidth / 1230.dp,
            maxHeight / 670.dp
        )).coerceIn(0.72f, 1.35f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 50.dp * scale,
                    vertical = 40.dp * scale
                ),
            verticalArrangement = Arrangement.spacedBy(65.dp * scale)
        ) {
            // 공통 컴포넌트: 뒤로가기 + 페이지 제목
            PageTitle(
                title = "게임 모드",
                onBack = onBack,
                scale = scale
            )

            // 설정 패널
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        color = ModePanel,
                        shape = RoundedCornerShape(28.dp * scale)
                    )
                    .padding(
                        horizontal = 38.dp * scale,
                        vertical = 18.dp * scale
                    ),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // 난이도
                ModeSettingRow(
                    icon = R.drawable.cellular,
                    title = "난이도",
                    description = "문제의 난이도를 선택하세요",
                    scale = scale
                ) {
                    ModeOptionGroup(
                        options = listOf(
                            ModeOption("쉬움", R.drawable.smile_face),
                            ModeOption("보통", R.drawable.commonly_face),
                            ModeOption("어려움", R.drawable.bad_face)
                        ),
                        selected = difficulty,
                        onSelect = onDifficulty,
                        scale = scale
                    )
                }

                ModeDivider()

                // 인원
                ModeSettingRow(
                    icon = R.drawable.groups,
                    title = "인원",
                    description = "함께 플레이할 인원 수",
                    scale = scale
                ) {
                    ModeCounter(
                        value = "${players}명",
                        onDecrease = {
                            if (players > 1) onPlayers(players - 1)
                        },
                        onIncrease = {
                            if (players < 8) onPlayers(players + 1)
                        },
                        scale = scale
                    )
                }

                ModeDivider()

                // 진행 방식
                ModeSettingRow(
                    icon = R.drawable.play,
                    title = "진행 방식",
                    description = "선택한 항목을 유지할지 결정합니다",
                    scale = scale
                ) {
                    ModeOptionGroup(
                        options = listOf(
                            ModeOption("선택 유지", R.drawable.bookmark),
                            ModeOption("매번 새롭게", R.drawable.refresh)
                        ),
                        selected = playStyle,
                        onSelect = onPlayStyle,
                        scale = scale
                    )
                }

                ModeDivider()

                // 라운드 수
                ModeSettingRow(
                    icon = R.drawable.scoreboard,
                    title = "라운드 수",
                    description = "한 게임에서 진행할 라운드 수",
                    scale = scale
                ) {
                    ModeCounter(
                        value = "${totalRounds}라운드",
                        onDecrease = {
                            if (totalRounds > 1) {
                                onTotalRounds(totalRounds - 1)
                            }
                        },
                        onIncrease = {
                            if (totalRounds < 20) {
                                onTotalRounds(totalRounds + 1)
                            }
                        },
                        scale = scale
                    )
                }

                ModeDivider()

                // 대기 시간
                ModeSettingRow(
                    icon = R.drawable.timer,
                    title = "대기 시간",
                    description = "선택할 수 있는 제한 시간",
                    scale = scale
                ) {
                    ModeOptionGroup(
                        options = listOf(
                            ModeOption("10초"),
                            ModeOption("15초"),
                            ModeOption("30초"),
                            ModeOption("45초"),
                            ModeOption("60초")
                        ),
                        selected = waitTime,
                        onSelect = onWaitTime,
                        scale = scale
                    )
                }
            }

            // 공통 컴포넌트: 게임 시작 버튼
//            Box(
//                modifier = Modifier.fillMaxWidth(),
//                contentAlignment = Alignment.CenterEnd
//            ) {
//                GradientButton(
//                    label = "게임 시작",
//                    onClick = onStart,
//                    modifier = Modifier
//                        .width(230.dp * scale)
//                        .height(62.dp * scale)
//                )
//            }
        }
    }
}

/**
 * 설정 항목의 왼쪽 아이콘과 설명,
 * 오른쪽 선택 컨트롤을 배치하는 공통 레이아웃
 */
@Composable
private fun ModeSettingRow(
    icon: Int,
    title: String,
    description: String,
    scale: Float,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp * scale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drawable 아이콘
        Box(
            modifier = Modifier
                .size(48.dp * scale)
                .background(
                    color = Color(0x00FFFFFF),
                    shape = RoundedCornerShape(12.dp * scale)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = title,
                modifier = Modifier.size(45.dp * scale)
            )
        }

        Spacer(modifier = Modifier.width(25.dp * scale))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = White,
                fontSize = 23.sp * scale,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(5.dp * scale))

            Text(
                text = description,
                color = ModeGray,
                fontSize = 14.sp * scale
            )
        }

        Box(
            modifier = Modifier.width(420.dp * scale),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

data class ModeOption(
    val title: String,
    val icon: Int? = null
)

/**
 * 선택형 옵션 버튼 그룹
 */
@Composable
private fun ModeOptionGroup(
    options: List<ModeOption>,
    selected: String,
    onSelect: (String) -> Unit,
    scale: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp * scale),
    ) {
        options.forEach { option ->
            val isSelected = option.title == selected

            Button(
                onClick = { onSelect(option.title) },
                modifier = Modifier
                    .height(
                        (55.dp) * scale
                    )
                    .weight(1f),
                shape = RoundedCornerShape(50.dp * scale),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) {
                        ModePurple
                    } else {
                        ModeUnselected
                    },
                    contentColor = White
                ),
                contentPadding = PaddingValues(8.dp * scale)
            ) {
                if (option.icon != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(option.icon),
                            contentDescription = null,
                            modifier = Modifier.size(25.dp * scale)
                        )

                        Spacer(
                            modifier = Modifier.width(10.dp * scale)
                        )

                        Text(
                            text = option.title,
                            fontSize = 15.sp * scale,
                            color = Color.White,
                            fontWeight = if (isSelected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Medium
                            },
                            maxLines = 1
                        )
                    }
                } else {
                    Text(
                        text = option.title,
                        fontSize = 15.sp * scale,
                        fontWeight = if (isSelected) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 인원 및 라운드 수 조절 컨트롤
 */
@Composable
private fun ModeCounter(
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    scale: Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp * scale)
    ) {
        CounterButton(
            icon = R.drawable.remove,
            description = "감소",
            onClick = onDecrease,
            scale = scale
        )

        Text(
            text = value,
            color = White,
            fontSize = 20.sp * scale,
            fontWeight = FontWeight.Bold
        )

        CounterButton(
            icon = R.drawable.add,
            description = "증가",
            onClick = onIncrease,
            scale = scale
        )
    }
}

@Composable
private fun CounterButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    scale: Float
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(46.dp * scale),
        shape = RoundedCornerShape(50.dp * scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = ModeUnselected,
            contentColor = White
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = description,
            modifier = Modifier.size(23.dp * scale),
            colorFilter = ColorFilter.tint(Color(0xFFFFFFFF))
        )
    }
}

@Composable
private fun ModeDivider() {
    HorizontalDivider(
        color = Color(0xFF383541),
        thickness = 1.dp
    )
}

@Preview(
    name = "Mode Screen",
    showBackground = true,
    widthDp = 1280,
    heightDp = 720
)
@Composable
fun ModeScreenPreview() {
    GleoPlayTheme {
        ModeScreen(
            difficulty = "쉬움",
            onDifficulty = {},
            players = 2,
            onPlayers = {},
            playStyle = "선택 유지",
            onPlayStyle = {},
            conversation = "주고 받기",
            onConversation = {},
            waitTime = "15초",
            onWaitTime = {},
            totalRounds = 6,
            onTotalRounds = {},
            onBack = {},
            onStart = {}
        )
    }
}

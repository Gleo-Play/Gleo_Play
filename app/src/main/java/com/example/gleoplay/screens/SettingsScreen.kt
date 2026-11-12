package com.example.gleoplay.screens

import android.R.attr.icon
import android.R.id.icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.example.gleoplay.components.PageTitle
import com.example.gleoplay.ui.theme.AppColors.Lavender
import com.example.gleoplay.ui.theme.AppColors.Panel
import com.example.gleoplay.ui.theme.AppColors.Panel2
import com.example.gleoplay.ui.theme.AppColors.White
import com.example.gleoplay.ui.theme.GleoPlayTheme
import com.example.gleoplay.R

@Composable
fun SettingsScreen(
    voiceGuide: Boolean,
    onVoiceGuide: (Boolean) -> Unit,
    sleepyLink: Boolean,
    onSleepyLink: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var conversationStyle by remember { mutableStateOf("자연스럽게") }
    var language by remember { mutableStateOf("자동 감지") }
    var drivingMode by remember { mutableStateOf("음성 전용") }
    var conversationMode by remember { mutableStateOf("주고 받기") }
    var steeringButton by remember { mutableStateOf("응답 / 건너뛰기") }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = minOf(
            maxWidth / 1230.dp,
            maxHeight / 670.dp
        ).coerceIn(0.72f, 1.35f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 50.dp * scale,
                    vertical = 40.dp * scale
                ),
            verticalArrangement = Arrangement.spacedBy(65.dp * scale)
        ) {
            PageTitle("설정", onBack, scale)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(24.dp * scale)
            ) {
                SettingsGroup(
                    title = "음성 및 대화",
                    modifier = Modifier.weight(1f),
                    scale = scale
                ) {
                    ToggleRow(
                        icon = painterResource(R.drawable.speaker),
                        title = "음성 안내",
                        checked = voiceGuide,
                        onChecked = onVoiceGuide,
                        scale = scale
                    )

                    DropdownRow(
                        icon = painterResource(R.drawable.chat),
                        title = "Gleo 대화 스타일",
                        value = conversationStyle,
                        options = listOf("자연스럽게", "간결하게", "친근하게"),
                        onSelected = { conversationStyle = it },
                        scale = scale
                    )

                    DropdownRow(
                        icon = painterResource(R.drawable.globe),
                        title = "언어",
                        value = language,
                        options = listOf("자동 감지", "한국어", "English"),
                        onSelected = { language = it },
                        scale = scale
                    )

                    DropdownRow(
                        icon = painterResource(R.drawable.voice),
                        title = "대화 방식",
                        value = conversationMode,
                        options = listOf("주고 받기", "자유 대화", "간단 응답"),
                        onSelected = { conversationMode = it },
                        scale = scale
                    )
                }

                SettingsGroup(
                    title = "주행 및 조작",
                    modifier = Modifier.weight(1f),
                    scale = scale
                ) {
                    DropdownRow(
                        icon = painterResource(R.drawable.handle),
                        title = "주행 중 진행",
                        value = drivingMode,
                        options = listOf("음성 전용", "화면 및 음성", "진행 안 함"),
                        onSelected = { drivingMode = it },
                        scale = scale
                    )

                    ToggleRow(
                        icon = painterResource(R.drawable.snooze),
                        title = "졸음 감지 연동",
                        checked = sleepyLink,
                        onChecked = onSleepyLink,
                        scale = scale
                    )

                    DropdownRow(
                        icon = painterResource(R.drawable.button),
                        title = "스티어링 휠 버튼",
                        value = steeringButton,
                        options = listOf(
                            "응답 / 건너뛰기",
                            "응답 / 종료",
                            "음성 안내"
                        ),
                        onSelected = { steeringButton = it },
                        scale = scale
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    scale: Float,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp * scale))
            .background(Panel)
            .padding(
                horizontal = 30.dp * scale,
                vertical = 50.dp * scale
            ),
        verticalArrangement = Arrangement.spacedBy(28.dp * scale)
    ) {
        Text(
            text = title,
            color = Lavender,
            fontSize = 30.sp * scale,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp * scale))

        content()
    }
}

@Composable
private fun ToggleRow(
    icon: Painter,
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    scale: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp * scale),
        verticalAlignment = Alignment.CenterVertically
    ) {
//        Text(
//            text = icon,
//            color = Lavender,
//            fontSize = 28.sp * scale,
//            modifier = Modifier.width(42.dp * scale)
//        )
        // ✨ 기존 Text 대신 Icon 사용 (기존 42.dp 너비 영역 유지)
        Box(
            modifier = Modifier.width(42.dp * scale),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                painter = icon,
                contentDescription = title,
                tint = Color(0xFFBD93FF), // 기존 Lavender 색상 또는 원하시는 색상
                modifier = Modifier.size(28.dp * scale) // 기존 28.sp와 비슷한 크기
            )
        }

        Text(
            text = title,
            color = White,
            fontSize = 23.sp * scale,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = onChecked
        )
    }
}

@Composable
private fun DropdownRow(
    icon: Painter,
    title: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    scale: Float
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp * scale),
        verticalAlignment = Alignment.CenterVertically
    ) {
//        Text(
//            text = icon,
//            color = Lavender,
//            fontSize = 27.sp * scale,
//            modifier = Modifier.width(42.dp * scale)
//        )

        // ✨ 기존 Text 대신 Icon 사용 (기존 42.dp 너비 영역 유지)
        Box(
            modifier = Modifier.width(42.dp * scale),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                painter = icon,
                contentDescription = title,
                tint = Color(0xFFBD93FF), // 기존 Lavender 색상 또는 원하시는 색상
                modifier = Modifier.size(28.dp * scale) // 기존 28.sp와 비슷한 크기
            )
        }

        Text(
            text = title,
            color = White,
            fontSize = 23.sp * scale,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Box {
            TextButton(
                onClick = { expanded = true },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = Panel2,
                    contentColor = White
                ),
                shape = RoundedCornerShape(18.dp * scale),
                contentPadding = PaddingValues(
                    horizontal = 16.dp * scale,
                    vertical = 8.dp * scale
                )
            ) {
                Text(
                    text = "$value",
                    fontSize = 18.sp * scale,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(15.dp * scale))

                Icon(
                    painter = painterResource(id = R.drawable.arrow_down),
                    contentDescription = "드롭다운 열기", // 필수 파라미터 (장식용이면 null 입력 가능)
                    tint = White, // 버튼 글자색과 통일 (원본 이미지 색상을 유지하려면 Color.Unspecified 입력)
                    modifier = Modifier.size(18.dp * scale) // 텍스트 크기에 맞춘 화살표 크기
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
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
fun SettingsScreenPreview() {
    GleoPlayTheme {
        SettingsScreen(
            voiceGuide = true,
            onVoiceGuide = {},
            sleepyLink = false,
            onSleepyLink = {},
            onBack = {}
        )
    }
}

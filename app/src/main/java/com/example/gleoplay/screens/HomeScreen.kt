package com.example.gleoplay.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.gleoplay.app.GleoPlayApp
import com.example.gleoplay.components.*
import com.example.gleoplay.ui.theme.AppColors.White
import com.example.gleoplay.ui.theme.GleoPlayTheme
import com.example.gleoplay.R

@Composable
fun HomeScreen(
    onStart: () -> Unit,
    onMode: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val horizontalPadding = 50.dp
        val tileSpacing = 25.dp

        // 현재 선택된 메뉴
        var selectedMenu by remember {
            mutableStateOf<String?>(null)
        }

        // 기존보다 메뉴 크기를 약 20% 축소
        val availableTileWidth =
            (maxWidth - horizontalPadding * 2 - tileSpacing * 3) / 4

        val tileSize = minOf(
            availableTileWidth * 0.82f,
            maxHeight * 0.29f
        )

        val categoryHeight = (maxHeight * 0.18f)
            .coerceIn(115.dp, 140.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = horizontalPadding,
                    vertical = 50.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // 앱 타이틀
            Text(
                text = "Gleo Play",
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF168BFF),
                            Color(0xFF8B43FF),
                            Color(0xFFC18BFF)
                        )
                    )
                )
            )

            // 메인 안내 문구
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "안녕하세요, Gleo Play입니다.",
                    color = White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(25.dp))

                Text(
                    text = "오늘은 어떤 특별한 게임을 해볼까요?",
                    color = Color(0xFFCECBD8),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // 메뉴 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeActionCard(
                    icon = Icons.Default.VideogameAsset,
                    title = "게임 시작",
                    description = "지금 바로 시작하기",
                    onClick = {
                        selectedMenu = "게임 시작"
                        onStart()
                    },
                    modifier = Modifier.size(tileSize),
                    highlighted = selectedMenu == "게임 시작"
                )

                HomeActionCard(
                    icon = Icons.Default.Tune,
                    title = "게임 모드",
                    description = "난이도, 인원 수 등 설정",
                    onClick = {
                        selectedMenu = "게임 모드"
                        onMode()
                    },
                    modifier = Modifier.size(tileSize),
                    highlighted = selectedMenu == "게임 모드"
                )

                HomeActionCard(
                    icon = Icons.Default.BarChart,
                    title = "게임 기록",
                    description = "지난 기록을 확인하세요",
                    onClick = {
                        selectedMenu = "게임 기록"
                        onHistory()
                    },
                    modifier = Modifier.size(tileSize),
                    highlighted = selectedMenu == "게임 기록"
                )

                HomeActionCard(
                    icon = Icons.Default.Settings,
                    title = "설정",
                    description = "환경을 변경하세요",
                    onClick = {
                        selectedMenu = "설정"
                        onSettings()
                    },
                    modifier = Modifier.size(tileSize),
                    highlighted = selectedMenu == "설정"
                )
            }

            // 추천 카테고리
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "추천 카테고리",
                    color = White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                val categories = listOf(
                    "음식" to R.drawable.food,
                    "여행" to R.drawable.travel,
                    "취미" to R.drawable.hobby,
                    "일상" to R.drawable.life,
                    "연애" to R.drawable.love,
                    "가족" to R.drawable.family,
                    "랜덤" to R.drawable.random
                )

                val colors = listOf(
                    Color(0xFF65513F),
                    Color(0xFF344C68),
                    Color(0xFF66574C),
                    Color(0xFF65503F),
                    Color(0xFF70536B),
                    Color(0xFF62533F),
                    Color(0xFF344C68)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(categoryHeight),
                    horizontalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    categories.forEachIndexed { index, (name, image) ->
                        CategoryCard(
                            title = name,
                            color = colors[index],
                            imageRes = image,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Gleo Play App",
    showBackground = true,
    widthDp = 1280,
    heightDp = 720
)
@Composable
fun HomeScreenPreview() {
    GleoPlayTheme {
        GleoPlayApp()
    }
}
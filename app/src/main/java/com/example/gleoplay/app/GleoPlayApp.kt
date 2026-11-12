package com.example.gleoplay.app

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.gleoplay.data.model.samplePairs
import com.example.gleoplay.ui.theme.AppColors
import com.example.gleoplay.screens.*
import com.example.gleoplay.ui.theme.GleoPlayTheme

// 현재 앱에서 표시할 화면
private enum class Screen {
    HOME, MODE, GAME, RESULT, SETTINGS
}

@Composable
fun GleoPlayApp() {

    // 현재 표시 중인 화면
    var screen by remember {
        mutableStateOf(Screen.HOME)
    }

    // 게임 설정 상태
    var difficulty by remember { mutableStateOf("쉬움") }
    var players by remember { mutableIntStateOf(2) }
    var playStyle by remember { mutableStateOf("선택 유지") }
    var conversation by remember { mutableStateOf("질문만") }
    var waitTime by remember { mutableStateOf("15초") }

    // 게임 진행 상태
    var round by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }

    // 앱 설정 상태
    var voiceGuide by remember { mutableStateOf(true) }
    var sleepyLink by remember { mutableStateOf(true) }

    // 전체 화면 배경
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {

        // 현재 화면에 따라 해당 Screen을 표시
        when (screen) {

            Screen.HOME -> HomeScreen(
                onStart = {
                    screen = Screen.GAME
                    round = 0
                    selected = -1
                },
                onMode = {
                    screen = Screen.MODE
                },
                onSettings = {
                    screen = Screen.SETTINGS
                },
                onHistory = {
                    screen = Screen.RESULT
                }
            )

            Screen.MODE -> ModeScreen(
                difficulty = difficulty,
                onDifficulty = { difficulty = it },
                players = players,
                onPlayers = { players = it.coerceIn(1, 8) },
                playStyle = playStyle,
                onPlayStyle = { playStyle = it },
                conversation = conversation,
                onConversation = { conversation = it },
                waitTime = waitTime,
                onWaitTime = { waitTime = it },
                onBack = { screen = Screen.HOME },
                onStart = {
                    round = 0
                    selected = -1
                    screen = Screen.GAME
                }
            )

            Screen.GAME -> GameScreen(
                pair = samplePairs[round % samplePairs.size],
                round = round + 1,
                total = 10,
                selected = selected,
                onSelect = { selected = it },
                onContinue = {
                    if (round >= 9) {
                        screen = Screen.RESULT
                    } else {
                        round++
                        selected = -1
                    }
                }
            )

            Screen.RESULT -> HistoryScreen(
                onBack = { screen = Screen.HOME },
//                onAgain = {
//                    round = 0
//                    selected = -1
//                    screen = Screen.GAME
//                }
            )

            Screen.SETTINGS -> SettingsScreen(
                voiceGuide = voiceGuide,
                onVoiceGuide = { voiceGuide = it },
                sleepyLink = sleepyLink,
                onSleepyLink = { sleepyLink = it },
                onBack = { screen = Screen.HOME }
            )
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
fun GleoPlayAppPreview() {
    GleoPlayTheme {
        GleoPlayApp()
    }
}
package com.example.gleoplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.gleoplay.app.GleoPlayApp
import com.example.gleoplay.ui.theme.GleoPlayTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Jetpack Compose UI 실행
        setContent {
            // 앱 전체에 공통 테마 적용
            GleoPlayTheme {
                GleoPlayApp()
            }
        }
    }
}
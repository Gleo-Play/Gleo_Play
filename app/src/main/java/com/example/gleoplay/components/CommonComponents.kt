package com.example.gleoplay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.gleoplay.ui.theme.AppColors.Purple
import com.example.gleoplay.ui.theme.AppColors.White

@Composable
fun PageTitle(title: String, onBack: () -> Unit, scale: Float = 1f) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp * scale)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(54.dp * scale)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "뒤로",
                tint = White,
                modifier = Modifier.size(32.dp * scale)
            )
        }
        Text(
            title,
            color = White,
            fontSize = 38.sp * scale,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GradientButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Purple, Color(0xFF5A00B5))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
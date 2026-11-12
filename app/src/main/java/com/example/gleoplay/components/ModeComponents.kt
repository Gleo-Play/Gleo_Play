package com.example.gleoplay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.gleoplay.ui.theme.AppColors.Lavender
import com.example.gleoplay.ui.theme.AppColors.Muted
import com.example.gleoplay.ui.theme.AppColors.Panel2
import com.example.gleoplay.ui.theme.AppColors.Purple
import com.example.gleoplay.ui.theme.AppColors.White

@Composable
fun ColumnScope.SettingRow(
    icon: String,
    title: String,
    description: String,
    scale: Float,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp * scale)
    ) {
        Text(
            icon,
            color = Lavender,
            fontSize = 38.sp * scale,
            modifier = Modifier.width(52.dp * scale),
            textAlign = TextAlign.Center
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp * scale)
        ) {
            Text(title, color = White, fontSize = 26.sp * scale, fontWeight = FontWeight.Bold)
            Text(description, color = Muted, fontSize = 18.sp * scale)
        }
        Box(
            modifier = Modifier.widthIn(min = 390.dp * scale),
            contentAlignment = Alignment.CenterEnd
        ) {
            trailing()
        }
    }
}

@Composable
fun ChoicePills(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    scale: Float
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp * scale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val active = option == selected
            Text(
                option,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (active) Brush.horizontalGradient(listOf(Purple, Color(0xFF5D00B8)))
                        else Brush.horizontalGradient(listOf(Color(0xFF353047), Color(0xFF353047)))
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 20.dp * scale, vertical = 14.dp * scale),
                color = White,
                fontSize = 19.sp * scale,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun RoundAction(label: String, scale: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp * scale)
            .clip(CircleShape)
            .background(Panel2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = White, fontSize = 28.sp * scale)
    }
}

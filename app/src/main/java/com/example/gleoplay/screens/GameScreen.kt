package com.example.gleoplay.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.gleoplay.components.*
import com.example.gleoplay.ui.theme.AppColors.Blue
import com.example.gleoplay.ui.theme.AppColors.Lavender
import com.example.gleoplay.ui.theme.AppColors.Muted
import com.example.gleoplay.ui.theme.AppColors.Panel
import com.example.gleoplay.ui.theme.AppColors.Purple
import com.example.gleoplay.ui.theme.AppColors.White
import com.example.gleoplay.data.model.GamePair
import com.example.gleoplay.ui.theme.GleoPlayTheme

@Composable
fun GameScreen(
    pair: GamePair,
    round: Int,
    total: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    onContinue: () -> Unit
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gleo Play", color = Purple, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF4B008B),
                                Color(0xFF77717C)
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$round / $total",
                    color = White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(12.dp))
                repeat(total) { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (index < round) Lavender else Color.LightGray)
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("♛", color = Lavender, fontSize = 30.sp)
        Text(
            if (selected == -1) pair.question else "이번 라운드 승자는!",
            color = White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChoiceCard(
                pair.left,
                pair.leftDescription,
                selected == 0,
                selected == 1,
                Modifier.weight(1f)
            ) { onSelect(0) }
            Text(
                "VS",
                color = Purple,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            ChoiceCard(
                pair.right,
                pair.rightDescription,
                selected == 1,
                selected == 0,
                Modifier.weight(1f)
            ) { onSelect(1) }
        }
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Panel),
            contentAlignment = Alignment.Center
        ) {
            if (selected == -1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "▂ ▄ ▆ █ ▆ ▄ ▂",
                        color = Blue,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("보기를 선택해 주세요", color = Muted, fontSize = 12.sp)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (selected == 0) pair.left else pair.right,
                        color = White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("선택이 완료되었습니다", color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (selected != -1) {
            Spacer(Modifier.height(10.dp))
            GradientButton(
                "다음 라운드", onContinue, Modifier
                    .width(190.dp)
                    .height(44.dp)
            )
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    description: String,
    isSelected: Boolean,
    isDimmed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.42f)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        if (title == "감자") listOf(Color(0xFFBCA17D), Color(0xFF67513C))
                        else listOf(Color(0xFFE3C06E), Color(0xFF79572A))
                    )
                )
                .then(
                    if (isSelected) Modifier.border(
                        4.dp,
                        Brush.linearGradient(listOf(Blue, Purple)),
                        RoundedCornerShape(22.dp)
                    )
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(if (title == "감자") "🥔" else if (title == "옥수수") "🌽" else "✨", fontSize = 64.sp)
            if (isDimmed) Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            color = if (isDimmed) Muted else White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(description, color = Muted, fontSize = 11.sp)
    }
}

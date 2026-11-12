package com.example.gleoplay.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.gleoplay.ui.theme.AppColors.White

@Composable
fun HomeActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (highlighted)
                    Color(0xFF252036)
                else
                    Color(0xFF191820)
            )
            .then(
                if (highlighted) {
                    Modifier.border(
                        3.dp,
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF168BFF),
                                Color(0xFF9B4DFF)
                            )
                        ),
                        RoundedCornerShape(26.dp)
                    )
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFFB98AFF),
            modifier = Modifier.size(58.dp)
        )

        Spacer(Modifier.height(35.dp))

        Text(
            text = title,
            color = White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(15.dp))

        Text(
            text = description,
            color = Color(0xFFAAA4B8),
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun CategoryCard(
    title: String,
    color: Color,
    imageRes: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
    ){
        // 카테고리 배경 이미지
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0x99000000))
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

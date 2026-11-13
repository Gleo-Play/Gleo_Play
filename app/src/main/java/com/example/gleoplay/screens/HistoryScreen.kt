
package com.example.gleoplay.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.example.gleoplay.R
import com.example.gleoplay.components.PageTitle
import com.example.gleoplay.ui.theme.AppColors.Lavender
import com.example.gleoplay.ui.theme.AppColors.Panel
import com.example.gleoplay.ui.theme.AppColors.Panel2
import com.example.gleoplay.ui.theme.AppColors.White
import com.example.gleoplay.ui.theme.GleoPlayTheme

// 기록 데이터 모델
data class HistoryRecord(
    val id: Int,
    val category: String,
    val rounds: Int,
    val difficulty: String,
    val players: Int,
    val playMode: String,
    val winner: String?,
    val selectedChoice: String,
    val date: String,
    val time: String,
    val icon: String
)

// 임시 기록 데이터
private val sampleHistoryRecords = listOf(
    HistoryRecord(
        1, "음식", 10, "보통", 2, "선택 유지",
        "감자", "치킨", "오늘", "오후 7:42", "🍗"
    ),
    HistoryRecord(
        2, "여행", 10, "보통", 3, "매번 새롭게",
        null, "삿포로(홋카이도)", "11월 4일", "오후 6:31", "🏝️"
    ),
    HistoryRecord(
        3, "취미", 10, "어려움", 2, "매번 새롭게",
        null, "노래 듣기", "11월 2일", "오후 3:11", "🎧"
    ),
    HistoryRecord(
        4, "음식", 10, "쉬움", 1, "선택 유지",
        "치킨", "치킨", "10월 31일", "오후 7:46", "🍗"
    )
)

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    records: List<HistoryRecord> = sampleHistoryRecords,
    onRecordClick: (HistoryRecord) -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf("전체") }
    var sortOrder by remember { mutableStateOf("최근순") }
    var sortExpanded by remember { mutableStateOf(false) }

    val categories = listOf(
        "전체", "음식", "여행", "취미",
        "일상", "연애", "가족", "랜덤"
    )

    // 선택한 카테고리로 필터링
    val filteredRecords = remember(records, selectedCategory, sortOrder) {
        val filtered = if (selectedCategory == "전체") {
            records
        } else {
            records.filter { it.category == selectedCategory }
        }

        when (sortOrder) {
            "오래된순" -> filtered.reversed()
            else -> filtered
        }
    }

    // 통계 계산
    val totalPlays = records.size
    val totalRounds = records.sumOf { it.rounds }

    val mostCategory = records
        .groupingBy { it.category }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key ?: "-"

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
            verticalArrangement = Arrangement.spacedBy(50.dp * scale)
        ) {
            PageTitle("게임 기록", onBack, scale)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp * scale))
                    .background(Panel)
                    .padding(
                        horizontal = 30.dp * scale,
                        vertical = 26.dp * scale
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp * scale)
            ) {

                // 통계 영역
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    HistoryStat(
                        title = "총 플레이",
                        value = "$totalPlays",
                        unit = "회",
                        modifier = Modifier.weight(1f),
                        scale = scale
                    )

                    VerticalDivider(
                        modifier = Modifier
                            .height(65.dp * scale)
                            .padding(
                                horizontal = 50.dp
                            ),
                        color = Lavender.copy(alpha = 0.6f)
                    )

                    HistoryStat(
                        title = "선택한 라운드",
                        value = "$totalRounds",
                        unit = "개",
                        modifier = Modifier.weight(1f),
                        scale = scale
                    )

                    VerticalDivider(
                        modifier = Modifier
                            .height(65.dp * scale)
                            .padding(
                                horizontal = 50.dp
                            ),
                        color = Lavender.copy(alpha = 0.6f)
                    )

                    HistoryStat(
                        title = "가장 많이 고른 카테고리",
                        value = mostCategory,
                        unit = "",
                        modifier = Modifier.weight(1.2f),
                        scale = scale
                    )

                    Box {
                        TextButton(
                            onClick = { sortExpanded = true },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = Panel2,
                                contentColor = White
                            )
                        ) {
                            Text(
                                "$sortOrder",
                                fontSize = 18.sp * scale
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
                            expanded = sortExpanded,
                            onDismissRequest = {
                                sortExpanded = false
                            }
                        ) {
                            listOf("최근순", "오래된순").forEach {
                                DropdownMenuItem(
                                    text = { Text(it) },
                                    onClick = {
                                        sortOrder = it
                                        sortExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 카테고리 필터
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp * scale)
                ) {
                    categories.forEach { category ->
                        CategoryFilterChip(
                            title = category,
                            selected = selectedCategory == category,
                            scale = scale,
                            onClick = {
                                selectedCategory = category
                            }
                        )
                    }
                }

                HorizontalDivider(
                    color = Lavender.copy(alpha = 0.6f)
                )

                // 기록 목록
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp * scale)
                ) {
                    items(
                        items = filteredRecords,
                        key = { it.id }
                    ) { record ->
                        HistoryItem(
                            record = record,
                            scale = scale,
                            onClick = {
                                onRecordClick(record)
                            }
                        )
                    }

                    if (filteredRecords.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp * scale),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "기록이 없습니다.",
                                    color = White.copy(alpha = 0.5f),
                                    fontSize = 22.sp * scale
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 통계 항목
@Composable
private fun HistoryStat(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier,
    scale: Float
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp * scale)
    ) {
        Text(
            title,
            color = White.copy(alpha = 0.5f),
            fontSize = 19.sp * scale,
            fontWeight = FontWeight.Bold
        )

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = White,
                fontSize = 38.sp * scale,
                fontWeight = FontWeight.Bold
            )

            Text(
                unit,
                color = Lavender,
                fontSize = 20.sp * scale,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 5.dp * scale)
            )
        }
    }
}

// 카테고리 필터 버튼
@Composable
private fun CategoryFilterChip(
    title: String,
    selected: Boolean,
    scale: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color(0xFF7517D9) else Panel2)
            .clickable(onClick = onClick)
            .padding(
                horizontal = 17.dp * scale,
                vertical = 7.dp * scale
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = White,
            fontSize = 18.sp * scale,
            fontWeight = FontWeight.Bold
        )
    }
}

// 개별 기록 항목
@Composable
private fun HistoryItem(
    record: HistoryRecord,
    scale: Float,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp * scale))
            .clickable(onClick = onClick)
            .padding(
                horizontal = 5.dp * scale,
                vertical = 8.dp * scale
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 카테고리 이미지 영역
        Box(
            modifier = Modifier
                .size(48.dp * scale)
                .clip(RoundedCornerShape(12.dp * scale))
                .background(Panel2),
            contentAlignment = Alignment.Center
        ) {
            Text(
                record.icon,
                fontSize = 27.sp * scale
            )
        }

        Spacer(Modifier.width(24.dp * scale))

        // 게임 정보
        Column(
            modifier = Modifier.weight(1.3f),
            verticalArrangement = Arrangement.spacedBy(4.dp * scale)
        ) {
            Text(
                "${record.category}  ·  ${record.rounds}라운드",
                color = White,
                fontSize = 22.sp * scale,
                fontWeight = FontWeight.Bold
            )

            Text(
                "${record.difficulty}  ·  ${record.players}명  ·  ${record.playMode}",
                color = White.copy(alpha = 0.45f),
                fontSize = 16.sp * scale,
                fontWeight = FontWeight.Bold
            )
        }

        // 우승자 및 선택 결과
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (record.winner != null) {
                Text(
                    "♛ 우승",
                    color = Lavender,
                    fontSize = 17.sp * scale,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.width(12.dp * scale))
            }

            Text(
                text = record.winner ?: record.selectedChoice,
                color = White,
                fontSize = 22.sp * scale,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        // 날짜 및 시간
        Column(
            modifier = Modifier.width(110.dp * scale),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp * scale)
        ) {
            Text(
                record.date,
                color = White,
                fontSize = 20.sp * scale,
                fontWeight = FontWeight.Medium
            )

            Text(
                record.time,
                color = White.copy(alpha = 0.4f),
                fontSize = 15.sp * scale
            )
        }

        Spacer(Modifier.width(15.dp * scale))

        Icon(
            painter = painterResource(id = R.drawable.arrow_forward),
            contentDescription = "드롭다운 열기", // 필수 파라미터 (장식용이면 null 입력 가능)
            tint = Lavender, // 버튼 글자색과 통일 (원본 이미지 색상을 유지하려면 Color.Unspecified 입력)
            modifier = Modifier.size(24.dp * scale) // 텍스트 크기에 맞춘 화살표 크기
        )
    }
}

@Preview(
    name = "History Screen",
    showBackground = true,
    widthDp = 1280,
    heightDp = 720
)
@Composable
fun HistoryScreenPreview() {
    GleoPlayTheme {
        HistoryScreen(
            onBack = {}
        )
    }
}

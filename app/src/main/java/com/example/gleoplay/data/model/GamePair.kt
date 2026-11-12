package com.example.gleoplay.data.model

data class GamePair(
    val question: String,
    val left: String,
    val right: String,
    val leftDescription: String,
    val rightDescription: String
)

val samplePairs = listOf(
    GamePair("둘 중 더 좋아하는 것은?", "감자", "옥수수", "포슬포슬한 감자", "달콤한 옥수수"),
    GamePair("평생 하나만 먹을 수 있다면?", "치킨", "피자", "바삭한 치킨", "치즈 가득 피자"),
    GamePair("휴가를 떠난다면?", "바다", "산", "시원한 바닷바람", "조용한 숲속")
)

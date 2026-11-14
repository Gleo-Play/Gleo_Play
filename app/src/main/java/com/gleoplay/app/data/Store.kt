package com.gleoplay.app.data

import android.content.Context
import com.gleoplay.app.core.BalancePair
import com.gleoplay.app.core.Choice
import com.gleoplay.app.core.Difficulty
import com.gleoplay.app.core.DrivingMode
import com.gleoplay.app.core.GameConfig
import com.gleoplay.app.core.GameRecord
import com.gleoplay.app.core.KidBand
import com.gleoplay.app.core.Language
import com.gleoplay.app.core.ProgressMode
import com.gleoplay.app.core.RoundResult
import com.gleoplay.app.core.TalkMode
import com.gleoplay.app.core.TalkStyle
import com.gleoplay.app.core.WheelButton
import org.json.JSONArray
import org.json.JSONObject

/** assets/questions.json — 밸런스 게임 질문 */
object QuestionLoader {
    fun load(context: Context): List<BalancePair> =
        parse(context.assets.open("questions.json").bufferedReader().use { it.readText() })

    fun parse(json: String): List<BalancePair> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            BalancePair(
                id = o.getString("id"),
                category = o.optString("category", "지역"),
                level = when (o.optString("level")) { "쉬움" -> Difficulty.EASY; "어려움" -> Difficulty.HARD; else -> Difficulty.NORMAL },
                a = Choice(o.getString("a"), o.optString("ae", "🅰️")),
                b = Choice(o.getString("b"), o.optString("be", "🅱️")),
                region = o.optString("region").ifBlank { null },
                reaction = o.optString("reaction").ifBlank { null },
                minAge = o.optInt("minAge", 0),
                talk = o.optString("talk").ifBlank { null },
                band = o.optString("band").ifBlank { null }?.let { name -> KidBand.entries.firstOrNull { it.label == name } },
            )
        }
    }
}

/** 게임 모드·설정·기록 저장(기기 안에만) */
class Store(context: Context) {
    private val p = context.getSharedPreferences("gleoplay", Context.MODE_PRIVATE)

    // ── 게임 모드 ──
    var difficulty: Difficulty
        get() = enumOr(p.getString("difficulty", null), Difficulty.NORMAL)
        set(v) = p.edit().putString("difficulty", v.name).apply()
    var players: Int
        get() = p.getInt("players", 2)
        set(v) = p.edit().putInt("players", v.coerceIn(1, 6)).apply()
    var progress: ProgressMode
        get() = enumOr(p.getString("progress", null), ProgressMode.FRESH)
        set(v) = p.edit().putString("progress", v.name).apply()
    var talkMode: TalkMode
        get() = enumOr(p.getString("talkMode", null), TalkMode.TALK)
        set(v) = p.edit().putString("talkMode", v.name).apply()
    var waitSec: Int
        get() = p.getInt("waitSec", 15)
        set(v) = p.edit().putInt("waitSec", v).apply()

    /** 함께 탄 아이(순한맛). 아이 정보는 연령대 하나만 기기에 저장한다(이름·생일 없음) */
    var kidBand: KidBand
        get() = enumOr(p.getString("kidBand", null), KidBand.NONE)
        set(v) = p.edit().putString("kidBand", v.name).apply()

    /** 지금까지 나온 질문 id(오래된 것부터). 안 나온 질문을 다 낸 뒤에야 오래된 것부터 다시 낸다 */
    val recent: List<String> get() = p.getString("recent", "")!!.split(',').filter { it.isNotBlank() }

    /** 나온 주제 기록을 지운다(처음부터 다시 섞기) */
    fun clearRecent() = p.edit().remove("recent").apply()

    fun addRecent(id: String) {
        val list = p.getString("recent", "")!!.split(',').filter { it.isNotBlank() && it != id } + id
        p.edit().putString("recent", list.takeLast(RECENT_MAX).joinToString(",")).apply()
    }

    // ── 설정 ──
    var voiceGuide: Boolean
        get() = p.getBoolean("voiceGuide", true)
        set(v) = p.edit().putBoolean("voiceGuide", v).apply()
    var style: TalkStyle
        get() = enumOr(p.getString("style", null), TalkStyle.NATURAL)
        set(v) = p.edit().putString("style", v.name).apply()
    var language: Language
        get() = enumOr(p.getString("language", null), Language.AUTO)
        set(v) = p.edit().putString("language", v.name).apply()
    var drivingMode: DrivingMode
        get() = enumOr(p.getString("drivingMode", null), DrivingMode.VOICE_ONLY)
        set(v) = p.edit().putString("drivingMode", v.name).apply()
    var drowsyLink: Boolean
        get() = p.getBoolean("drowsyLink", true)
        set(v) = p.edit().putBoolean("drowsyLink", v).apply()
    var wheelButton: WheelButton
        get() = enumOr(p.getString("wheelButton", null), WheelButton.ANSWER_SKIP)
        set(v) = p.edit().putString("wheelButton", v.name).apply()

    /** 현재 지역(지금은 시연 도구에서 지정. 위치 연동 시 FusedLocation 으로 바꾼다) */
    var region: String?
        get() = p.getString("region", null)
        set(v) = p.edit().putString("region", v).apply()

    fun config(category: String?): GameConfig = GameConfig(
        difficulty = difficulty, players = players, progress = progress, talkMode = talkMode,
        waitSec = waitSec, category = category, region = region, style = style, voiceGuide = voiceGuide,
        kidBand = kidBand, recent = recent,
    )

    // ── 기록 ──
    fun records(): List<GameRecord> {
        val arr = JSONArray(p.getString("records", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val rs = o.getJSONArray("results")
            GameRecord(
                timeMs = o.getLong("t"),
                category = o.optString("category").ifBlank { null },
                region = o.optString("region").ifBlank { null },
                difficulty = enumOr(o.optString("difficulty"), Difficulty.NORMAL),
                players = o.optInt("players", 2),
                results = (0 until rs.length()).map { j ->
                    val r = rs.getJSONObject(j)
                    RoundResult(
                        r.getInt("round"), Choice(r.getString("a"), r.getString("ae")), Choice(r.getString("b"), r.getString("be")),
                        r.getInt("w"), r.getInt("va"), r.getInt("vb"),
                        talkSec = r.optInt("ts", 0), followUp = r.optString("fu"),
                    )
                },
            )
        }.sortedByDescending { it.timeMs }
    }

    fun addRecord(rec: GameRecord) {
        val arr = JSONArray(p.getString("records", "[]"))
        arr.put(JSONObject().apply {
            put("t", rec.timeMs); put("category", rec.category ?: ""); put("region", rec.region ?: "")
            put("difficulty", rec.difficulty.name); put("players", rec.players)
            put("results", JSONArray().apply {
                rec.results.forEach { r ->
                    put(JSONObject().apply {
                        put("round", r.round); put("a", r.a.label); put("ae", r.a.emoji); put("b", r.b.label); put("be", r.b.emoji)
                        put("w", r.winner); put("va", r.votesA); put("vb", r.votesB)
                        put("ts", r.talkSec); put("fu", r.followUp) // 이야기 시간·던진 질문만(들은 내용은 저장하지 않음)
                    })
                }
            })
        })
        p.edit().putString("records", arr.toString()).apply()
    }

    fun clearRecords() = p.edit().remove("records").apply()

    companion object { /** 질문 전체보다 넉넉히 커서, 사실상 나온 주제를 모두 기억한다 */
        const val RECENT_MAX = 2_000 }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, def: E): E =
        runCatching { enumValueOf<E>(name ?: "") }.getOrDefault(def)
}

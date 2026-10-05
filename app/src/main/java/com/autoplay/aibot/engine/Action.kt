package com.autoplay.aibot.engine

import org.json.JSONObject

enum class ActionType { TAP, SWIPE, HOLD, WAIT }

/**
 * Một hành động AI yêu cầu thực hiện. Tọa độ theo thang 0–1000
 * tương đối so với màn hình (x: trái→phải, y: trên→dưới).
 */
data class GameAction(
    val type: ActionType,
    val x: Int = 500,
    val y: Int = 500,
    val x1: Int = 0,
    val y1: Int = 0,
    val x2: Int = 0,
    val y2: Int = 0,
    val durationMs: Long = 500
) {
    companion object {
        private fun clamp(v: Int) = v.coerceIn(0, 1000)

        fun fromJson(o: JSONObject): GameAction? = try {
            when (o.optString("type").lowercase()) {
                "tap", "click", "press" -> GameAction(
                    ActionType.TAP,
                    x = clamp(o.optInt("x", 500)),
                    y = clamp(o.optInt("y", 500))
                )
                "swipe", "drag" -> GameAction(
                    ActionType.SWIPE,
                    x1 = clamp(o.optInt("x1", o.optInt("x", 500))),
                    y1 = clamp(o.optInt("y1", o.optInt("y", 500))),
                    x2 = clamp(o.optInt("x2", 500)),
                    y2 = clamp(o.optInt("y2", 500)),
                    durationMs = o.optLong("duration_ms", 500).coerceIn(100, 5000)
                )
                "hold", "longpress", "long_press" -> GameAction(
                    ActionType.HOLD,
                    x = clamp(o.optInt("x", 500)),
                    y = clamp(o.optInt("y", 500)),
                    durationMs = o.optLong("duration_ms", 1200).coerceIn(300, 10000)
                )
                "wait", "sleep", "delay" -> GameAction(
                    ActionType.WAIT,
                    durationMs = o.optLong("duration_ms", o.optLong("ms", 1000)).coerceIn(200, 15000)
                )
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** Kết quả phân tích một khung hình của Gemini. */
data class Analysis(
    val game: String,
    val state: String,
    val thought: String,
    val memory: String,
    val actions: List<GameAction>
)

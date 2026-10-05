package com.autoplay.aibot.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Gọi Google AI Studio (Gemini) để "nhìn" ảnh chụp màn hình game
 * và suy luận ra chuỗi hành động chơi tiếp theo.
 */
class GeminiClient(private val apiKey: String, private val model: String) {

    suspend fun analyze(imageBase64: String, memory: String, stuck: Boolean): Analysis =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                    .put(JSONObject().put("text", buildPrompt(memory, stuck)))
                    .put(JSONObject().put("inline_data", JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", imageBase64)))
                )))
                put("generationConfig", JSONObject()
                    .put("temperature", 0.3)
                    .put("maxOutputTokens", 1024)
                    .put("responseMimeType", "application/json"))
            }

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
                if (code !in 200..299) throw Exception("Gemini API lỗi $code: ${extractApiError(text)}")
                parseResponse(text)
            } finally {
                conn.disconnect()
            }
        }

    private fun buildPrompt(memory: String, stuck: Boolean): String {
        val stuckNote = if (stuck)
            "IMPORTANT: Your previous actions did NOT change the screen — you are STUCK. Try a DIFFERENT strategy: a different button, a swipe in another direction, closing a popup, or pressing back area."
        else "none"
        val mem = memory.ifBlank { "none yet" }
        return """
You are AutoPlay, an expert AI that plays ANY Android mobile game by looking at screenshots and deciding touch actions.

SCREEN: The image is an Android phone screenshot. Coordinates use a 0-1000 relative scale (x: left→right, y: top→bottom), independent of resolution.

MEMORY OF PREVIOUS TURNS: $mem
STUCK WARNING: $stuckNote

TASK:
1. Identify the game (title or genre) and describe the current screen in one line.
2. Infer the gameplay objective and decide the next best actions to progress: pass tutorials, press Play/Start, close popups/ads (tap X or "Close"), collect rewards, choose smart moves, navigate menus.

RULES:
- Output ONLY valid JSON, no markdown fences, no extra text.
- Maximum 6 actions per turn, ordered.
- Prefer taps on clear, unambiguous buttons. Avoid the system navigation bar area (y > 950).
- "swipe" needs x1,y1 (start), x2,y2 (end) and duration_ms (200-1200). Use it for scrolling, dragging items, aiming.
- "hold" is a long-press at x,y with duration_ms.
- "wait" pauses (duration_ms) to let animations/loadings finish.
- If a loading spinner or ad countdown is visible, usually "wait" is best; then re-evaluate.
- Keep "memory" to one short sentence worth remembering next turn.

JSON SCHEMA:
{"game":"name or genre","state":"one-line screen description","thought":"why these actions (short)","memory":"one-line memory","actions":[{"type":"tap","x":500,"y":800},{"type":"swipe","x1":200,"y1":500,"x2":800,"y2":500,"duration_ms":600},{"type":"hold","x":500,"y":500,"duration_ms":1500},{"type":"wait","duration_ms":1200}]}
""".trimIndent()
    }

    private fun parseResponse(text: String): Analysis {
        val root = JSONObject(text)
        val parts = root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
        val raw = StringBuilder()
        for (i in 0 until parts.length()) raw.append(parts.getJSONObject(i).optString("text"))
        var json = raw.toString().trim()
        // Phòng khi model vẫn bọc markdown
        if (json.startsWith("```")) {
            json = json.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }
        // Cắt lấy object JSON ngoài cùng nếu có chữ thừa
        val start = json.indexOf('{')
        val end = json.lastIndexOf('}')
        if (start >= 0 && end > start) json = json.substring(start, end + 1)

        val o = JSONObject(json)
        val actions = mutableListOf<GameAction>()
        val arr = o.optJSONArray("actions")
        if (arr != null) {
            for (i in 0 until minOf(arr.length(), 8)) {
                GameAction.fromJson(arr.optJSONObject(i) ?: continue)?.let { actions.add(it) }
            }
        }
        return Analysis(
            game = o.optString("game", "unknown").take(60),
            state = o.optString("state", "").take(140),
            thought = o.optString("thought", "").take(200),
            memory = o.optString("memory", "").take(200),
            actions = actions
        )
    }

    private fun extractApiError(text: String): String = try {
        JSONObject(text).optJSONObject("error")?.optString("message")?.take(160) ?: text.take(160)
    } catch (e: Exception) {
        text.take(160)
    }
}

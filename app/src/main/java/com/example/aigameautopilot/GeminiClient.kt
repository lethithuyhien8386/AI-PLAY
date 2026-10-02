package com.example.aigameautopilot

import android.graphics.Bitmap
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class GameAction(
    val type: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    val durationMs: Long = 100L,
    val waitMs: Long = 300L,
    val reason: String = ""
)

class GeminiClient(private val apiKey: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun decide(bitmap: Bitmap, goal: String): GameAction {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        val schema = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("type", JSONObject().put("type", "string"))
                .put("x", JSONObject().put("type", "number"))
                .put("y", JSONObject().put("type", "number"))
                .put("x2", JSONObject().put("type", "number"))
                .put("y2", JSONObject().put("type", "number"))
                .put("duration_ms", JSONObject().put("type", "integer"))
                .put("wait_ms", JSONObject().put("type", "integer"))
                .put("reason", JSONObject().put("type", "string")))
            .put("required", org.json.JSONArray()
                .put("type").put("x").put("y").put("x2").put("y2")
                .put("duration_ms").put("wait_ms").put("reason"))

        val prompt = """
            You are a general game-playing visual agent.
            Analyze the current screenshot and choose ONE safest useful next action.
            Coordinates MUST be normalized 0..1000 relative to the screenshot.
            Allowed type values:
            tap = one touch
            hold = press and hold at x,y
            drag = press at x,y and drag to x2,y2
            wait = do nothing for wait_ms
            back = Android back
            stop = stop if the screen indicates game over, fatal error, login block, or an unrecoverable state.
            Prefer small, reversible actions. Do not invent UI that is not visible.
            The screenshot may be any mobile game; infer controls and immediate objective from visual evidence.
            User goal: ${goal.ifBlank { "play autonomously and progress safely" }}
            Return only the requested JSON object.
        """.trimIndent()

        val input = org.json.JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image").put("mime_type", "image/jpeg").put("data", b64))

        val responseFormat = JSONObject()
            .put("type", "text")
            .put("mime_type", "application/json")
            .put("schema", schema)

        val body = JSONObject()
            .put("model", "gemini-3.8-flash")
            .put("input", input)
            .put("response_format", responseFormat)
            .toString()

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/interactions")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("Gemini HTTP ${resp.code}: ${resp.body?.string()}")
            val text = resp.body?.string() ?: error("Empty Gemini response")
            val root = JSONObject(text)
            val steps = root.optJSONArray("steps") ?: error("No steps")
            var modelText: String? = null
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                val content = step.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val block = content.optJSONObject(j) ?: continue
                    if (block.optString("type") == "text") {
                        modelText = block.optString("text")
                    }
                }
            }
            val actionJson = JSONObject(modelText ?: error("No model text"))
            return GameAction(
                type = actionJson.optString("type", "wait"),
                x = actionJson.optDouble("x", 0.0).toFloat(),
                y = actionJson.optDouble("y", 0.0).toFloat(),
                x2 = actionJson.optDouble("x2", 0.0).toFloat(),
                y2 = actionJson.optDouble("y2", 0.0).toFloat(),
                durationMs = actionJson.optLong("duration_ms", 100L).coerceIn(50L, 5000L),
                waitMs = actionJson.optLong("wait_ms", 500L).coerceIn(100L, 10000L),
                reason = actionJson.optString("reason", "")
            )
        }
    }
}

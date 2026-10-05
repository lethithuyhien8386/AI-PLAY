package com.autoplay.aibot.util

import android.content.Context
import android.content.SharedPreferences

/** Lưu cấu hình bot trên máy người dùng (API key không bao giờ rời khỏi máy ngoài việc gọi API Gemini). */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("autoplay_ai", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = sp.getString("model", MODELS[0]) ?: MODELS[0]
        set(v) = sp.edit().putString("model", v).apply()

    var intervalSec: Int
        get() = sp.getInt("interval_sec", 5)
        set(v) = sp.edit().putInt("interval_sec", v.coerceIn(3, 30)).apply()

    companion object {
        val MODELS = listOf(
            "gemini-2.0-flash",
            "gemini-2.5-flash",
            "gemini-2.0-flash-lite"
        )
    }
}

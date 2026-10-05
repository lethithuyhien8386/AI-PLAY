package com.autoplay.aibot.service

/**
 * Cầu nối giữa BotService và AccessibilityService (do hệ thống quản lý).
 * GameAutomationService tự đăng ký vào đây khi được bật.
 */
object AutomationBridge {
    @Volatile
    var service: GameAutomationService? = null

    val isReady: Boolean get() = service != null

    suspend fun tap(x: Int, y: Int): Boolean = service?.tapRel(x, y) ?: false

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean =
        service?.swipeRel(x1, y1, x2, y2, durationMs) ?: false

    suspend fun hold(x: Int, y: Int, durationMs: Long): Boolean =
        service?.holdRel(x, y, durationMs) ?: false
}

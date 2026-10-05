package com.autoplay.aibot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Dịch vụ Trợ năng: biến tọa độ 0–1000 của AI thành cử chỉ thật
 * (chạm / giữ / vuốt / kéo-thả) trên mọi ứng dụng.
 */
class GameAutomationService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutomationBridge.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Không cần đọc nội dung màn hình — AI "nhìn" bằng ảnh chụp.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (AutomationBridge.service === this) AutomationBridge.service = null
        super.onDestroy()
    }

    private fun toPx(x1000: Int, y1000: Int): Point {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Point(x1000 * b.width() / 1000, y1000 * b.height() / 1000)
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Point(x1000 * dm.widthPixels / 1000, y1000 * dm.heightPixels / 1000)
        }
    }

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean =
        withTimeoutOrNull(6000) {
            suspendCancellableCoroutine { cont ->
                val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                }, null)
                if (!ok && cont.isActive) cont.resume(false)
            }
        } ?: false

    suspend fun tapRel(x: Int, y: Int): Boolean {
        val p = toPx(x, y)
        return dispatch(Path().apply { moveTo(p.x.toFloat(), p.y.toFloat()) }, 60)
    }

    suspend fun holdRel(x: Int, y: Int, durationMs: Long): Boolean {
        val p = toPx(x, y)
        return dispatch(
            Path().apply { moveTo(p.x.toFloat(), p.y.toFloat()) },
            durationMs.coerceIn(300, 10000)
        )
    }

    suspend fun swipeRel(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean {
        val a = toPx(x1, y1)
        val b = toPx(x2, y2)
        return dispatch(
            Path().apply {
                moveTo(a.x.toFloat(), a.y.toFloat())
                lineTo(b.x.toFloat(), b.y.toFloat())
            },
            durationMs.coerceIn(100, 5000)
        )
    }
}

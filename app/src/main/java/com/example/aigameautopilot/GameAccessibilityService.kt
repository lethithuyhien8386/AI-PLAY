package com.example.aigameautopilot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class GameAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: GameAccessibilityService? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Không cần xử lý accessibility event ở đây.
    }

    override fun onInterrupt() {
        // Android yêu cầu override hàm này.
    }

    /**
     * Tap tại tọa độ x,y.
     */
    fun tap(
        x: Float,
        y: Float,
        duration: Long = 60L,
        callback: (() -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(x, y)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            duration.coerceAtLeast(1L)
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {

                override fun onCompleted(
                    gestureDescription: GestureDescription?
                ) {
                    callback?.invoke()
                }

                override fun onCancelled(
                    gestureDescription: GestureDescription?
                ) {
                    // Gesture bị hủy thì không gọi callback thành công.
                }
            },
            mainHandler
        )
    }

    /**
     * Nhấn giữ tại tọa độ x,y.
     */
    fun hold(
        x: Float,
        y: Float,
        duration: Long
    ) {
        val path = Path().apply {
            moveTo(x, y)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            duration.coerceAtLeast(100L)
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(
            gesture,
            null,
            mainHandler
        )
    }

    /**
     * Kéo từ x1,y1 đến x2,y2.
     */
    fun drag(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        duration: Long
    ) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            duration.coerceAtLeast(100L)
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(
            gesture,
            null,
            mainHandler
        )
    }

    /**
     * Kéo với nhiều điểm trung gian.
     * Có thể dùng cho thao tác vuốt phức tạp hơn.
     */
    fun swipe(
        points: List<Pair<Float, Float>>,
        duration: Long
    ) {
        if (points.size < 2) return

        val path = Path()

        path.moveTo(
            points[0].first,
            points[0].second
        )

        for (i in 1 until points.size) {
            path.lineTo(
                points[i].first,
                points[i].second
            )
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            duration.coerceAtLeast(100L)
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(
            gesture,
            null,
            mainHandler
        )
    }

    /**
     * Nhấn nút Back của Android.
     */
    fun back() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Nhấn Home.
     */
    fun home() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Mở màn hình Recent Apps.
     */
    fun recentApps() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }
}

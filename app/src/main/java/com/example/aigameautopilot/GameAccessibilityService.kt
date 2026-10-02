package com.example.aigameautopilot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class GameAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: GameAccessibilityService? = null
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun tap(x: Float, y: Float, duration: Long = 60L, callback: (() -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureDescription.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback?.invoke()
                }
            },
            mainHandler
        )
    }

    fun hold(x: Float, y: Float, duration: Long) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, mainHandler)
    }

    fun drag(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, mainHandler)
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
}

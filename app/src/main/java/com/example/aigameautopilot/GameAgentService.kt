package com.example.aigameautopilot

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class GameAgentService : Service() {
    private lateinit var projection: MediaProjection
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val worker = HandlerThread("game-ai").apply { start() }
    private val handler = Handler(worker.looper)
    private var running = false
    private var overlay: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var statusText: TextView? = null
    private var cycle = 0

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            42,
            NotificationCompat.Builder(this, "autopilot")
                .setContentTitle("AI Game Autopilot")
                .setContentText("Đang phân tích và điều khiển game")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build()
        )
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running && intent?.hasExtra("resultData") == true) {
            val code = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            val data = intent.getParcelableExtra<Intent>("resultData") ?: return START_NOT_STICKY
            startProjection(code, data)
        }
        return START_NOT_STICKY
    }

    private fun startProjection(code: Int, data: Intent) {
        val mgr = getSystemService(MediaProjectionManager::class.java)
        projection = mgr.getMediaProjection(code, data)

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection.createVirtualDisplay(
            "AI Game Autopilot",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                running = false
                handler.post { releaseProjection() }
            }
        }, handler)

        running = true
        handler.post { loop() }
        updateOverlay("AI: ON")
    }

    private fun loop() {
        if (!running) return
        val image = reader?.acquireLatestImage()
        if (image == null) {
            handler.postDelayed({ loop() }, 300)
            return
        }

        try {
            val plane = image.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val cropped = if (bitmap.width != image.width)
                Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            else bitmap

            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val key = prefs.getString("api_key", "") ?: ""
            val goal = prefs.getString("goal", "") ?: ""
            if (key.isBlank() || GameAccessibilityService.instance == null) {
                updateOverlay("AI: thiếu API key/quyền Accessibility")
                cropped.recycle()
                handler.postDelayed({ loop() }, 1000)
                return
            }

            cycle++
            updateOverlay("AI: thinking #$cycle")
            val action = GeminiClient(key).decide(cropped, goal)
            cropped.recycle()

            execute(action, imageWidth = resources.displayMetrics.widthPixels.toFloat(),
                imageHeight = resources.displayMetrics.heightPixels.toFloat())

            val wait = action.waitMs.coerceIn(250, 5000)
            handler.postDelayed({ loop() }, wait)
        } catch (e: Exception) {
            try { image.close() } catch (_: Exception) {}
            updateOverlay("AI error: ${e.message?.take(45)}")
            handler.postDelayed({ loop() }, 2500)
        }
    }

    private fun execute(action: GameAction, imageWidth: Float, imageHeight: Float) {
        val a = GameAccessibilityService.instance ?: return
        fun sx(v: Float) = (v.coerceIn(0f, 1000f) / 1000f) * imageWidth
        fun sy(v: Float) = (v.coerceIn(0f, 1000f) / 1000f) * imageHeight

        updateOverlay("${action.type}: ${action.reason.take(35)}")
        when (action.type.lowercase()) {
            "tap" -> a.tap(sx(action.x), sy(action.y), 60)
            "hold" -> a.hold(sx(action.x), sy(action.y), action.durationMs)
            "drag" -> a.drag(sx(action.x), sy(action.y), sx(action.x2), sy(action.y2), action.durationMs)
            "back" -> a.back()
            "stop" -> {
                running = false
                updateOverlay("AI: STOP")
            }
            "wait" -> Unit
        }
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val box = TextView(this).apply {
            text = "AI: OFF"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC7C5CFC.toInt())
            setPadding(22, 12, 22, 12)
            setOnClickListener {
                running = !running
                updateOverlay(if (running) "AI: ON" else "AI: OFF")
                if (running) handler.post { loop() }
            }
            setOnLongClickListener {
                stopSelf()
                true
            }
        }
        statusText = box
        overlay = box
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 18
            y = 90
        }
        windowManager?.addView(overlay, overlayParams)
    }

    private fun updateOverlay(text: String) {
        Handler(Looper.getMainLooper()).post { statusText?.text = text }
    }

    private fun releaseProjection() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        virtualDisplay = null
        reader = null
    }

    override fun onDestroy() {
        running = false
        releaseProjection()
        try { projection.stop() } catch (_: Exception) {}
        overlay?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        overlay = null
        worker.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("autopilot", "AI Game Autopilot",
                NotificationManager.IMPORTANCE_LOW)
        )
    }
}

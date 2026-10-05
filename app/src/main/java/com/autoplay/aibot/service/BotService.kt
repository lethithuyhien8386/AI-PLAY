package com.autoplay.aibot.service

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.autoplay.aibot.MainActivity
import com.autoplay.aibot.R
import com.autoplay.aibot.engine.AutomationEngine
import com.autoplay.aibot.engine.EngineConfig
import com.autoplay.aibot.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service trung tâm:
 *  - Chụp màn hình bằng MediaProjection (nhìn được mọi game)
 *  - Chạy AutomationEngine (AI phân tích → điều khiển)
 *  - Hiển thị thanh nổi 🎮 để bật/tắt, kéo thả tự do
 */
class BotService : Service() {

    companion object {
        const val ACTION_START = "com.autoplay.aibot.START"
        const val ACTION_STOP = "com.autoplay.aibot.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_MP_DATA = "mp_data"
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "autoplay_bot"

        @Volatile
        var isRunning = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engine: AutomationEngine? = null

    private lateinit var wm: WindowManager
    private var floatView: View? = null
    private var floatParams: WindowManager.LayoutParams? = null
    private var bubble: View? = null
    private var panel: View? = null
    private var tvStatus: TextView? = null
    private var tvGame: TextView? = null
    private var btnToggle: Button? = null
    private var tvBubbleIcon: TextView? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var capW = 0
    private var capH = 0
    private var density = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBot()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val rc = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(EXTRA_MP_DATA, Intent::class.java)
                else
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_MP_DATA)
                startBot(rc, data)
            }
        }
        return START_STICKY
    }

    // ---------------------------------------------------------------
    // Khởi động / dừng bot
    // ---------------------------------------------------------------
    private fun startBot(resultCode: Int, data: Intent?) {
        if (isRunning) return
        val prefs = Prefs(this)
        if (prefs.apiKey.isBlank()) {
            toast("Thiếu API key — mở app để nhập")
            stopSelf()
            return
        }
        if (data == null || resultCode != Activity.RESULT_OK) {
            toast("Cần cho phép chụp màn hình để bot hoạt động")
            stopSelf()
            return
        }

        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notif)
        }
        isRunning = true

        // Kích thước chụp = kích thước màn hình thật
        val (w, h, d) = screenSize()
        capW = w
        capH = h
        density = d

        try {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(resultCode, data)
            imageReader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "autoplay",
                capW, capH, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
        } catch (e: Exception) {
            toast("Không khởi tạo được chụp màn hình: ${e.message}")
            stopBot()
            return
        }

        engine = AutomationEngine(
            screenshot = { capture() },
            config = EngineConfig(prefs.apiKey, prefs.model, prefs.intervalSec * 1000L),
            onStatus = { s -> scope.launch(Dispatchers.Main) { tvStatus?.text = s } },
            onGame = { g -> scope.launch(Dispatchers.Main) { tvGame?.text = g } }
        )
        engine?.start(scope)

        setupOverlay()
        updateToggleUi()
        toast("Bot đã khởi động — mở game bất kỳ để AI tự chơi")
    }

    private fun stopBot() {
        engine?.stop()
        engine = null
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        removeOverlay()
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (isRunning) stopBot()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // Chụp màn hình
    // ---------------------------------------------------------------
    private fun screenSize(): Triple<Int, Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Triple(b.width(), b.height(), resources.displayMetrics.densityDpi)
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi)
        }
    }

    private fun capture(): Bitmap? {
        val reader = imageReader ?: return null
        val image = try {
            reader.acquireLatestImage()
        } catch (_: Exception) {
            null
        } ?: return null
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * capW
            var bmp = Bitmap.createBitmap(
                capW + rowPadding / pixelStride, capH, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)
            if (rowPadding != 0) {
                val cropped = Bitmap.createBitmap(bmp, 0, 0, capW, capH)
                bmp.recycle()
                bmp = cropped
            }
            if (isMostlyBlack(bmp)) {
                bmp.recycle()
                return null // game chặn chụp màn hình (FLAG_SECURE)
            }
            return bmp
        } catch (_: Exception) {
            return null
        } finally {
            image.close()
        }
    }

    private fun isMostlyBlack(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        var dark = 0
        var total = 0
        var stepX = w / 12
        var stepY = h / 12
        if (stepX < 1) stepX = 1
        if (stepY < 1) stepY = 1
        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val c = bmp.getPixel(x, y)
                val r = c shr 16 and 0xFF
                val g = c shr 8 and 0xFF
                val b = c and 0xFF
                if (r + g + b < 30) dark++
                total++
            }
        }
        return total > 0 && dark.toFloat() / total > 0.97f
    }

    // ---------------------------------------------------------------
    // Thanh nổi (floating bar)
    // ---------------------------------------------------------------
    private fun setupOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Cần quyền hiển thị trên ứng dụng khác cho thanh nổi")
            return
        }
        if (floatView != null) return
        val view = (getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater)
            .inflate(R.layout.floating_widget, null)

        bubble = view.findViewById(R.id.bubble)
        panel = view.findViewById(R.id.panel)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvGame = view.findViewById(R.id.tvGame)
        btnToggle = view.findViewById(R.id.btnToggle)
        tvBubbleIcon = view.findViewById(R.id.tvBubbleIcon)

        btnToggle?.setOnClickListener { togglePause() }
        view.findViewById<Button>(R.id.btnNow).setOnClickListener {
            engine?.requestNow = true
            toast("Đang phân tích màn hình ngay…")
        }
        view.findViewById<Button>(R.id.btnStop).setOnClickListener { stopBot() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 320
        }

        // Kéo để di chuyển, nhấn để mở/đóng bảng
        var downX = 0
        var downY = 0
        var startX = 0
        var startY = 0
        var moved = false
        bubble?.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX.toInt()
                    downY = e.rawY.toInt()
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX.toInt() - downX
                    val dy = e.rawY.toInt() - downY
                    if (dx * dx + dy * dy > 120) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    try {
                        wm.updateViewLayout(view, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }

        floatView = view
        floatParams = params
        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            toast("Không hiển thị được thanh nổi: ${e.message}")
            floatView = null
        }
    }

    private fun togglePanel() {
        panel?.let {
            it.visibility = if (it.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun togglePause() {
        val e = engine ?: return
        e.paused = !e.paused
        updateToggleUi()
        tvStatus?.text = if (e.paused) "⏸ Đang tạm dừng — nhấn ▶ để tiếp tục" else "▶ Đang chơi…"
    }

    private fun updateToggleUi() {
        val paused = engine?.paused == true
        btnToggle?.text = if (paused) "▶" else "⏸"
        tvBubbleIcon?.text = if (paused) "⏸" else "🎮"
    }

    private fun removeOverlay() {
        floatView?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        floatView = null
        bubble = null
        panel = null
        tvStatus = null
        tvGame = null
        btnToggle = null
        tvBubbleIcon = null
    }

    // ---------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BotService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_stat_bot)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notif_stop), stop)
            .setOngoing(true)
            .build()
    }

    private fun toast(msg: String) {
        scope.launch(Dispatchers.Main) {
            Toast.makeText(this@BotService, msg, Toast.LENGTH_SHORT).show()
        }
    }
}

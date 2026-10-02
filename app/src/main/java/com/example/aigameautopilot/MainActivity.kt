package com.example.aigameautopilot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var keyInput: EditText
    private lateinit var goalInput: EditText
    private lateinit var status: TextView
    private val captureCode = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        buildUi()
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 900)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 28)
            setBackgroundColor(Color.rgb(11,16,32))
        }

        fun label(text: String) = TextView(this).apply {
            this.text = text
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(0, 12, 0, 6)
        }

        val title = TextView(this).apply {
            text = "AI Game Autopilot"
            textSize = 28f
            setTextColor(Color.WHITE)
        }
        root.addView(title)

        val sub = TextView(this).apply {
            text = "Screen → Gemini → Action → repeat"
            textSize = 14f
            setTextColor(Color.rgb(168,176,197))
            setPadding(0, 4, 0, 18)
        }
        root.addView(sub)

        root.addView(label("Google AI Studio API key"))
        keyInput = EditText(this).apply {
            hint = "AIza..."
            setText(prefs.getString("api_key", ""))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = 0x00000081
        }
        root.addView(keyInput, LinearLayout.LayoutParams(-1, 58))

        root.addView(label("Mục tiêu / luật chơi (không bắt buộc)"))
        goalInput = EditText(this).apply {
            hint = "Ví dụ: chơi an toàn, ưu tiên sống lâu, tránh quảng cáo..."
            setText(prefs.getString("goal", ""))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            minLines = 2
            gravity = Gravity.TOP
        }
        root.addView(goalInput, LinearLayout.LayoutParams(-1, 100))

        val save = Button(this).apply {
            text = "LƯU CẤU HÌNH"
            setOnClickListener {
                prefs.edit().putString("api_key", keyInput.text.toString().trim())
                    .putString("goal", goalInput.text.toString()).apply()
                toast("Đã lưu trên thiết bị")
            }
        }
        root.addView(save)

        val accessibility = Button(this).apply {
            text = "1. BẬT QUYỀN ĐIỀU KHIỂN MÀN HÌNH"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(accessibility)

        val overlay = Button(this).apply {
            text = "2. CẤP QUYỀN THANH NỔI"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                } else toast("Đã có quyền thanh nổi")
            }
        }
        root.addView(overlay)

        val start = Button(this).apply {
            text = "3. CHẠY AI AUTOPILOT"
            setOnClickListener { startCapture() }
        }
        root.addView(start)

        val stop = Button(this).apply {
            text = "DỪNG"
            setOnClickListener {
                stopService(Intent(this@MainActivity, GameAgentService::class.java))
                toast("Đã dừng")
            }
        }
        root.addView(stop)

        status = TextView(this).apply {
            text = "Trạng thái: sẵn sàng"
            setTextColor(Color.rgb(168,176,197))
            setPadding(0, 18, 0, 0)
        }
        root.addView(status)

        val note = TextView(this).apply {
            text = "Lưu ý: AI không thể đảm bảo chơi được mọi game. Game có anti-cheat, nội dung mã hóa/không cho capture hoặc cần phản xạ cực nhanh có thể không hoạt động tốt."
            setTextColor(Color.rgb(150,158,180))
            textSize = 12f
            setPadding(0, 22, 0, 0)
        }
        root.addView(note)

        setContentView(root)
    }

    private fun startCapture() {
        val key = keyInput.text.toString().trim()
        if (key.isEmpty()) {
            toast("Hãy nhập API key trước")
            return
        }
        prefs.edit().putString("api_key", key)
            .putString("goal", goalInput.text.toString()).apply()

        if (!Settings.canDrawOverlays(this)) {
            toast("Hãy cấp quyền thanh nổi trước")
            return
        }

        val mgr = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mgr.createScreenCaptureIntent(), captureCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureCode && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, GameAgentService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("resultData", data)
            }
            ContextCompat.startForegroundService(this, intent)
            status.text = "Trạng thái: đang chạy — dùng thanh nổi để bật/tắt"
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}

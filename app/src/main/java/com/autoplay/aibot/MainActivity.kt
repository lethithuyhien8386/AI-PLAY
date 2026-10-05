package com.autoplay.aibot

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autoplay.aibot.service.AutomationBridge
import com.autoplay.aibot.service.BotService
import com.autoplay.aibot.service.GameAutomationService
import com.autoplay.aibot.util.Prefs

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var etApiKey: EditText
    private lateinit var dotA11y: TextView
    private lateinit var dotOverlay: TextView
    private lateinit var spModel: Spinner
    private lateinit var sbInterval: SeekBar
    private lateinit var tvInterval: TextView
    private lateinit var btnStartStop: Button
    private lateinit var tvBotStatus: TextView

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK && res.data != null) {
                val i = Intent(this, BotService::class.java)
                    .setAction(BotService.ACTION_START)
                    .putExtra(BotService.EXTRA_RESULT_CODE, res.resultCode)
                    .putExtra(BotService.EXTRA_MP_DATA, res.data)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(i)
                } else {
                    startService(i)
                }
            } else {
                toast("Bạn cần cho phép chụp màn hình thì bot mới nhìn được game")
            }
            refreshUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_main)

        etApiKey = findViewById(R.id.etApiKey)
        dotA11y = findViewById(R.id.dotA11y)
        dotOverlay = findViewById(R.id.dotOverlay)
        spModel = findViewById(R.id.spModel)
        sbInterval = findViewById(R.id.sbInterval)
        tvInterval = findViewById(R.id.tvInterval)
        btnStartStop = findViewById(R.id.btnStartStop)
        tvBotStatus = findViewById(R.id.tvBotStatus)

        etApiKey.setText(prefs.apiKey)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, Prefs.MODELS)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spModel.adapter = adapter
        spModel.setSelection(Prefs.MODELS.indexOf(prefs.model).coerceAtLeast(0))

        sbInterval.progress = (prefs.intervalSec - 3).coerceIn(0, 27)
        updateIntervalLabel()
        sbInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = updateIntervalLabel()
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        btnStartStop.setOnClickListener {
            if (BotService.isRunning) stopBot() else startBotFlow()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun updateIntervalLabel() {
        val sec = 3 + sbInterval.progress
        tvInterval.text = getString(R.string.cfg_interval_val, sec)
    }

    private fun startBotFlow() {
        val key = etApiKey.text.toString().trim()
        if (key.isEmpty()) {
            toast(getString(R.string.err_no_key))
            return
        }
        prefs.apiKey = key
        prefs.model = spModel.selectedItem.toString()
        prefs.intervalSec = 3 + sbInterval.progress

        if (!isA11yOn()) {
            toast(getString(R.string.err_no_a11y))
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast(getString(R.string.err_no_overlay))
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun stopBot() {
        startService(Intent(this, BotService::class.java).setAction(BotService.ACTION_STOP))
        // Cập nhật UI sau một nhịp để service kịp dừng
        btnStartStop.postDelayed({ refreshUi() }, 600)
    }

    private fun isA11yOn(): Boolean {
        if (AutomationBridge.isReady) return true
        val expected = ComponentName(this, GameAutomationService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun refreshUi() {
        val a11y = isA11yOn()
        val overlay = Settings.canDrawOverlays(this)

        dotA11y.text = getString(if (a11y) R.string.status_on else R.string.status_off)
        dotA11y.setTextColor(
            ContextCompat.getColor(
                this, if (a11y) R.color.neon_green else R.color.neon_red
            )
        )
        dotOverlay.text = getString(if (overlay) R.string.status_on else R.string.status_off)
        dotOverlay.setTextColor(
            ContextCompat.getColor(
                this, if (overlay) R.color.neon_green else R.color.neon_red
            )
        )

        if (BotService.isRunning) {
            btnStartStop.text = getString(R.string.btn_stop)
            tvBotStatus.text = getString(R.string.bot_running)
        } else {
            btnStartStop.text = getString(R.string.btn_start)
            tvBotStatus.text = getString(R.string.bot_stopped)
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

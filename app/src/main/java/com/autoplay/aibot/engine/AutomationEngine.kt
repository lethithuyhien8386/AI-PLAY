package com.autoplay.aibot.engine

import android.graphics.Bitmap
import android.util.Base64
import com.autoplay.aibot.service.AutomationBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

data class EngineConfig(
    val apiKey: String,
    val model: String,
    val intervalMs: Long
)

/**
 * Vòng lặp chính của bot:
 * chụp màn hình → (bỏ qua nếu màn hình không đổi) → Gemini phân tích →
 * thực hiện tap/giữ/vuốt → lặp lại. Có chống kẹt (stuck detection).
 */
class AutomationEngine(
    private val screenshot: () -> Bitmap?,
    private val config: EngineConfig,
    private val onStatus: (String) -> Unit,
    private val onGame: (String) -> Unit
) {
    @Volatile var running = false
        private set
    @Volatile var paused = false
    @Volatile var requestNow = false

    private var job: Job? = null
    private val client = GeminiClient(config.apiKey, config.model)

    fun start(scope: CoroutineScope) {
        if (running) return
        running = true
        paused = false
        job = scope.launch(Dispatchers.Default) { loop() }
    }

    fun stop() {
        running = false
        job?.cancel()
        job = null
    }

    private suspend fun loop() {
        var lastHash = 0L
        var stuckCount = 0
        var memory = ""
        onStatus("Sẵn sàng — nhấn ▶ để AI bắt đầu chơi")

        while (running) {
            try {
                if (paused) {
                    delay(600)
                    continue
                }
                if (!AutomationBridge.isReady) {
                    onStatus("⏳ Đang chờ quyền Trợ năng được bật…")
                    delay(2000)
                    continue
                }

                val bmp = screenshot()
                if (bmp == null) {
                    onStatus("⚠️ Không chụp được màn hình — game này có thể chặn chụp màn hình")
                    delay(3000)
                    continue
                }
                val small = scaleDown(bmp, 768)
                if (small !== bmp) bmp.recycle()
                val hash = avgHash(small)

                var skipApi = false
                if (hash == lastHash) {
                    stuckCount++
                    if (stuckCount < 3) skipApi = true // màn hình đứng yên → tiết kiệm lượt API
                } else {
                    stuckCount = 0
                    lastHash = hash
                }

                if (!skipApi || requestNow) {
                    requestNow = false
                    val stuck = stuckCount >= 3
                    onStatus(if (stuck) "🔄 AI đang thử cách khác (dường như bị kẹt)…" else "🧠 AI đang phân tích màn hình…")

                    val b64 = jpegBase64(small, 72)
                    val res = try {
                        client.analyze(b64, memory, stuck)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onStatus("❌ Lỗi API: ${(e.message ?: "không rõ").take(120)}")
                        small.recycle()
                        delay(5000)
                        continue
                    }

                    if (res.memory.isNotBlank()) memory = (memory + " | " + res.memory).takeLast(500)
                    onGame("🎮 ${res.game}")
                    val stateLine = if (res.state.isNotBlank()) " — ${res.state}" else ""
                    onStatus("💭 ${res.thought}$stateLine")

                    for (a in res.actions) {
                        if (!running || paused) break
                        runAction(a)
                        delay(350)
                    }
                    if (stuck) stuckCount = 0
                    small.recycle()

                    // Chờ tới lượt phân tích tiếp theo (có thể bị ngắt bởi "phân tích ngay")
                    var waited = 0L
                    while (running && !paused && waited < config.intervalMs && !requestNow) {
                        delay(400)
                        waited += 400
                    }
                } else {
                    small.recycle()
                    delay(1200)
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                onStatus("⚠️ ${e.message}")
                delay(2000)
            }
        }
        onStatus("Đã dừng")
    }

    private suspend fun runAction(a: GameAction) {
        when (a.type) {
            ActionType.TAP -> AutomationBridge.tap(a.x, a.y)
            ActionType.SWIPE -> AutomationBridge.swipe(a.x1, a.y1, a.x2, a.y2, a.durationMs)
            ActionType.HOLD -> AutomationBridge.hold(a.x, a.y, a.durationMs)
            ActionType.WAIT -> delay(a.durationMs)
        }
    }

    companion object {
        fun scaleDown(src: Bitmap, maxDim: Int): Bitmap {
            val m = maxOf(src.width, src.height)
            if (m <= maxDim) return src
            val s = maxDim.toFloat() / m
            return Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true)
        }

        fun jpegBase64(bmp: Bitmap, quality: Int): String {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }

        /** Average-hash 8x8: phát hiện màn hình không thay đổi để tiết kiệm API. */
        fun avgHash(bmp: Bitmap): Long {
            val s = Bitmap.createScaledBitmap(bmp, 8, 8, true)
            val px = IntArray(64)
            s.getPixels(px, 0, 8, 0, 0, 8, 8)
            s.recycle()
            var sum = 0L
            val gray = IntArray(64)
            for (i in px.indices) {
                val c = px[i]
                val g = ((c shr 16 and 0xFF) * 3 + (c shr 8 and 0xFF) * 4 + (c and 0xFF) * 2) / 9
                gray[i] = g
                sum += g
            }
            val avg = sum / 64
            var h = 0L
            for (i in gray.indices) if (gray[i] > avg) h = h or (1L shl i)
            return h
        }
    }
}

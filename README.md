# AI Game Autopilot (Android)

Ứng dụng Android thử nghiệm cho game automation bằng AI:

**Screenshot → Gemini Vision → JSON action → Accessibility gesture → Screenshot**

### Có sẵn
- MediaProjection chụp màn hình game.
- Google AI Studio / Gemini API.
- Tap, hold, drag/swipe, back, wait, stop.
- Thanh nổi có thể bấm để bật/tắt; nhấn giữ để dừng service.
- Lưu API key và mục tiêu trên thiết bị.
- GitHub Actions build APK debug tự động.
- Không nhúng API key vào source code.

### Giới hạn
"Chơi mọi game" không thể đảm bảo. AI phụ thuộc vào hình ảnh, độ trễ mạng, game, FPS, UI và cơ chế anti-cheat. Một số game có thể chặn screen capture hoặc automation.

### Cách dùng
1. Mở app.
2. Nhập Google AI Studio API key.
3. Bật Accessibility cho `AI Game Autopilot`.
4. Cấp quyền "Display over other apps".
5. Nhấn `CHẠY AI AUTOPILOT` và chấp nhận quyền screen capture.
6. Mở game. Thanh nổi hiển thị trạng thái.
7. Bấm thanh nổi để bật/tắt vòng lặp AI. Nhấn giữ để dừng.

### Build bằng GitHub
Workflow: `.github/workflows/build-apk.yml`

Sau khi push repo:
**Actions → Build APK → Run workflow**

APK nằm trong artifact `AI-Game-Autopilot-debug`.

### Quan trọng về API key
API key được gửi trực tiếp từ điện thoại tới Gemini API. Không commit key vào GitHub. Google yêu cầu `x-goog-api-key` cho Gemini API.

### Tùy biến
Model mặc định trong `GeminiClient.kt` là `gemini-3.8-flash`. Có thể đổi sang model Gemini khác mà API key/project của bạn có quyền dùng.

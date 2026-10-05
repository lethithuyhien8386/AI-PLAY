# 🎮 AutoPlay AI — Bot tự chơi mọi game bằng Gemini

App Android giúp AI **tự nhìn màn hình, suy luận lối chơi và tự chạm / giữ / vuốt / kéo-thả**
để chơi bất kỳ game nào, dùng API của **Google AI Studio (Gemini)**.
Điều khiển mọi thứ qua **thanh nổi 🎮** có thể kéo thả, bật/tắt bất cứ lúc nào.

## ✨ Tính năng

- 📸 **Tự quét màn hình** bằng MediaProjection — nhìn được mọi game đang mở
- 🧠 **AI suy luận lối chơi**: nhận diện game, hiểu trạng thái, tự quyết định nước đi
- 👆 **Tự động thao tác**: tap, hold (giữ), swipe/kéo-thả qua AccessibilityService
- 🎮 **Thanh nổi bật/tắt**: kéo thả tự do, nhấn để mở bảng điều khiển
  - ▶/⏸ tạm dừng / tiếp tục, 📸 phân tích ngay, ⏹ dừng & thoát
  - Hiển thị AI đang nghĩ gì + game đang chơi
- 🔄 **Chống kẹt**: phát hiện màn hình đứng yên, bỏ qua lượt gọi API thừa, tự đổi chiến thuật khi bị kẹt
- 🧾 **Tiết kiệm API**: chỉ gọi Gemini khi màn hình thay đổi; khoảng cách phân tích tùy chỉnh (3–30s)
- 🔒 API key chỉ lưu trên máy bạn

## 🔑 Bước 1 — Lấy API key Google AI Studio (miễn phí)

1. Vào [aistudio.google.com](https://aistudio.google.com) → đăng nhập tài khoản Google
2. Nhấn **Get API key** → **Create API key** → copy key
3. Mở app AutoPlay AI, dán key vào ô API key

## 🏗️ Bước 2 — Build file APK bằng GitHub Actions (không cần máy tính cấu hình)

> Code đã có sẵn workflow tự build. Bạn chỉ cần push code lên GitHub.

**Cách A — bằng trình duyệt (dễ nhất):**
1. Vào [github.com/new](https://github.com/new) tạo repository mới (đặt tên `autoplay-ai`, chọn **Private**)
2. Trên máy tính, giải nén file zip → mở thư mục `autoplay-aibot`
3. Chạy các lệnh sau (cần cài [git](https://git-scm.com)):
   ```bash
   cd autoplay-aibot
   git init
   git add .
   git commit -m "AutoPlay AI bot"
   git branch -M main
   git remote add origin https://github.com/TEN-BAN/autoplay-ai.git
   git push -u origin main
   ```
   (Lần đầu GitHub sẽ hỏi đăng nhập — dùng token hoặc GitHub CLI `gh auth login`)
4. Vào tab **Actions** của repo → chọn workflow **Build APK** → chờ ~3–5 phút
5. Vào **Artifacts** → tải `autoplay-ai-apk` → giải nén ra file `app-debug.apk`

**Cách B — mỗi lần push code mới**, workflow tự chạy lại và build APK mới.

## 📲 Bước 3 — Cài & dùng

1. Copy `app-debug.apk` vào điện thoại → mở file để cài
   (Cho phép "Cài đặt ứng dụng không rõ nguồn gốc" nếu máy hỏi)
2. Mở **AutoPlay AI** → dán API key → cấp đủ quyền:
   - **Trợ năng** (để bot chạm/vuốt): Cài đặt → Trợ năng → AutoPlay AI → Bật
   - **Hiển thị trên ứng dụng khác** (thanh nổi)
   - **Chụp màn hình**: nhấn BẮT ĐẦU rồi chọn "Bắt đầu ngay"
3. Mở game bất kỳ → thanh nổi 🎮 xuất hiện → nhấn **▶** để AI tự chơi
4. Kéo thanh nổi để di chuyển, nhấn vào để mở bảng điều khiển

## 🧩 Cấu trúc project

```
autoplay-aibot/
├── .github/workflows/build-apk.yml   # CI: tự build APK mỗi lần push
├── app/
│   ├── build.gradle                   # compileSdk 34, minSdk 26
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/autoplay/aibot/
│       │   ├── MainActivity.kt        # Màn hình chính: key, quyền, cấu hình
│       │   ├── engine/
│       │   │   ├── GeminiClient.kt    # Gọi Google AI Studio, parse JSON hành động
│       │   │   ├── AutomationEngine.kt# Vòng lặp: chụp → phân tích → thao tác
│       │   │   └── Action.kt          # tap / swipe / hold / wait (tọa độ 0–1000)
│       │   ├── service/
│       │   │   ├── BotService.kt      # Foreground service: chụp MH + thanh nổi
│       │   │   ├── GameAutomationService.kt  # Accessibility: cử chỉ thật
│       │   │   └── AutomationBridge.kt
│       │   └── util/Prefs.kt
│       └── res/                       # Giao diện neon dark, tiếng Việt
```

### Luồng hoạt động

```
MediaProjection (chụp màn hình)
        ↓  Bitmap (mỗi 3–30s, bỏ qua khi màn hình đứng yên)
Gemini API (aistudio.google.com)
        ↓  JSON: { game, state, thought, actions[] }
AutomationEngine → AutomationBridge
        ↓  tap / hold / swipe (tọa độ 0–1000 → pixel thật)
GameAutomationService (Accessibility)
```

## ⚠️ Lưu ý

- Một số game chặn chụp màn hình (ảnh đen) — bot sẽ báo "không chụp được màn hình" và không chơi được game đó.
- Mỗi lượt phân tích tốn 1 request Gemini — gói miễn phí AI Studio có giới hạn, chỉnh khoảng cách phân tích dài hơn để tiết kiệm.
- Không dùng bot để gian lận trong game online cạnh tranh — tài khoản có thể bị khóa.
- App không thu thập dữ liệu cá nhân; ảnh màn hình chỉ gửi tới API Gemini để phân tích theo lệnh của bạn.

## 🛠️ Build thủ công (nếu muốn)

Cần JDK 17 + Android SDK. Không cần wrapper:

```bash
gradle :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

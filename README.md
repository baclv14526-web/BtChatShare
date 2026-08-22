# BT Chat Share

Ứng dụng Android (Kotlin) cho phép **chat văn bản và chia sẻ file trực tiếp giữa 2 điện thoại qua Bluetooth Classic (RFCOMM)**, không cần Internet, không cần server trung gian.

## Tính năng
- Ghép nối và kết nối Bluetooth giữa 2 máy (một máy đóng vai trò server lắng nghe, máy kia chủ động kết nối).
- Gửi/nhận tin nhắn văn bản theo thời gian thực.
- Gửi/nhận file bất kỳ (ảnh, video, tài liệu…) với thanh tiến trình %, file nhận được lưu trong bộ nhớ riêng của app tại `Android/data/com.example.btchatshare/files/Received/`.
- Tự xin quyền Bluetooth phù hợp theo từng phiên bản Android (Android 12+ dùng `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN`, Android ≤11 dùng `ACCESS_FINE_LOCATION`).

## Cấu trúc dự án
```
app/src/main/java/com/example/btchatshare/
├── App.kt                     # Application, giữ 1 BluetoothChatService dùng chung
├── BluetoothChatService.kt    # Logic RFCOMM: server/client thread + giao thức gửi text/file
├── MainActivity.kt            # Màn hình chính: xin quyền, bật Bluetooth, chờ kết nối
├── DeviceListActivity.kt      # Danh sách thiết bị đã ghép nối + quét thiết bị mới
├── ChatActivity.kt            # Màn hình chat + gửi/nhận file
├── ChatAdapter.kt / ChatMessage.kt
└── DeviceAdapter.kt
```

## Mở bằng Android Studio
1. Mở Android Studio → **Open** → chọn thư mục `BtChatShare`.
2. Android Studio sẽ tự tạo Gradle wrapper (`gradlew`) nếu thiếu — hoặc bạn có thể chạy `gradle wrapper --gradle-version 8.9` một lần (yêu cầu đã cài Gradle) để tạo `gradlew`/`gradlew.bat` cho máy local. Repo này **không** commit sẵn `gradle-wrapper.jar` (file nhị phân) — CI trên GitHub Actions tự sinh wrapper ở bước build nên không ảnh hưởng tới việc build trên GitHub.
3. Chọn **Run** trên 2 thiết bị Android thật (Bluetooth không hoạt động trên emulator) đã bật Bluetooth và ở gần nhau.
4. Trên máy A: bấm **"Cho phép thiết bị khác tìm thấy tôi"**. Trên máy B: bấm **"Kết nối tới thiết bị khác"**, ghép nối (pair) nếu chưa từng ghép, sau đó chọn máy A trong danh sách.
5. Sau khi kết nối thành công, cả hai máy tự chuyển sang màn hình Chat.

## Build & CI trên GitHub (public repo)
Workflow tại `.github/workflows/android-build.yml` sẽ tự động:
1. Checkout mã nguồn.
2. Cài JDK 17 và Android SDK (platform 34, build-tools 34.0.0).
3. Dùng `gradle/actions/setup-gradle` để cài Gradle 8.9, sau đó chạy `gradle wrapper` để tự sinh `gradlew` (không cần commit file nhị phân `gradle-wrapper.jar`).
4. Build `assembleDebug` và upload file APK làm artifact của workflow run (mục **Actions → chọn run → Artifacts**).

Workflow chạy khi push/PR vào nhánh `main`/`master`, hoặc có thể chạy thủ công bằng **Actions → Android CI Build → Run workflow**.

## Ghi chú kỹ thuật
- `minSdk = 26`, `targetSdk = compileSdk = 34`.
- Giao thức truyền dữ liệu trên cùng 1 socket RFCOMM: 1 byte loại gói (0 = text, 1 = file) + `UTF` (nội dung/tên file) + với file có thêm `long` (kích thước) và dữ liệu nhị phân theo sau.
- UUID dịch vụ cố định dùng chung giữa client/server: `8ce255c0-200a-11e0-ac64-0800200c9a66`.
- Vì Bluetooth Classic yêu cầu phần cứng thật, **không thể test trên emulator** — cần 2 thiết bị Android vật lý.

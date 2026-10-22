1. Đặc tả nghiệp vụ và Danh sách Pages (Core Business Specs)
Nhóm 1: Guest (Người chưa đăng nhập)
Trang chủ: Hiển thị form tìm kiếm chuyến xe (Nơi đi, Nơi đến có dropdown gợi ý, Ngày đi tích hợp bật/tắt lịch âm). Hiển thị các block Tuyến đường phổ biến và Ưu đãi nổi bật.
Trang Danh sách kết quả: Hiển thị danh sách chuyến xe dựa trên tìm kiếm. Cung cấp bộ lọc bên trái (theo giờ đi, nhà xe, điểm đón/trả, giá) và tính năng sắp xếp (sớm nhất, rẻ nhất...).
Trang Thông tin chi tiết: Giao diện chia tab ngang bao gồm Giảm giá, Đón/trả, Đánh giá (điểm tổng sao và bình luận), Chính sách, Hình ảnh, Tiện ích. (Yêu cầu đăng nhập khi bấm nút "Chọn chỗ").
Nhóm 2: User (Hành khách đã đăng nhập)
Trang Chọn chỗ & Thanh toán: Hiển thị sơ đồ ghế (tầng trên/dưới) và tính tiền theo loại ghế. Form thông tin liên hệ tự động điền (Auto-fill) dữ liệu từ tài khoản. Có đồng hồ đếm ngược 10 phút trên header. Hiển thị popup cảnh báo "Giữ hay hủy chỗ" khi có ý định thoát trang. Chức năng chọn phương thức thanh toán.
Trang Đơn hàng của tôi: Giao diện chia 3 tab (Hiện tại, Đã đi, Đã hủy) để quản lý lịch sử đặt vé.
Trang Thông tin tài khoản: Form xem và cập nhật thông tin cá nhân (Họ tên, SĐT, Ngày sinh, Giới tính) và chức năng Đăng xuất.
Trang Đánh giá chuyến đi: Giao diện chọn số sao và nhập bình luận/phản hồi cho các chuyến xe ở trạng thái "Đã đi".
Nhóm 3: Admin (Quản trị hệ thống)
Trang Dashboard: Biểu đồ thống kê doanh thu, số lượng vé bán ra, tỷ lệ lấp đầy.
Trang Quản lý Chuyến xe: Thêm, sửa, xóa thông tin chuyến xe (tuyến đường, giờ đi, giá vé) để hiển thị ra cho khách đặt.
Trang Quản lý Đơn hàng: Xem danh sách vé mà User đã đặt, quản lý danh sách hành khách theo từng chuyến.
2. Bảng Phân công Công việc Full-stack (Theo luồng Pages)
Ghi chú: Toàn bộ thiết kế Database chung sẽ được chốt trước một lần cho cả nhóm. Các thành viên nhận Page nào sẽ chịu trách nhiệm code cả Frontend (UI, State) và Backend (API, Logic xử lý) cho Page đó.
Thành viên 1: Module Khám phá (Trang chủ + Danh sách kết quả)
Trang đảm nhận: Trang chủ, Trang Danh sách kết quả (Nhóm Guest).
Frontend (React): Dựng layout tổng, code form tìm kiếm động, hiển thị danh sách chuyến, xử lý state cho bộ lọc và sắp xếp.
Backend (Spring Boot): Viết API tìm kiếm chuyến xe, API xử lý truy vấn động cho bộ lọc và sắp xếp.
Nhiệm vụ điểm cộng (Từ từ làm sau): Code tính năng tìm kiếm thông minh bằng AI Gemini (tự nhận diện điểm đi/đến, giá tiền từ câu text người dùng gõ).
Thành viên 2: Module Chi tiết, Đánh giá & Phân quyền
Trang đảm nhận: Trang Thông tin chi tiết (Nhóm Guest), Trang Đánh giá chuyến đi (Nhóm User). Phụ trách thêm luồng Đăng nhập/Đăng ký.
Frontend (React): Code component Tab thông tin chuyến xe, form đăng nhập/đăng ký, form gửi đánh giá chuyến đi, xử lý chặn popup yêu cầu login khi khách bấm "Chọn chỗ".
Backend (Spring Boot): API lấy chi tiết chuyến xe, API CRUD cho Review/Đánh giá. Cấu hình Spring Security (JWT) phân quyền Guest, User, Admin.
Nhiệm vụ điểm cộng (Từ từ làm sau): Setup Docker và Docker Compose cho Database và Backend để chuẩn hóa môi trường dev cho cả nhóm.
Thành viên 3: Module Đặt vé Core (Nghiệp vụ phức tạp nhất)
Trang đảm nhận: Trang Chọn chỗ & Thanh toán (Nhóm User).
Frontend (React): Code sơ đồ ghế 2 tầng (click chọn đổi màu, tính tổng tiền), đồng hồ đếm ngược, tự động điền form, popup chặn thoát trang, giao diện tích hợp các cổng thanh toán.
Backend (Spring Boot): API tạo đơn hàng, thuật toán giữ ghế tạm thời trong 10 phút, xử lý chống đụng độ dữ liệu (Concurrency) khi nhiều người giành 1 ghế, API tích hợp thanh toán (VNPAY demo).
Nhiệm vụ điểm cộng (Từ từ làm sau): Tách module xử lý vé và ghế này thành một Booking Microservice độc lập.
Thành viên 4: Module Quản lý Cá nhân & Quản trị Hệ thống
Trang đảm nhận: Trang Đơn hàng của tôi, Trang Thông tin tài khoản (Nhóm User) + Toàn bộ 3 Trang Admin (Dashboard, Quản lý chuyến, Quản lý đơn hàng).
Frontend (React): Code giao diện hồ sơ, lịch sử vé chia tab cho User. Dựng layout Sidebar cho Admin, vẽ biểu đồ thống kê, code các form Thêm/Sửa/Xóa dữ liệu nội bộ.
Backend (Spring Boot): API cập nhật user profile, API truy xuất lịch sử đơn hàng. Các API thống kê và CRUD chuyên biệt dành riêng cho quyền Admin.
Nhiệm vụ điểm cộng (Từ từ làm sau): Thực hiện Deploy Backend lên server ảo (VPS hoặc Render) và Deploy Frontend React lên Vercel.



Link DB: [db.md](db.md)

---

## Ghi chú triển khai (cập nhật)

Thứ tự làm đã chốt ở [db.md](db.md) mục C. Trạng thái hiện tại:

- **Giai đoạn 1 — Catalog + DB nền: XONG.** TV1 có API tìm kiếm/lọc/sắp xếp chuyến;
  TV2 có API chi tiết chuyến + đọc đánh giá; TV4 có API admin CRUD catalog; TV3 có
  API đọc sơ đồ ghế (việc giữ ghế 10 phút thuộc Giai đoạn 2).
- Endpoint đầy đủ: [API.md](API.md) mục 11. Cổng booking-service là `8083`,
  database `booking_db` (cổng host `5435`).
- Seed dữ liệu mẫu: `--app.seed.enabled=true` (mặc định tắt) — 20 địa điểm, 6 nhà xe,
  15 tuyến, 225 chuyến, 8850 ghế.
- **Giai đoạn 2 — Đặt vé + giữ ghế + thanh toán: XONG.** `POST /api/bookings` giữ
  ghế (khoá dòng `SELECT ... FOR UPDATE`, đơn `PENDING`, hạn 10 phút), `POST
  /api/bookings/{id}/pay` (ghế `BOOKED`, đơn `PAID`, giao dịch), `POST
  /api/bookings/{id}/cancel`, `GET /api/bookings` (đơn của tôi), `GET
  /api/admin/bookings` (quản lý đơn). `BookingExpiryJob` trả ghế hết hạn mỗi 60s.
  Ghế cũ đặt trùng bị chặn bằng khoá dòng, không bằng UNIQUE. Endpoint ở
  [API.md](API.md) mục 11.7–11.8. 100 test booking chạy xanh, có test tranh ghế
  hai luồng thật.
- Giai đoạn 3 (đánh giá ghi, dashboard thống kê) chưa làm; bảng và route đã sẵn.
  **Lộ trình còn lại và các quyết định đã chốt** (cổng thanh toán, quy tắc đánh giá,
  định nghĩa dashboard, khách vãng lai...) nằm ở [PROJECT_STATUS.md](PROJECT_STATUS.md).
- Khác biệt so với đặc tả đã chốt: thanh toán chính dùng **Stripe test mode** thay
  VNPAY (VNPAY là điểm cộng); **khách vãng lai đặt vé được không cần đăng nhập** (như
  Vexere — đã làm 2026-10-22): bấm "Chọn chỗ" chỉ gợi ý đăng nhập để tự điền thông tin,
  không chặn. Khách vãng lai tra cứu vé bằng mã vé + SĐT và không được viết đánh giá.
- **payment-service — Stripe Checkout: XONG (chờ khoá test để chạy E2E).** Service
  riêng cổng `8084`, KHÔNG có database riêng: ghi bảng `payments` qua API nội bộ
  `/api/internal/bookings/**` (header `X-Internal-Key`) của booking-service, nhờ vậy
  tiền và ghế luôn chốt trong cùng một transaction. `POST /api/payments/checkout-session`
  tạo Stripe Checkout Session (số tiền lấy từ đơn, KHÔNG lấy từ client);
  `POST /api/payments/webhook` xác thực chữ ký `Stripe-Signature` rồi gọi
  booking-service chuyển đơn sang `PAID`, ghế `BOOKED`;
  nếu ghế đã bị trả về do hết hạn thì tự động hoàn tiền. Gateway đã có route
  `/api/payments/**` và mục Swagger `payment-service`. 19 test payment chạy xanh.
  Cần `STRIPE_SECRET_KEY` + `STRIPE_WEBHOOK_SECRET` trong `.env` để chạy thật (xem
  [.env.example](.env.example)). Thanh toán demo `/api/bookings/{id}/pay` của
  booking-service vẫn giữ để test offline, không cần mạng.

Ghi chú này không thay thế phần đặc tả phía trên.
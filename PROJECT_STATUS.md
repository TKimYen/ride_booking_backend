# Ride Booking Project Status & Kế hoạch tổng

Ngày cập nhật: 2026-10-22

Đây là **tài liệu gốc về trạng thái và lộ trình**. Các tài liệu khác chỉ đi sâu vào phần của mình
và trỏ về đây khi nói "làm việc gì trước":

| Tài liệu | Là nguồn chuẩn cho | Không chứa |
|---|---|---|
| [requirement.md](requirement.md) | Đặc tả nghiệp vụ gốc, phân công 4 thành viên | Trạng thái, cách làm |
| **PROJECT_STATUS.md** (file này) | Đã xong / còn thiếu, **lộ trình**, **quyết định đã chốt** | Code mẫu |
| [db.md](db.md) + `database/03_booking_db.sql` | Schema (bảng, cột, ràng buộc) | API |
| [API.md](API.md) | Endpoint **đã chạy được** | Endpoint dự kiến |
| [HUONG_DAN_HOAN_THIEN_BE.md](../HUONG_DAN_HOAN_THIEN_BE.md) | Cách làm từng việc backend còn thiếu (code khung, test) | Thiết kế giao diện |
| [../HUONG_DAN_FRONTEND.md](../HUONG_DAN_FRONTEND.md) | Thiết kế frontend: route, trang, design system | Schema, code backend |
| [README.md](README.md) | Cách chạy, cấu hình, xử lý sự cố | |

Quy tắc giữ đồng bộ: làm xong một việc → cập nhật trạng thái ở **file này**, thêm endpoint vào
**API.md**; đổi schema → **db.md + SQL + entity** cùng lúc. Không chép lại bảng trạng thái sang file khác.

## Quy ước trạng thái

| Trạng thái | Ý nghĩa |
|---|---|
| Hoàn thành | Đã có code + test, hoặc cấu hình đã kiểm chứng. |
| Một phần | Đã có một phần (ví dụ có schema nhưng chưa có API). |
| Chưa có | Chưa thấy trong mã nguồn. |

## Tổng quan

- Backend microservice (gateway, discovery, auth, user, booking, payment) đã có xác thực, hồ sơ, catalog,
  giữ ghế 10 phút chống tranh chấp, đặt vé, thanh toán Stripe + thanh toán demo.
- **Schema mở rộng đã áp dụng (2026-10-19)**: khách vãng lai (`booking_code`, `guest_token_hash`),
  nội dung nhà xe (`company_images`, `amenities`, `company_policies`, `cancellation_rules`), `promotions`,
  đánh giá gắn đơn (`reviews.booking_id`, `company_id`, `is_hidden`), hoàn tiền (`payments.REFUNDED`,
  `refund_amount`). Entity + repository đã có; **chưa có API và seed** cho các phần này.
- **Khách vãng lai đặt vé: XONG (2026-10-22)** — đặt vé không cần tài khoản, `X-Guest-Token`, tra cứu vé,
  gắn vé vào tài khoản, thanh toán Stripe cho khách; 15 test mới (booking 115 test, payment 22 test chạy xanh).
  ⚠️ DB PostgreSQL tạo trước 2026-10-19 phải chạy lại `database/03_booking_db.sql` một lần (bỏ `NOT NULL`
  của `bookings.user_id`, sinh mã vé cho đơn cũ) — nếu không, đặt vé không đăng nhập sẽ lỗi 500.
- Còn thiếu: vòng đời `COMPLETED`, ghi đánh giá, dashboard, huỷ chuyến, API cho nội dung/khuyến mãi.
- Chưa có frontend trong workspace.

## Đối chiếu theo phạm vi

| Phạm vi | Trạng thái | Đã có | Còn thiếu → việc ở lộ trình |
|---|---|---|---|
| Tìm kiếm chuyến | Hoàn thành (BE) | Lọc tuyến/ngày/giờ/nhà xe/giá, sắp xếp, phân trang, seed | Lọc điểm đón/trả, loại xe, nhiều nhà xe, trả `rating` → **4.4** |
| Chi tiết chuyến | Một phần | Chi tiết, điểm đón/trả, đọc đánh giá; bảng ảnh/tiện ích/chính sách | API profile nhà xe + seed → **3.7, 4.1** |
| Đăng ký, đăng nhập, phân quyền | Hoàn thành (BE) | JWT + refresh xoay vòng, USER/ADMIN, xác minh email, quên mật khẩu, chống brute-force | Tích hợp FE |
| Hồ sơ người dùng | Hoàn thành (BE) | `GET/PUT /api/users/me`, admin users | Tích hợp FE |
| Sơ đồ ghế, giữ chỗ | Hoàn thành (BE) | Giữ 10 phút, `SELECT ... FOR UPDATE`, job trả ghế | **Lỗi**: chưa chặn đặt chuyến đã khởi hành → **3.0** |
| Đặt vé, đơn của tôi | Hoàn thành (BE) | Tạo/xem/huỷ đơn, lọc nhiều `status` (`?status=PENDING,PAID`), admin xem đơn | — |
| Vòng đời chuyến/đơn | Chưa có | Enum `COMPLETED` có nhưng không chỗ nào gán | Job hoàn tất + huỷ chuyến → **3.1, 3.2** |
| Đánh giá | Một phần | Đọc + tổng hợp sao; schema gắn `booking_id` | API ghi, điểm theo nhà xe, admin ẩn → **3.4** |
| Dashboard Admin | Chưa có | Index `payments.created_at`, `bookings.created_at` | `/api/admin/stats/*` → **3.5** |
| Quản lý chuyến (Admin) | Một phần | CRUD, tự sinh ghế, điểm dừng | Liệt kê mọi trạng thái → **3.3** |
| Ưu đãi / mã giảm giá | Một phần | Bảng `promotions`, cột giảm giá ở `bookings` | API + áp mã khi giữ ghế → **4.2** |
| Thanh toán | Hoàn thành (BE, chờ E2E) | Stripe Checkout + webhook + tự hoàn tiền khi ghế hết hạn; `/pay` demo offline | Chạy E2E bằng khoá test → **6.2** |
| Khách vãng lai đặt vé | Hoàn thành (BE) | `POST /api/bookings` không JWT, `X-Guest-Token`, `POST /api/bookings/lookup`, `POST /api/bookings/claim`, checkout Stripe cho khách, `bookingCode` trong response | Tích hợp FE; email vé → **5.2** |
| Email vé / QR | Chưa có | Email cho luồng tài khoản (Mailpit, retry) | QR sinh ở FE; email vé → **5.2** (tuỳ chọn) |
| Frontend | Chưa có | Thiết kế ở HUONG_DAN_FRONTEND.md | → **Giai đoạn F** |
| Kiểm thử | Một phần | Test tự động backend (booking 100, payment 19, auth/user/gateway) | Chạy lại toàn bộ; E2E → **6.1** |
| Chạy local | **Lỗi cấu hình đã sửa** | Compose đủ service, Swagger, seed | Xem mục "Cổng gateway" bên dưới |
| Deploy | Chưa có | Dockerfile/Compose local | → **6.3** (điểm cộng) |

### Cổng gateway (đã chốt 2026-10-22): **8088**, không dùng 8080

Gateway nghe **8088** ở mọi cách chạy: `api-gateway/application.yml` (`server.port: 8088`), Docker
(`SERVER_PORT=8088`, publish `${GATEWAY_PORT:-8088}:8088`), Dockerfile `EXPOSE`, Swagger "servers",
script E2E. **Mọi tài liệu dùng `http://localhost:8088`**; frontend đặt `VITE_API_BASE_URL=http://localhost:8088`.
(Trước đó compose để container nghe 8088 nhưng publish `8088:8080` nên gateway không gọi được — đã sửa.)

## Quyết định đã chốt (đề cử)

Các câu hỏi mở trước đây, chốt theo hướng **ít rủi ro nhất cho đồ án demo**, tận dụng phần đã code.
Nhóm muốn đổi thì sửa tại đây trước, rồi mới sửa tài liệu khác.

| # | Câu hỏi | Chốt | Lý do |
|---|---|---|---|
| Q1 | Cổng thanh toán | **Stripe (test mode) là chính** + `/pay` demo cho chạy offline. VNPAY là điểm cộng, chỉ làm nếu dư thời gian | Stripe đã code + 19 test; VNPAY cần đăng ký sandbox, viết lại ký HMAC. Ghi lý do đổi so với đặc tả vào báo cáo |
| Q2 | Đơn chuyển `COMPLETED` khi nào | **Tự động** khi `arrivalTime < now` (job 5 phút) + nút Admin "Hoàn tất ngay" để demo | Không phụ thuộc thao tác tay; vẫn demo được ngay |
| Q3 | Quy tắc đánh giá | **Một đánh giá cho mỗi đơn `COMPLETED`** (UK `booking_id`, giữ cả UK `(user_id, trip_id)`); được sửa/xoá của mình; Admin ẩn (`is_hidden`). Điểm hiển thị **theo nhà xe** | Khớp schema đã áp dụng; điểm theo chuyến gần như luôn 0–1 đánh giá |
| Q4 | Định nghĩa dashboard | Doanh thu = Σ`payments.amount` (`SUCCESS`,`REFUNDED`) − Σ`refund_amount`, theo `payments.created_at`. Vé bán = số `booking_details` của đơn `PAID/COMPLETED` theo `bookings.created_at`. Lấp đầy = ghế `BOOKED` / tổng ghế của chuyến **không huỷ** khởi hành trong kỳ. Mặc định 30 ngày, tối đa 366 | Một định nghĩa duy nhất cho BE và FE |
| Q5 | Tab Giảm giá/Chính sách/Ảnh/Tiện ích | **Bắt buộc** (requirement liệt kê), gắn theo **nhà xe**, một API `GET /api/bus-companies/{id}/profile` | Bảng đã có; một request cho cả panel |
| Q6 | Khách vãng lai đặt vé | **Cho đặt vé không cần đăng nhập (như Vexere) — đã làm 2026-10-22**. Bấm "Chọn chỗ" không chặn, chỉ gợi ý đăng nhập để tự điền. Khách vãng lai bắt buộc email + SĐT; **không** được đánh giá. Gắn vé vào tài khoản bằng mã vé + SĐT | Nhóm chốt đổi so với đặc tả gốc (đặc tả yêu cầu đăng nhập khi "Chọn chỗ"). Gắn vé bằng mã + SĐT thay vì theo email vì JWT không mang cờ "email đã xác minh" |
| Q7 | Email vé, QR | QR sinh ở FE từ `bookingCode` (bắt buộc). Email vé sau `PAID`: **nên làm** (P1) vì khách vãng lai chỉ có mã vé để tra cứu | Ít việc, vẫn có vé để xuất trình |
| Q8 | Mục tiêu | **Demo đồ án**; deploy là điểm cộng của TV4 | — |
| Q9 | Tên API mới | Dashboard `/api/admin/stats/*`; đánh giá `POST /api/reviews {bookingId,...}`; khuyến mãi `GET /api/promotions?featured=&tripId=`, `POST /api/promotions/validate` | Một bộ tên dùng chung cho hai hướng dẫn BE/FE |

## Lộ trình đề cử

Nguyên tắc: **lấp lỗ hổng nghiệp vụ lõi trước** (để tab "Đã đi", đánh giá, dashboard có dữ liệu),
rồi nội dung (khách vãng lai đã xong trước). **Frontend bắt đầu ngay song song** bằng mock theo API.md + bảng
"API dự kiến" ở HUONG_DAN_FRONTEND.md B10 — không chờ backend.

Cách làm chi tiết từng việc BE: [HUONG_DAN_HOAN_THIEN_BE.md](../HUONG_DAN_HOAN_THIEN_BE.md) (số Việc ghi trong ngoặc).

### Giai đoạn 3 — Nghiệp vụ lõi còn thiếu (P0, backend)

| # | Việc | Người | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| 3.0 | Chặn giữ ghế chuyến đã khởi hành (Việc nhỏ) | TV3 | — | Test 409 xanh |
| 3.1 | `TripCompletionJob` + `POST /api/admin/trips/{id}/complete` (Việc 1) | TV2 | 3.0 | Đơn `PAID` → `COMPLETED` |
| 3.2 | Huỷ chuyến từ Admin, trả ghế, trả DS đơn cần hoàn tiền (Việc 2) | TV4 | — | Test huỷ 2 lần, huỷ chuyến `COMPLETED` 409 |
| 3.3 | `GET /api/admin/trips`, `GET /api/admin/trips/{id}` (Việc 4) | TV4 | — | Thấy chuyến mọi trạng thái |
| 3.4 | Ghi đánh giá theo đơn, điểm theo nhà xe, admin ẩn (Việc 3) | TV2 | 3.1 | Checklist Việc 3.8 |
| 3.5 | `/api/admin/stats/*` + route gateway (Việc 5) | TV4 | 3.1, 3.2 | Số liệu khớp dữ liệu dựng tay |
| 3.6 | Đồng bộ Swagger: `PaymentRequest` có `STRIPE`; API.md cập nhật | Người làm việc đó | — | — |
| 3.7 | Seed: ảnh, tiện ích, chính sách, quy tắc hoàn huỷ, 4–6 khuyến mãi, `routes.is_featured` | TV2 | — | Trang chi tiết có nội dung |

### Giai đoạn 4 — Nội dung & tìm kiếm (P1, backend)

| # | Việc | Người | Phụ thuộc |
|---|---|---|---|
| 4.1 | `GET /api/bus-companies/{id}/profile` + CRUD admin ảnh/tiện ích/chính sách/quy tắc (Việc 8) | TV2 | 3.7 |
| 4.2 | Khuyến mãi: đọc, `validate`, áp mã khi giữ ghế, `used_count` khi `PAID`, CRUD admin (Việc 9) | TV1 đọc · TV3 áp mã · TV4 CRUD | 3.7 |
| 4.3 | Tuyến phổ biến (`featured` + theo vé bán, kèm `minPrice`) (Việc 7) | TV1 | — |
| 4.4 | Lọc điểm đón/trả, `busType`, nhiều `companyId`; trả `rating` nhà xe (Việc 6) | TV1 | 3.4 |
| 4.5 | `GET /api/admin/bookings` thêm `keyword/from/to`; admin huỷ đơn kèm lý do | TV4 | — |

### Giai đoạn 5 — Khách vãng lai (backend — phần chính đã xong)

| # | Việc | Người | Phụ thuộc |
|---|---|---|---|
| 5.1 | ~~Đặt vé không JWT, `X-Guest-Token`, lookup, claim, payment-service nhận guest token (Việc 11)~~ **XONG 2026-10-22** | TV3 | — |
| 5.2 | Email vé sau `PAID` (mã vé, link tra cứu) | TV3 | 5.1 |
| 5.3 | Rate limit riêng cho `POST /api/bookings` (không JWT) và `/lookup` theo IP, chống giữ ghế ảo | TV3 | 5.1 |

### Giai đoạn F — Frontend (bắt đầu ngay, song song)

Thứ tự trong [HUONG_DAN_FRONTEND.md](../HUONG_DAN_FRONTEND.md) B11. Tuần đầu: khung dự án, design
system, `http.ts` (refresh token), layout — TV1 dựng, cả nhóm dùng chung. Sau đó mỗi người làm trang
của mình, nối API thật khi việc BE tương ứng xong.

### Giai đoạn 6 — Nghiệm thu & điểm cộng

| # | Việc | Người |
|---|---|---|
| 6.1 | Chạy lại toàn bộ test; checklist toàn luồng (BE guide cuối file) và FE B12 | Cả nhóm |
| 6.2 | E2E Stripe test mode: thành công, huỷ ở cổng, webhook lặp, trả sau hết hạn (tự hoàn tiền) | TV3 |
| 6.3 | Flyway thay `ddl-auto=update` (BE guide 0.6); deploy BE + FE | TV4 |
| 6.4 | Điểm cộng: tìm kiếm AI Gemini (TV1), VNPAY (Việc 10, TV3) | — |

## Những phần chưa cần tách service

- Review, dashboard, khuyến mãi, nội dung nhà xe nằm trong `booking-service` (dữ liệu liên quan trực tiếp
  đến chuyến và đơn — xem db.md B1).
- Frontend là ứng dụng client riêng (`ride_booking_frontend/`), không phải microservice.
- Chỉ cân nhắc notification-service khi cần gửi thông báo độc lập ở quy mô lớn.

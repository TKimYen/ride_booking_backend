// ==========================================
// 1. MODULE TÀI KHOẢN & PHÂN QUYỀN (USER)
// ==========================================
// LƯU Ý: đây là mô hình logic theo đặc tả. Khi triển khai thật, bảng Users đã
// được TÁCH LÀM 2 bảng ở 2 database khác nhau (xem mục "A. Database vật lý"
// cuối file) để auth-service và user-service không phụ thuộc dữ liệu của nhau.
Table Users {
  id int [pk, increment, note: 'Khóa chính']
  full_name varchar [note: 'Họ và tên để auto-fill']
  phone varchar [note: 'Số điện thoại liên hệ']
  email varchar [unique, note: 'Dùng để đăng nhập và gửi vé']
  password varchar [note: 'Mật khẩu đã mã hóa (BCrypt)']
  dob date [note: 'Ngày sinh']
  gender varchar [note: 'Nam, Nữ, Khác']
  role varchar [note: 'Quyền: GUEST, USER, ADMIN']
  created_at timestamp
}

// ==========================================
// 2. MODULE CHUYẾN XE (CORE CATALOG)
// ==========================================
Table Locations {
  id int [pk, increment]
  name varchar [note: 'Tên Tỉnh/Thành phố (vd: Hà Nội, Hà Giang)']
  slug varchar [unique, note: 'Sinh từ name, dùng cho URL/SEO; chỉ chữ thường, số, gạch ngang']
}

Table Bus_Companies {
  id int [pk, increment]
  name varchar [note: 'Tên nhà xe (vd: G8 SAPA OPEN TOUR)']
  description text
}

Table Routes {
  id int [pk, increment]
  origin_id int [ref: > Locations.id, note: 'Nơi xuất phát']
  destination_id int [ref: > Locations.id, note: 'Nơi đến']
  name varchar [note: 'Tên tuyến (vd: Hà Nội - Sapa)']
}

Table Trips {
  id int [pk, increment]
  route_id int [ref: > Routes.id]
  company_id int [ref: > Bus_Companies.id]
  bus_type varchar [note: 'Loại xe (vd: Limousine 24 chỗ, Cabin 22 phòng)']
  departure_time datetime [note: 'Giờ khởi hành']
  arrival_time datetime [note: 'Giờ đến dự kiến']
  base_price decimal [note: 'Giá vé cơ bản']
  status varchar [note: 'Trạng thái: ACTIVE, CANCELLED, COMPLETED']
}

// ==========================================
// 3. MODULE ĐIỂM ĐÓN/TRẢ (STOP POINTS)
// ==========================================
Table Stop_Points {
  id int [pk, increment]
  trip_id int [ref: > Trips.id]
  name varchar [note: 'Tên văn phòng/trạm (vd: Văn phòng Tam Cốc)']
  address varchar [note: 'Địa chỉ chi tiết']
  point_type varchar [note: 'Loại: PICKUP (Đón) hoặc DROPOFF (Trả)']
  stop_order int [note: 'Thứ tự trong NHÓM cùng point_type; đánh lại khi thêm/xoá/đổi loại']
  estimated_time datetime [note: 'Giờ đón/trả dự kiến']
}

// ==========================================
// 4. MODULE ĐẶT VÉ & CHỐNG ĐỤNG ĐỘ GHẾ (BOOKING)
// ==========================================
Table Trip_Seats {
  id int [pk, increment]
  trip_id int [ref: > Trips.id]
  seat_name varchar [unique with trip_id, note: 'Tên ghế (vd: 1A1 tầng 1, 2A1 tầng 2). Xe 2 tầng gắn tiền tố tầng để không trùng']
  floor int [note: 'Tầng: 1 (Tầng dưới), 2 (Tầng trên)']
  seat_type varchar [note: 'Loại chỗ: SINGLE hoặc DOUBLE']
  extra_price decimal [note: 'Giá chênh lệch (vd: ghế đôi +250k; cột sát lối đi +50k)']
  status varchar [note: 'Trạng thái: AVAILABLE (Trống), HOLDING (Đang giữ), BOOKED (Đã bán)']
  holding_until datetime [note: 'Thời gian hết hạn giữ chỗ (Phục vụ đếm ngược 10p)']
  updated_at timestamp [note: 'Job trả ghế HOLDING quá hạn về AVAILABLE dựa vào cột này']
}

Table Bookings {
  id int [pk, increment]
  user_id int [ref: > Users.id, note: 'Ai là người đặt']
  trip_id int [ref: > Trips.id]
  pickup_point_id int [ref: > Stop_Points.id]
  dropoff_point_id int [ref: > Stop_Points.id]
  contact_name varchar [note: 'Tên người đi (có thể đặt hộ)']
  contact_phone varchar
  contact_email varchar
  total_price decimal [note: 'Tổng tiền phải thanh toán']
  status varchar [note: 'Trạng thái đơn: PENDING, PAID, CANCELLED, COMPLETED']
  created_at timestamp
  expires_at timestamp [note: 'Hết hạn thanh toán (Giữ ghế 10p)']
}

Table Booking_Details {
  id int [pk, increment]
  booking_id int [ref: > Bookings.id]
  seat_id int [ref: > Trip_Seats.id, note: 'Khóa ghế nào']
  price decimal [note: 'Giá chốt mua lúc đó (Tránh sau này nhà xe đổi giá làm sai lệch bill)']
}

// ==========================================
// 5. MODULE HẬU MÃI (REVIEWS)
// ==========================================
Table Reviews {
  id int [pk, increment]
  user_id int [ref: > Users.id]
  trip_id int [ref: > Trips.id]
  rating int [note: 'Số sao 1-5']
  comment text [note: 'Nội dung nhận xét']
  created_at timestamp
}

// ==========================================
// 6. MODULE LỊCH SỬ GIAO DỊCH (PAYMENTS)
// ==========================================
Table Payments {
  id int [pk, increment]
  booking_id int [ref: > Bookings.id, note: 'Thanh toán cho đơn hàng nào']
  payment_gateway varchar [note: 'Cổng thanh toán: STRIPE, VNPAY, MOMO, ZALOPAY']
  transaction_no varchar [note: 'Mã giao dịch trả về từ ngân hàng/cổng API']
  amount decimal [note: 'Số tiền thực tế đã giao dịch']
  bank_code varchar [note: 'Ngân hàng sử dụng (vd: NCB, Vietcombank)']
  response_code varchar [note: 'Mã phản hồi từ API (vd: 00 là thành công)']
  status varchar [note: 'Trạng thái giao dịch: SUCCESS, FAILED, PENDING']
  created_at timestamp
}
// ==========================================
// A. DATABASE VẬT LÝ ĐANG TRIỂN KHAI
// ==========================================
// Module Tài khoản được tách thành 2 database để mỗi service tự chủ dữ liệu.
// Catalog + booking nằm ở database thứ ba. Ba database chạy trong 3 container
// PostgreSQL riêng (xem docker-compose.yml).
//
// SQL tạo schema: database/01_auth_db.sql, 02_user_db.sql, 03_booking_db.sql
//   - Idempotent, chạy lại nhiều lần vẫn an toàn.
//   - Local: không cần chạy, Hibernate tự tạo (JPA_DDL_AUTO=update).
//   - Production: đặt JPA_DDL_AUTO=validate rồi chạy 3 file trên một lần:
//       psql -U postgres -d auth_db    -f database/01_auth_db.sql
//       psql -U postgres -d user_db    -f database/02_user_db.sql
//       psql -U postgres -d booking_db -f database/03_booking_db.sql
//     Khi đó Hibernate chỉ kiểm tra schema khớp, không tự sửa.
//
// Cổng: auth-db 5433, user-db 5434, booking-db 5435 (đều map từ 5432 trong
// container để không đụng PostgreSQL cài trên máy). booking-service local mặc
// định đọc cổng 5435; trong Docker compose ghi đè DB_PORT=5432.

// ---- auth_db : do auth-service sở hữu ----
Table users {
  id int [pk, increment, note: 'Sinh ở đây, user_profiles.user_id trỏ tới id này']
  created_at timestamp
  email varchar [unique, note: 'Nguồn chuẩn của email đăng nhập']
  full_name varchar [note: 'Chỉ giữ bản tối thiểu, nguồn chuẩn của họ tên là user_db']
  password varchar [note: 'BCrypt, không bao giờ trả về ra API']
  role varchar [note: 'Quyền: GUEST, USER, ADMIN. Nguồn chuẩn']
  last_login_at timestamp [note: 'Lần đăng nhập thành công gần nhất']
  email_verified boolean [note: 'Đã bấm link xác minh trong email chưa. NULL/TRUE mới cho đăng nhập khi bật EMAIL_VERIFY_REQUIRED_FOR_LOGIN']
  email_verified_at timestamp [note: 'Thời điểm xác minh, NULL = chưa xác minh']
}

  // Bộ đếm đăng nhập sai KHÔNG nằm ở users nữa — xem login_attempts bên dưới.
  // users.failed_login_attempts / users.locked_until là schema cũ, đã bỏ khỏi
  // entity; script upgrade vẫn để lại cho tới khi dọn dữ liệu.

Table login_attempts {
  id bigint [pk, increment]
  email varchar [note: 'Email chuẩn hoá (lowercase). Email không tồn tại cũng được ghi, để không lộ qua hành vi']
  ip_address varchar [note: 'IP client do proxy tin cậy phân giải. Không xác định thì ghi 'unknown'']
  failed_attempts int [note: 'Số lần sai cho cặp này. >= 5 thì chặn cặp trong locked_until']
  locked_until timestamp [note: 'NULL = chưa bị chặn. Chỉ chặn CẶP này, không chặn cả tài khoản']
  first_failed_at timestamp
  last_failed_at timestamp
}

  // UNIQUE (email, ip_address) — bắt buộc, nếu không request song song tạo hai
  // dòng và chia nhỏ bộ đếm, giới hạn bị lách. Unique index này cũng phục vụ
  // truy vấn SELECT ... FOR UPDATE khi tăng bộ đếm, nên không cần index riêng.
  // Bỏ chặn: PATCH /api/admin/users/{id}/unlock hoặc job dọn dữ liệu.

Table email_tokens {
  id bigint [pk, increment]
  user_id int [ref: > users.id, note: '= users.id. ON DELETE CASCADE']
  purpose varchar [note: 'VERIFY | RESET_PASSWORD | CHANGE_EMAIL']
  token_hash varchar [note: 'SHA-256 hex 64 ký tự của token ngẫu nhiên 256-bit. KHÔNG lưu bản gốc']
  new_email varchar [note: 'Chỉ dùng cho CHANGE_EMAIL: email đang chờ xác nhận']
  expires_at timestamp [note: 'VERIFY +24h, RESET_PASSWORD +30 phút, CHANGE_EMAIL +60 phút']
  used_at timestamp [note: 'NULL = chưa dùng. Một token chỉ dùng được 1 lần']
  created_at timestamp
  ip_address varchar [note: 'IP gửi mail, phục vụ điều tra']
}

Table email_send_log {
  id bigint [pk, increment]
  recipient varchar [note: 'Người nhận, dùng để áp hạn mức gửi theo người nhận']
  kind varchar [note: 'Loại thư, ví dụ EMAIL_VERIFICATION, PASSWORD_RESET']
  status varchar [note: 'SENT | FAILED. Ghi cả lần lỗi để kẻ spam không lách được bằng cách làm hỏng']
  error_message varchar [note: 'Lý do thất bại, phục vụ debug. Không để lộ chi tiết ra API']
  ip_address varchar [note: 'IP người gửi, dùng để áp hạn mức theo IP']
  created_at timestamp
}

  // Hạn mức: EMAIL_MAX_PER_EMAIL và EMAIL_MAX_PER_IP lần trong
  // EMAIL_RESEND_WINDOW_MINUTES. Job dọn dòng cũ hơn EMAIL_HISTORY_RETENTION_DAYS.

Table refresh_tokens {
  id bigint [pk, increment]
  user_id int [ref: > users.id, note: '= users.id. ON DELETE CASCADE: xoá tài khoản là xoá luôn các phiên']
  token_hash varchar [unique, note: 'SHA-256 hex 64 ký tự của refresh token. KHÔNG lưu bản gốc. Có CHECK bắt buộc đúng 64 ký tự hex']
  expires_at timestamp [note: 'Mặc định +7 ngày từ lúc cấp']
  revoked_at timestamp [note: 'NULL = còn hiệu lực. Mỗi lần refresh là token cũ bị đánh dấu ở đây']
  created_at timestamp
  user_agent varchar [note: 'Trình duyệt/thiết bị lúc cấp token, phục vụ quản lý phiên']
  ip_address varchar [note: 'IPv4/IPv6 lúc đăng nhập']
}

  // Vòng đời refresh token:
  //   đăng nhập -> tạo dòng (revoked_at = NULL)
  //   refresh   -> set revoked_at = now() cho dòng vừa dùng, tạo dòng mới
  //   dùng lại dòng đã có revoked_at -> coi là bị đánh cắp, set revoked_at cho
  //     MỌI dòng cùng user_id rồi trả 401
  //   đổi mật khẩu / đăng xuất toàn bộ -> set revoked_at cho mọi dòng
  //   job 03:00 mỗi đêm xoá dòng có expires_at < now() - 30 ngày

// ---- user_db : do user-service sở hữu ----
Table user_profiles {
  user_id int [pk, note: '= users.id, sinh ở auth_db nên không có increment']
  created_at timestamp
  updated_at timestamp
  full_name varchar [note: 'Nguồn chuẩn, người dùng tự sửa ở Trang Thông tin tài khoản']
  email varchar [note: 'Bản sao từ auth_db để tìm kiếm được']
  phone varchar
  dob date
  gender varchar [note: 'Nam, Nữ, Khác']
  role varchar [note: 'Bản sao từ auth_db.users.role, làm mới mỗi lần đăng nhập']
}

// ---- booking_db : do booking-service sở hữu ----
// Catalog (Locations, Bus_Companies, Routes, Trips, Stop_Points, Trip_Seats) và
// Booking (Bookings, Booking_Details, Reviews, Payments) nằm CHUNG MỘT database.
// Xem mục B để biết vì sao và ràng buộc nào đã chốt.
Table booking_db_placeholder {
  _ note 'Schema thật: database/03_booking_db.sql — xem mục B'
}

// ==========================================
// B. QUYẾT ĐỊNH ĐÃ CHỐT VỀ BOOKING_DB
// ==========================================
// Trạng thái: CHỐT, đã có file SQL và code chạy được.
// Bộ ràng buộc dưới đây là những chỗ CỐT KHÔNG được bỏ, xem lý do từng cái.

// ---------------------------------------------------------------- B1
//  Catalog và Booking CHUNG service, CHUNG database. KHÔNG tách.
//
//  Lý do: nghiệp vụ giữ ghế 10 phút chống đụng độ (requirement.md, Thành viên 3 —
//  phần bị gọi là khó nhất) cần khoá dòng trip_seats rồi ghi bookings trong CÙNG
//  một transaction. Tách thành 2 database sẽ mất transaction, buộc phải tự viết
//  distributed lock — tức là tự tạo ra chính lỗi khó nhất của bài toán.
//
//  Hệ quả phải chấp nhận: booking-service giữ cả catalog lẫn đơn hàng, nên
//  nó là service lớn nhất hệ thống. Đây là đánh đổi có chủ ý.

// ---------------------------------------------------------------- B2
//  bookings.user_id và reviews.user_id KHÔNG có foreign key.
//
//  Nguyên nhân: Users đã tách sang auth_db (mục A), khác database nên không
//  khoá ngoại thật được. Dùng đúng nguyên tắc đã áp cho user_profiles.user_id.
//
//  Cách xử lý: giữ user_id dạng INTEGER thuần. Khi hiển thị tên/email người
//  đặt, booking-service gọi user-service qua /api/internal/users, gom theo lô
//  rồi cache. Xem API.md mục 9 cho quy ước gọi giữa các service.

// ---------------------------------------------------------------- B3
//  Năm ràng buộc đã thêm vào schema. Đã kiểm chứng trên PostgreSQL thật.
//  (Không có 5 cái này thì code chạy vẫn được nhưng hỏng âm thầm về sau.)

//  1. UNIQUE (trip_id, seat_name) trên trip_seats
//     Chống sinh trùng ghế. Logic sinh ghế chạy lại khi thêm chuyến, sửa
//     bus_type, hay chạy lại seed. Không có UK thì sơ đồ ghế hiện trùng chỗ
//     trống giả, và bước 4 (giữ ghế) sẽ cho hai người giữ cùng một tên ghế.

//  2. INDEX (trip_id, status) trên trip_seats
//     trip_seats là bảng lớn nhất hệ thống. Cả dựng sơ đồ ghế lẫn đếm ghế
//     trống đều lọc đúng cặp cột này.

//  3. INDEX (user_id, status) trên bookings
//     Trang "Đơn hàng của tôi" (requirement.md) chia 3 tab Hiện tại / Đã đi /
//     Đã hủy — nghĩa là lọc theo user_id rồi theo status.

//  4. UNIQUE (user_id, trip_id) trên reviews
//     Một người đánh giá một chuyến đúng một lần. Điều kiện "chuyến đã đi" ở
//     Trang Đánh giá vốn đã đảm bảo một chuyến cho mỗi người, nhưng không có
//     UK thì bấm hai lần vẫn ra hai dòng và điểm trung bình ở Trang chi tiết
//     bị tính sai.

//  5. INDEX (route_id, departure_time) trên trips
//     Phục vụ đúng truy vấn của Trang Danh sách kết quả: lọc theo tuyến rồi
//     sắp xếp theo giờ đi (mặc định "sớm nhất").

// ---------------------------------------------------------------- B4
//  bookings.status có 5 giá trị: PENDING, PAID, CANCELLED, COMPLETED, EXPIRED.
//  db.md cũ mới ghi 4, thiếu EXPIRED — cần nó để phân biệt "khách bỏ quên,
//  hết 10 phút" với "khách chủ động huỷ". Cả hai đều trả ghế về AVAILABLE
//  nhưng ý nghĩa với hành khách khác nhau.

// ---------------------------------------------------------------- B5
//  Cố ý KHÔNG đặt UNIQUE(seat_id) trên booking_details.
//
//  Ghế phải bán lại được sau khi huỷ đơn, nên UNIQUE(seat_id) sẽ chặn nhầm.
//  Ràng buộc "một ghế chỉ có một đơn đang hiệu lực" trải qua hai bảng nên không
//  biểu diễn được bằng UNIQUE đơn giản — nó được bảo vệ bằng khoá dòng
//  SELECT ... FOR UPDATE trên trip_seats.status (Giai đoạn 2).

// ---------------------------------------------------------------- B6
//  trips.bus_type chỉ nhận 3 giá trị, có CHECK ở tầng DB:
//    LIMOUSINE_29     Limousine 29 chỗ, 2 tầng, ghế đơn và ghế đôi
//    CABIN_DOUBLE_22  Cabin 22 phòng đôi, 2 tầng, mỗi phòng 2 ghế
//    SLEEPER_45       Giường nằm 45 chỗ, 1 tầng
//  Giá trị này QUYẾT ĐỊNH SƠ ĐỒ GHẾ (số ghế, số tầng, loại ghế, giá chênh
//  lệch). Enum tương ứng: BusType trong booking-service.
//  Thêm loại xe mới thì sửa cả CHECK trong 03_booking_db.sql và enum trong code.

// ---------------------------------------------------------------- B7
//  Cách viết file SQL: mọi CONSTRAINT đặt bằng ALTER TABLE ở CUỐI bảng,
//  KHÔNG viết inline trong CREATE TABLE.
//
//  Lý do rất cụ thể, đã kiểm chứng: ở local ta chạy JPA_DDL_AUTO=update nên
//  Hibernate tạo bảng TRƯỚC khi file SQL được chạy. Khi đó
//  CREATE TABLE IF NOT EXISTS bị bỏ qua hoàn toàn, kéo theo mọi CONSTRAINT
//  viết inline cũng biến mất. Thử nghiệm với trip_seats cho thấy
//  UNIQUE (trip_id, seat_name) không tồn tại sau khi chạy script.
//  ALTER TABLE thì không bị bỏ qua. PostgreSQL không có
//  "ADD CONSTRAINT IF NOT EXISTS" nên dùng DROP CONSTRAINT IF EXISTS + ADD.

// ==========================================
// C. LỘ TRÌNH ĐÃ CHỐT
// ==========================================
// Nhóm trước đây chỉ chia theo người (requirement.md mục 2), chưa có thứ tự
// làm. Mục này chốt thứ tự để không ai phải tự đoán phần còn thiếu.
//
//  Giai đoạn 1 — Catalog + DB nền        [XONG]
//    Làm sao sớm nhất vì TV1, TV2, TV4 đều đang bị chặn vì thiếu dữ liệu tuyến
//    và chuyến xe. Thứ tự làm trong giai đoạn:
//      1. booking_db + compose + pom + API.md    (hạ tầng)              [xong]
//      2. Locations, Bus_Companies, Routes       (không FK về nhau)    [xong]
//      3. Trips + API tìm kiếm có phân trang    (TV1 cần)             [xong]
//      4. Trip_Seats: sinh ghế tự động + API đọc trạng thái (chưa giữ ghế) [xong]
//      5. Stop_Points                                                  [xong]
//      6. Seed data: vài tỉnh, 3-4 nhà xe, 4-5 tuyến, vài chuyến có ghế thật [xong]
//      7. Reviews: bảng + API đọc             (TV2 cần tab Đánh giá)   [xong]
//    Xong giai đoạn này thì 3/4 người có việc để làm song song.
//
//    Đã kiểm chứng trên PostgreSQL thật (JPA_DDL_AUTO=validate) và qua gateway:
//      - Seed: 20 địa điểm, 6 nhà xe, 15 tuyến, 225 chuyến, 8850 ghế.
//      - Tìm kiếm trả kèm availableSeats bằng 3 câu truy vấn cho cả trang.
//      - Sơ đồ ghế 2 tầng tên 1A1/2A1; điểm đón/trả đánh số riêng.
//      - 84 test booking (BusType, SeatGenerator, TripSort, luồng tích hợp
//        tìm kiếm/ghế/điểm dừng, client user-service) chạy xanh.
//      - API.md mục 11 là danh sách endpoint đầy đủ của service.
//
//    Các lỗi tìm thấy khi viết test tích hợp, đã sửa (để không tái phạm):
//      - Đổi loại xe sinh ghế theo loại CŨ vì gán busType sau khi generate.
//      - Đổi pointType chỉ đánh lại nhóm mới, nhóm cũ hở số thứ tự.
//      - Đếm ghế put đè thay vì cộng, nên chuyến vừa BOOKED vừa HOLDING bị thiếu.
//      - sort=priceAsc (giá trị Swagger ghi) rơi về sắp xếp mặc định.
//      - slug tiếng Việt làm mất chữ "đ" (a-nang thay vì da-nang).
//
//  Giai đoạn 2 — Đặt vé + giữ ghế + thanh toán      [XONG]
//    Bookings, Booking_Details, Payments. Schema ĐÃ có sẵn trong
//    03_booking_db.sql từ giai đoạn 1, chỉ còn viết code.
//    Trọng tâm là thuật toán giữ ghế 10 phút và chống đụng độ ghế.
//    Làm sau vì đây là phần khó nhất, cần catalog đã ổn định để test.
//
//    Đã kiểm chứng: entity Booking/BookingDetail/Payment + enum
//    BookingStatus/PaymentStatus/PaymentGateway; BookingService giữ ghế,
//    thanh toán, huỷ, danh sách; BookingExpiryJob; BookingController và
//    AdminBookingController. 100 test booking chạy xanh, gồm:
//      - BookingFlowTest: 15 ca (giữ/hết hạn/thanh toán idempotent/huỷ/quyền/
//        danh sách/job).
//      - BookingConcurrencyTest: 2 luồng giành CÙNG một ghế trên transaction
//        riêng — đúng một người thắng, người kia nhận 409.
//    Chống bán trùng dựa vào SELECT ... FOR UPDATE trên trip_seats.status,
//    KHÔNG bằng UNIQUE (ràng buộc trải qua bookings + booking_details).
//    Ghế HOLDING quá hạn được giành lại ngay ở luồng giữ, job chỉ là lưới an toàn.
//
//  Giai đoạn 3 — Đánh giá ghi, quản trị, thống kê
//    Viết đánh giá (nửa đọc đã có ở bước 7), Trang Quản lý Đơn hàng, Dashboard
//    thống kê. Cần dữ liệu đơn hàng thật nên phải sau giai đoạn 2.
//
//  Việc CHƯA có owner rõ ràng, cần nhóm bàn:
//    - payment-service: ĐÃ dựng (8084) với Stripe Checkout + webhook. Không có
//      database riêng: ghi bảng payments qua API nội bộ của booking-service.
//    - Các bảng trong db.md mà requirement.md chưa giao ai: hình ảnh chuyến,
//      chính sách, tiện ích (những tab của Trang chi tiết). Hiện db.md chưa
//      có bảng nào cho chúng — nếu TV2 làm tab này thì cần chốt bảng trước.
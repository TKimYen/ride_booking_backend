-- =============================================================================
--  booking_db — schema chuẩn cho booking-service
-- =============================================================================
--  Script IDEMPOTENT: chạy lại nhiều lần vẫn an toàn.
--
--  psql -U postgres -d booking_db -f database/03_booking_db.sql
--
--  -----------------------------------------------------------------------------
--  QUYẾT ĐỊNH KIẾN TRÚC (chốt trước cho cả nhóm, xem db.md mục "B")
--
--  Catalog (locations, bus_companies, routes, trips, stop_points, trip_seats)
--  và booking (bookings, booking_details, payments) nằm CHUNG service, CHUNG
--  database. Lý do: thuật toán giữ ghế 10 phút chống đụng độ cần khoá dòng
--  trip_seats rồi ghi bookings trong CÙNG một transaction. Tách ra hai
--  database sẽ mất transaction và phải tự viết distributed lock — tức là
--  tự tạo ra chính lỗi khó nhất của nghiệp vụ đặt vé.
--
--  File này khai báo TOÀN BỘ schema kể cả bookings / booking_details / payments,
--  dù Giai đoạn 2 mới viết code cho chúng. Mục đích là khóa schema một lần
--  (requirement.md mục 2: "Toàn bộ thiết kế Database chung sẽ được chốt trước
--  một lần cho cả nhóm") để Thành viên 3 và Thành viên 4 không phải tự đoán.
--
--  -----------------------------------------------------------------------------
--  VỀ CÁCH VIẾT RÀNG BUỘC — ĐỌC TRƯỚC KHI SỬA FILE NÀY
--
--  Mọi CONSTRAINT (khoá ngoại, CHECK, UNIQUE) được đặt bằng ALTER TABLE ở
--  CUỐI mỗi bảng, KHÔNG viết inline trong CREATE TABLE. Lý do rất cụ thể:
--
--  Ở local ta chạy JPA_DDL_AUTO=update, nên Hibernate tự tạo bảng TRƯỚC khi
--  file này được chạy. Khi đó CREATE TABLE IF NOT EXISTS bị BỎ QUA HOÀN TOÀN,
--  kéo theo mọi CONSTRAINT viết inline cũng biến mất theo. Đã kiểm chứng:
--  tạo trip_seats bằng tay không ràng buộc, chạy script này, kết quả là
--  UNIQUE (trip_id, seat_name) KHÔNG tồn tại -> vẫn sinh trùng ghế.
--
--  ALTER TABLE thì khác: câu lệnh luôn chạy, không bị "relation already
--  exists" bỏ qua. Theo khuôn đã dùng ở 02_user_db.sql.
--  PostgreSQL không có "ADD CONSTRAINT IF NOT EXISTS", nên dùng
--  DROP CONSTRAINT IF EXISTS rồi ADD CONSTRAINT.
-- =============================================================================

-- =============================================================================
--  1. CATALOG: ĐỊA ĐIỂM / NHÀ XE / TUYẾN
-- =============================================================================

-- ------------------------------------------------------------- locations
-- Nguồn cho dropdown "Nơi đi" / "Nơi đến" ở Trang chủ (Thành viên 1).
-- slug dùng để dựng URL thân thiện, name là thứ hiện ra cho người dùng.
CREATE TABLE IF NOT EXISTS locations (
    id          SERIAL       PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE locations DROP CONSTRAINT IF EXISTS uk_locations_slug;
ALTER TABLE locations ADD  CONSTRAINT uk_locations_slug UNIQUE (slug);

-- Tìm địa điểm theo tên khi gõ ("Hà" -> ["Hà Nội", "Hà Giang"]), chạy mỗi
-- lần gõ phím nên index theo name là bắt buộc.
CREATE INDEX IF NOT EXISTS ix_locations_name ON locations (name);

-- ------------------------------------------------------- bus_companies
CREATE TABLE IF NOT EXISTS bus_companies (
    id           SERIAL       PRIMARY KEY,
    name         VARCHAR(150) NOT NULL,
    description  TEXT,
    logo_url     VARCHAR(500),
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE bus_companies DROP CONSTRAINT IF EXISTS uk_bus_companies_name;
ALTER TABLE bus_companies ADD  CONSTRAINT uk_bus_companies_name UNIQUE (name);

CREATE INDEX IF NOT EXISTS ix_bus_companies_name ON bus_companies (name);

-- ---------------------------------------------------------------- routes
-- origin_id và destination_id cùng trỏ vào locations: đây là quan hệ trong
-- CÙNG database nên khoá ngoại thật được (khác với user_id bên bookings, xem
-- ghi chú tại bảng đó).
CREATE TABLE IF NOT EXISTS routes (
    id               SERIAL        PRIMARY KEY,
    origin_id        INTEGER       NOT NULL,
    destination_id   INTEGER       NOT NULL,
    name             VARCHAR(200)  NOT NULL,
    distance_km      NUMERIC(8, 2),
    duration_minutes INTEGER,
    created_at       TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE routes DROP CONSTRAINT IF EXISTS fk_routes_origin;
ALTER TABLE routes ADD  CONSTRAINT fk_routes_origin
    FOREIGN KEY (origin_id) REFERENCES locations (id) ON DELETE RESTRICT;

ALTER TABLE routes DROP CONSTRAINT IF EXISTS fk_routes_destination;
ALTER TABLE routes ADD  CONSTRAINT fk_routes_destination
    FOREIGN KEY (destination_id) REFERENCES locations (id) ON DELETE RESTRICT;

ALTER TABLE routes DROP CONSTRAINT IF EXISTS ck_routes_distinct;
ALTER TABLE routes ADD  CONSTRAINT ck_routes_distinct
    CHECK (origin_id <> destination_id);

ALTER TABLE routes DROP CONSTRAINT IF EXISTS ck_routes_distance;
ALTER TABLE routes ADD  CONSTRAINT ck_routes_distance
    CHECK (distance_km IS NULL OR distance_km > 0);

ALTER TABLE routes DROP CONSTRAINT IF EXISTS ck_routes_duration;
ALTER TABLE routes ADD  CONSTRAINT ck_routes_duration
    CHECK (duration_minutes IS NULL OR duration_minutes > 0);

CREATE INDEX IF NOT EXISTS ix_routes_origin      ON routes (origin_id);
CREATE INDEX IF NOT EXISTS ix_routes_destination ON routes (destination_id);
CREATE INDEX IF NOT EXISTS ix_routes_name        ON routes (name);

-- =============================================================================
--  2. CATALOG: CHUYẾN XE
-- =============================================================================

-- ----------------------------------------------------------------- trips
-- bus_type quyết định SƠ ĐỒ GHẾ (số hàng, số tầng, loại ghế, giá chênh lệch).
-- Giá trị hợp lệ khai trong enum BusType của booking-service; CHECK ở dưới là
-- lưới an toàn cuối cùng để dữ liệu ngoài (psql, import tay) không phá kịch bản
-- ghế.
CREATE TABLE IF NOT EXISTS trips (
    id               SERIAL         PRIMARY KEY,
    route_id         INTEGER        NOT NULL,
    company_id       INTEGER        NOT NULL,
    bus_type         VARCHAR(40)    NOT NULL,
    departure_time   TIMESTAMP(6)   NOT NULL,
    arrival_time     TIMESTAMP(6)   NOT NULL,
    base_price       NUMERIC(12, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP(6)
);

ALTER TABLE trips DROP CONSTRAINT IF EXISTS fk_trips_route;
ALTER TABLE trips ADD  CONSTRAINT fk_trips_route
    FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE RESTRICT;

ALTER TABLE trips DROP CONSTRAINT IF EXISTS fk_trips_company;
ALTER TABLE trips ADD  CONSTRAINT fk_trips_company
    FOREIGN KEY (company_id) REFERENCES bus_companies (id) ON DELETE RESTRICT;

ALTER TABLE trips DROP CONSTRAINT IF EXISTS ck_trips_bus_type;
ALTER TABLE trips ADD  CONSTRAINT ck_trips_bus_type CHECK (bus_type IN (
    'LIMOUSINE_29', 'CABIN_DOUBLE_22', 'SLEEPER_45'
));

ALTER TABLE trips DROP CONSTRAINT IF EXISTS ck_trips_status;
ALTER TABLE trips ADD  CONSTRAINT ck_trips_status CHECK (status IN (
    'ACTIVE', 'CANCELLED', 'COMPLETED'
));

-- Giờ đến phải sau giờ khởi hành. Chặn ở tầng DB để dữ liệu nhập tay không
-- tạo ra chuyến vô nghĩa.
ALTER TABLE trips DROP CONSTRAINT IF EXISTS ck_trips_time_order;
ALTER TABLE trips ADD  CONSTRAINT ck_trips_time_order
    CHECK (arrival_time > departure_time);

ALTER TABLE trips DROP CONSTRAINT IF EXISTS ck_trips_price;
ALTER TABLE trips ADD  CONSTRAINT ck_trips_price CHECK (base_price >= 0);

-- Truy vấn chính của Trang Danh sách kết quả (Thành viên 1): lọc theo tuyến
-- rồi sắp xếp theo giờ đi. Index (route_id, departure_time) phục vụ đúng
-- truy vấn đó, không có thì mỗi lần tìm kiếm đều full scan toàn bộ bảng trips.
CREATE INDEX IF NOT EXISTS ix_trips_route_departure ON trips (route_id, departure_time);
-- Trang chủ và bộ lọc "theo nhà xe" lọc theo company_id.
CREATE INDEX IF NOT EXISTS ix_trips_company ON trips (company_id);
-- Bộ lọc mặc định chỉ hiện chuyến ACTIVE, sắp xếp sớm nhất: index phủ status.
CREATE INDEX IF NOT EXISTS ix_trips_status_departure ON trips (status, departure_time);

-- ============================================================ stop_points
-- Điểm đón / trả. Một chuyến có nhiều điểm, mỗi điểm thuộc loại PICKUP hoặc
-- DROPOFF (db.md dùng chung một bảng cho cả hai).
CREATE TABLE IF NOT EXISTS stop_points (
    id              SERIAL        PRIMARY KEY,
    trip_id         INTEGER       NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    address         VARCHAR(300),
    point_type      VARCHAR(20)   NOT NULL,
    estimated_time  TIMESTAMP(6),
    -- Thứ tự dọc tuyến, hiển thị "đón lần 1, lần 2..." trên Trang chi tiết.
    stop_order      INTEGER       NOT NULL DEFAULT 1,
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE stop_points DROP CONSTRAINT IF EXISTS fk_stop_points_trip;
ALTER TABLE stop_points ADD  CONSTRAINT fk_stop_points_trip
    FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE;

ALTER TABLE stop_points DROP CONSTRAINT IF EXISTS ck_stop_points_type;
ALTER TABLE stop_points ADD  CONSTRAINT ck_stop_points_type
    CHECK (point_type IN ('PICKUP', 'DROPOFF'));

ALTER TABLE stop_points DROP CONSTRAINT IF EXISTS ck_stop_points_order;
ALTER TABLE stop_points ADD  CONSTRAINT ck_stop_points_order CHECK (stop_order >= 1);

CREATE INDEX IF NOT EXISTS ix_stop_points_trip ON stop_points (trip_id, point_type);

-- =============================================================================
--  3. GHẾ
-- =============================================================================
--  Sơ đồ ghế 2 tầng (Thành viên 3). Bảng lớn nhất trong hệ thống: một chuyến
--  29 chỗ cũng đã 29 dòng, chạy cả catalog lẫn lịch sử đều vào đây.
-- =============================================================================

-- -------------------------------------------------------------- trip_seats
-- status + holding_until là phần khó nhất của cả nhóm. Quy tắc:
--   AVAILABLE -> HOLDING khi khách bấm chọn ghế, kèm holding_until = now + 10p
--   HOLDING   -> AVAILABLE khi hết giờ (job quét, xem BookingService mục 2)
--   HOLDING   -> BOOKED   khi thanh toán thành công
-- Mọi chuyển trạng thái đều đi qua SELECT ... FOR UPDATE trên chính dòng ghế
-- đó, nên hai người không thể cùng giữ một ghế. Job quét chỉ đụng dòng đã hết
-- hạn nên không tranh chấp với request đang xử lý.
CREATE TABLE IF NOT EXISTS trip_seats (
    id            SERIAL         PRIMARY KEY,
    trip_id       INTEGER        NOT NULL,
    seat_name     VARCHAR(10)    NOT NULL,
    floor         INTEGER        NOT NULL,
    seat_type     VARCHAR(40)    NOT NULL DEFAULT 'STANDARD',
    extra_price   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status        VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',
    holding_until TIMESTAMP(6),
    updated_at    TIMESTAMP(6)
);

-- trip_seats đã có từ khi sơ đồ ghế còn giản dị: lúc đó bảng chỉ có id, trip_id,
-- seat_name, floor, status. Ghế đôi định giá khác nhau và cần thời điểm hết
-- giữ, nên phải thêm seat_type, extra_price, holding_until, updated_at.
--
-- CREATE TABLE IF NOT EXISTS không động tới bảng đã tồn tại, nên với database
-- cũ bốn cột này sẽ thiếu và Hibernate dừng lại với
-- "Schema validation: missing column [extra_price]". Dòng dưới vá lại đúng
-- khoảng trống đó, chạy lại nhiều lần cũng không sao.
ALTER TABLE trip_seats ADD COLUMN IF NOT EXISTS seat_type     VARCHAR(40)    NOT NULL DEFAULT 'STANDARD';
ALTER TABLE trip_seats ADD COLUMN IF NOT EXISTS extra_price   NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE trip_seats ADD COLUMN IF NOT EXISTS holding_until TIMESTAMP(6);
ALTER TABLE trip_seats ADD COLUMN IF NOT EXISTS updated_at    TIMESTAMP(6);

ALTER TABLE trip_seats DROP CONSTRAINT IF EXISTS fk_trip_seats_trip;
ALTER TABLE trip_seats ADD  CONSTRAINT fk_trip_seats_trip
    FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE;

-- Ràng buộc 1/5: chống sinh trùng ghế.
-- Không có UNIQUE này thì chạy lại logic sinh ghế (thêm chuyến, sửa
-- bus_type, chạy lại seed) sẽ tạo hai dòng cùng tên "A01" trong cùng một
-- chuyến, và sơ đồ ghế hiển thị trùng chỗ trống giả.
ALTER TABLE trip_seats DROP CONSTRAINT IF EXISTS uk_trip_seats_trip_name;
ALTER TABLE trip_seats ADD  CONSTRAINT uk_trip_seats_trip_name UNIQUE (trip_id, seat_name);

ALTER TABLE trip_seats DROP CONSTRAINT IF EXISTS ck_trip_seats_floor;
ALTER TABLE trip_seats ADD  CONSTRAINT ck_trip_seats_floor CHECK (floor IN (1, 2));

ALTER TABLE trip_seats DROP CONSTRAINT IF EXISTS ck_trip_seats_status;
ALTER TABLE trip_seats ADD  CONSTRAINT ck_trip_seats_status
    CHECK (status IN ('AVAILABLE', 'HOLDING', 'BOOKED'));

ALTER TABLE trip_seats DROP CONSTRAINT IF EXISTS ck_trip_seats_extra_price;
ALTER TABLE trip_seats ADD  CONSTRAINT ck_trip_seats_extra_price CHECK (extra_price >= 0);

-- holding_until chỉ có nghĩa khi đang HOLDING. Ghế trống hoặc đã bán thì
-- phải để NULL, nếu không lệch dữ liệu làm job quét xử lý sai.
ALTER TABLE trip_seats DROP CONSTRAINT IF EXISTS ck_trip_seats_holding;
ALTER TABLE trip_seats ADD  CONSTRAINT ck_trip_seats_holding CHECK (
    (status = 'HOLDING' AND holding_until IS NOT NULL)
    OR (status <> 'HOLDING' AND holding_until IS NULL)
);

-- Ràng buộc 2/5: đếm ghế trống và dựng sơ đồ ghế đều lọc theo (trip_id, status).
-- trip_seats là bảng lớn nhất nên index này là bắt buộc, không phải tuỳ chọn.
CREATE INDEX IF NOT EXISTS ix_trip_seats_trip_status ON trip_seats (trip_id, status);
-- Job quét ghế hết hạn: WHERE status='HOLDING' AND holding_until < now
CREATE INDEX IF NOT EXISTS ix_trip_seats_holding_until ON trip_seats (holding_until)
    WHERE status = 'HOLDING';

-- =============================================================================
--  4. ĐẶT VÉ
-- =============================================================================

-- --------------------------------------------------------------- bookings
-- user_id KHÔNG có foreign key: auth-service sinh id ở auth_db.users, khác
-- database nên không khoá ngoại thật được. Đây đúng nguyên tắc đã dùng cho
-- user_profiles.user_id — xem 02_user_db.sql.
--
-- Hiển thị tên/email người đặt thì booking-service gọi user-service
-- (/api/internal/users, batch + cache), không join DB.
CREATE TABLE IF NOT EXISTS bookings (
    id                  SERIAL         PRIMARY KEY,
    user_id             INTEGER        NOT NULL,
    trip_id             INTEGER        NOT NULL,
    pickup_point_id     INTEGER,
    dropoff_point_id    INTEGER,
    contact_name        VARCHAR(255),
    contact_phone       VARCHAR(20),
    contact_email       VARCHAR(255),
    total_price         NUMERIC(12, 2) NOT NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Hết hạn thanh toán = mốc giữ ghế 10 phút. Job quét dùng chung mốc này
    -- để trả ghế về AVAILABLE.
    expires_at          TIMESTAMP(6)
);

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS fk_bookings_trip;
ALTER TABLE bookings ADD  CONSTRAINT fk_bookings_trip
    FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE RESTRICT;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS fk_bookings_pickup;
ALTER TABLE bookings ADD  CONSTRAINT fk_bookings_pickup
    FOREIGN KEY (pickup_point_id) REFERENCES stop_points (id) ON DELETE SET NULL;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS fk_bookings_dropoff;
ALTER TABLE bookings ADD  CONSTRAINT fk_bookings_dropoff
    FOREIGN KEY (dropoff_point_id) REFERENCES stop_points (id) ON DELETE SET NULL;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_status;
ALTER TABLE bookings ADD  CONSTRAINT ck_bookings_status CHECK (status IN (
    'PENDING', 'PAID', 'CANCELLED', 'COMPLETED', 'EXPIRED'
));

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_price;
ALTER TABLE bookings ADD  CONSTRAINT ck_bookings_price CHECK (total_price >= 0);

-- Ràng buộc 3/5: tab "Đơn hàng của tôi" lọc theo user_id + status.
-- Không index thì mỗi lần mở trang đều quét toàn bộ lịch sử đặt vé.
CREATE INDEX IF NOT EXISTS ix_bookings_user_status ON bookings (user_id, status);
-- Trang Quản lý Đơn hàng của Admin lọc theo chuyến, và cần danh sách hành
-- khách theo từng chuyến (requirement.md trang Quản lý Đơn hàng).
CREATE INDEX IF NOT EXISTS ix_bookings_trip ON bookings (trip_id);
-- Job quét đơn hết hạn.
CREATE INDEX IF NOT EXISTS ix_bookings_status_expires ON bookings (status, expires_at);

-- -------------------------------------------------------- booking_details
-- price chốt lúc mua. Cột này tồn tại để giá vé nhà xe đổi sau này không làm
-- sai lệch hoá đơn cũ.
--
-- Cố tình KHÔNG đặt UNIQUE(seat_id): ghế được bán lại sau khi huỷ đơn. Ràng
-- buộc "một ghế chỉ có một đơn đang hiệu lực" KHÔNG thể biểu diễn bằng UNIQUE
-- đơn giản vì nó trải qua hai bảng — nó được bảo vệ bằng khoá dòng trên
-- trip_seats.status (SELECT ... FOR UPDATE), xem mục 3.
CREATE TABLE IF NOT EXISTS booking_details (
    id          SERIAL         PRIMARY KEY,
    booking_id  INTEGER        NOT NULL,
    seat_id     INTEGER        NOT NULL,
    price       NUMERIC(12, 2) NOT NULL
);

ALTER TABLE booking_details DROP CONSTRAINT IF EXISTS fk_booking_details_booking;
ALTER TABLE booking_details ADD  CONSTRAINT fk_booking_details_booking
    FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE;

ALTER TABLE booking_details DROP CONSTRAINT IF EXISTS fk_booking_details_seat;
ALTER TABLE booking_details ADD  CONSTRAINT fk_booking_details_seat
    FOREIGN KEY (seat_id) REFERENCES trip_seats (id) ON DELETE RESTRICT;

ALTER TABLE booking_details DROP CONSTRAINT IF EXISTS uk_booking_details_booking_seat;
ALTER TABLE booking_details ADD  CONSTRAINT uk_booking_details_booking_seat
    UNIQUE (booking_id, seat_id);

ALTER TABLE booking_details DROP CONSTRAINT IF EXISTS ck_booking_details_price;
ALTER TABLE booking_details ADD  CONSTRAINT ck_booking_details_price CHECK (price >= 0);

CREATE INDEX IF NOT EXISTS ix_booking_details_seat ON booking_details (seat_id);

-- =============================================================================
--  5. ĐÁNH GIÁ
-- =============================================================================

-- --------------------------------------------------------------- reviews
-- user_id lại là id từ auth_db, không có khoá ngoại, cùng lý do bookings.
CREATE TABLE IF NOT EXISTS reviews (
    id          SERIAL         PRIMARY KEY,
    user_id     INTEGER        NOT NULL,
    trip_id     INTEGER        NOT NULL,
    rating      INTEGER        NOT NULL,
    comment     TEXT,
    created_at  TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP(6)
);

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS fk_reviews_trip;
ALTER TABLE reviews ADD  CONSTRAINT fk_reviews_trip
    FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE;

-- Ràng buộc 4/5: một người đánh giá một chuyến đúng một lần.
-- Thiếu ràng buộc này thì bấm đánh giá hai lần sẽ ra hai dòng và điểm tổng
-- ở Trang chi tiết bị tính sai, trong khi điều kiện "chuyến đã đi" của
-- Trang Đánh giá vốn đã giữ một chuyến cho mỗi người.
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS uk_reviews_user_trip;
ALTER TABLE reviews ADD  CONSTRAINT uk_reviews_user_trip UNIQUE (user_id, trip_id);

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS ck_reviews_rating;
ALTER TABLE reviews ADD  CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5);

-- Trang chi tiết chuyến (Thành viên 2) tính điểm trung bình và đếm bình luận
-- theo trip_id, chạy mỗi lần mở tab Đánh giá.
CREATE INDEX IF NOT EXISTS ix_reviews_trip ON reviews (trip_id);
CREATE INDEX IF NOT EXISTS ix_reviews_user ON reviews (user_id);

-- =============================================================================
--  6. THANH TOÁN
-- =============================================================================

-- --------------------------------------------------------------- payments
-- Lịch sử giao dịch, nằm chung booking_db dù là cổng thanh toán bên ngoài.
-- payment-service sẽ đọc/ghi bảng này qua API nội bộ của booking-service.
CREATE TABLE IF NOT EXISTS payments (
    id               SERIAL         PRIMARY KEY,
    booking_id       INTEGER        NOT NULL,
    payment_gateway  VARCHAR(20)    NOT NULL,
    transaction_no   VARCHAR(100),
    amount           NUMERIC(12, 2) NOT NULL,
    bank_code        VARCHAR(50),
    response_code    VARCHAR(20),
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE payments DROP CONSTRAINT IF EXISTS fk_payments_booking;
ALTER TABLE payments ADD  CONSTRAINT fk_payments_booking
    FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT;

-- transaction_no là khoá chống thanh toán trùng: cổng báo thành công hai
-- lần cho cùng một mã thì chỉ ghi một dòng.
ALTER TABLE payments DROP CONSTRAINT IF EXISTS uk_payments_gateway_txn;
ALTER TABLE payments ADD  CONSTRAINT uk_payments_gateway_txn
    UNIQUE (payment_gateway, transaction_no);

-- STRIPE là cổng đang dùng thật (Stripe Checkout, tiền tệ VND). VNPAY/MOMO/
-- ZALOPAY giữ lại cho luồng demo cũ và sẽ nối sau; thêm một mã mới thì phải
-- sửa cả enum PaymentGateway trong booking-service cho khớp.
ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payments_gateway;
ALTER TABLE payments ADD  CONSTRAINT ck_payments_gateway
    CHECK (payment_gateway IN ('STRIPE', 'VNPAY', 'MOMO', 'ZALOPAY'));

ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payments_status;
ALTER TABLE payments ADD  CONSTRAINT ck_payments_status
    CHECK (status IN ('SUCCESS', 'FAILED', 'PENDING'));

ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payments_amount;
ALTER TABLE payments ADD  CONSTRAINT ck_payments_amount CHECK (amount >= 0);

CREATE INDEX IF NOT EXISTS ix_payments_booking ON payments (booking_id);
CREATE INDEX IF NOT EXISTS ix_payments_status  ON payments (status);

-- =============================================================================
--  Kiểm tra sau khi chạy:
--    \dt
--    SELECT count(*) FROM locations;
--    SELECT trip_id, count(*), count(DISTINCT seat_name)
--      FROM trip_seats GROUP BY trip_id;   -- cột count(*) và count(DISTINCT)
--                                          -- phải bằng nhau: UK đã có tác dụng
--
--  Kiểm tra ràng buộc đã được gắn đủ (chạy SAU khi bảng tồn tại):
--    SELECT conrelid::regclass, conname FROM pg_constraint
--     WHERE connamespace = 'public'::regnamespace ORDER BY 1, 2;
-- =============================================================================
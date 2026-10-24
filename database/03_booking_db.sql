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
--  một lần cho cả nhóm") để phần đặt vé và quản trị không phải tự đoán.
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
-- Nguồn cho dropdown "Nơi đi" / "Nơi đến" ở Trang chủ.
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

-- Truy vấn chính của Trang Danh sách kết quả: lọc theo tuyến
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

-- ---------------------------------------------------------- routes (bổ sung)
-- Block "Tuyến đường phổ biến" ở Trang chủ cần ảnh và cờ nổi bật do Admin chọn.
-- Hibernate update có thể đã thêm is_featured dạng NULL được, nên vá NULL rồi
-- mới SET NOT NULL — mẫu này lặp lại cho mọi cột boolean/tiền thêm sau.
ALTER TABLE routes ADD COLUMN IF NOT EXISTS image_url   VARCHAR(500);
ALTER TABLE routes ADD COLUMN IF NOT EXISTS is_featured BOOLEAN DEFAULT FALSE;
UPDATE routes SET is_featured = FALSE WHERE is_featured IS NULL;
ALTER TABLE routes ALTER COLUMN is_featured SET DEFAULT FALSE;
ALTER TABLE routes ALTER COLUMN is_featured SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_routes_featured ON routes (is_featured) WHERE is_featured;

-- =============================================================================
--  2b. NỘI DUNG NHÀ XE (tab Hình ảnh / Tiện ích / Chính sách của Trang chi tiết)
-- =============================================================================
--  Gắn theo NHÀ XE, không theo chuyến: nhập một lần, dùng cho mọi chuyến của
--  nhà xe đó. Gắn theo chuyến thì Admin phải nhập lại cho từng chuyến.
-- =============================================================================

-- ---------------------------------------------------------- company_images
CREATE TABLE IF NOT EXISTS company_images (
    id          SERIAL        PRIMARY KEY,
    company_id  INTEGER       NOT NULL,
    image_url   VARCHAR(500)  NOT NULL,
    caption     VARCHAR(200),
    sort_order  INTEGER       NOT NULL DEFAULT 1,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE company_images DROP CONSTRAINT IF EXISTS fk_company_images_company;
ALTER TABLE company_images ADD  CONSTRAINT fk_company_images_company
    FOREIGN KEY (company_id) REFERENCES bus_companies (id) ON DELETE CASCADE;

ALTER TABLE company_images DROP CONSTRAINT IF EXISTS ck_company_images_order;
ALTER TABLE company_images ADD  CONSTRAINT ck_company_images_order CHECK (sort_order >= 1);

CREATE INDEX IF NOT EXISTS ix_company_images_company ON company_images (company_id, sort_order);

-- --------------------------------------------------------------- amenities
-- Danh mục tiện ích dùng chung: Wifi, Nước uống, Chăn đắp, Sạc điện thoại...
-- icon là tên icon phía frontend, không phải URL ảnh.
CREATE TABLE IF NOT EXISTS amenities (
    id    SERIAL        PRIMARY KEY,
    code  VARCHAR(40)   NOT NULL,
    name  VARCHAR(100)  NOT NULL,
    icon  VARCHAR(60)
);

ALTER TABLE amenities DROP CONSTRAINT IF EXISTS uk_amenities_code;
ALTER TABLE amenities ADD  CONSTRAINT uk_amenities_code UNIQUE (code);

-- ------------------------------------------------------- company_amenities
CREATE TABLE IF NOT EXISTS company_amenities (
    company_id  INTEGER  NOT NULL,
    amenity_id  INTEGER  NOT NULL,
    PRIMARY KEY (company_id, amenity_id)
);

ALTER TABLE company_amenities DROP CONSTRAINT IF EXISTS fk_company_amenities_company;
ALTER TABLE company_amenities ADD  CONSTRAINT fk_company_amenities_company
    FOREIGN KEY (company_id) REFERENCES bus_companies (id) ON DELETE CASCADE;

ALTER TABLE company_amenities DROP CONSTRAINT IF EXISTS fk_company_amenities_amenity;
ALTER TABLE company_amenities ADD  CONSTRAINT fk_company_amenities_amenity
    FOREIGN KEY (amenity_id) REFERENCES amenities (id) ON DELETE CASCADE;

-- -------------------------------------------------------- company_policies
-- Tab Chính sách: văn bản hiển thị theo nhóm. Quy tắc hoàn tiền để MÁY tính
-- nằm riêng ở cancellation_rules, không parse từ văn bản này.
CREATE TABLE IF NOT EXISTS company_policies (
    id           SERIAL        PRIMARY KEY,
    company_id   INTEGER       NOT NULL,
    policy_type  VARCHAR(30)   NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    content      TEXT          NOT NULL,
    sort_order   INTEGER       NOT NULL DEFAULT 1
);

ALTER TABLE company_policies DROP CONSTRAINT IF EXISTS fk_company_policies_company;
ALTER TABLE company_policies ADD  CONSTRAINT fk_company_policies_company
    FOREIGN KEY (company_id) REFERENCES bus_companies (id) ON DELETE CASCADE;

ALTER TABLE company_policies DROP CONSTRAINT IF EXISTS ck_company_policies_type;
ALTER TABLE company_policies ADD  CONSTRAINT ck_company_policies_type CHECK (policy_type IN (
    'CANCELLATION', 'LUGGAGE', 'CHILDREN', 'PET', 'DOCUMENT', 'OTHER'
));

CREATE INDEX IF NOT EXISTS ix_company_policies_company ON company_policies (company_id, sort_order);

-- ------------------------------------------------------ cancellation_rules
-- Huỷ trước giờ khởi hành >= hours_before giờ thì hoàn refund_percent %.
-- Áp mốc lớn nhất mà khách còn thoả; không thoả mốc nào thì không hoàn.
CREATE TABLE IF NOT EXISTS cancellation_rules (
    id              SERIAL   PRIMARY KEY,
    company_id      INTEGER  NOT NULL,
    hours_before    INTEGER  NOT NULL,
    refund_percent  INTEGER  NOT NULL
);

ALTER TABLE cancellation_rules DROP CONSTRAINT IF EXISTS fk_cancellation_rules_company;
ALTER TABLE cancellation_rules ADD  CONSTRAINT fk_cancellation_rules_company
    FOREIGN KEY (company_id) REFERENCES bus_companies (id) ON DELETE CASCADE;

ALTER TABLE cancellation_rules DROP CONSTRAINT IF EXISTS ck_cancellation_rules_values;
ALTER TABLE cancellation_rules ADD  CONSTRAINT ck_cancellation_rules_values
    CHECK (hours_before >= 0 AND refund_percent BETWEEN 0 AND 100);

-- Hai mốc cùng số giờ cho một nhà xe thì không biết áp mốc nào.
ALTER TABLE cancellation_rules DROP CONSTRAINT IF EXISTS uk_cancellation_rules_company_hours;
ALTER TABLE cancellation_rules ADD  CONSTRAINT uk_cancellation_rules_company_hours
    UNIQUE (company_id, hours_before);

-- =============================================================================
--  2c. KHUYẾN MÃI (tab Giảm giá + block Ưu đãi nổi bật)
-- =============================================================================

-- -------------------------------------------------------------- promotions
-- company_id / route_id NULL = áp dụng toàn hệ thống.
-- used_count chỉ tăng khi đơn chuyển PAID (khoá dòng FOR UPDATE), KHÔNG tăng
-- lúc giữ ghế — nếu không đơn hết hạn sẽ ăn mất lượt dùng mã.
CREATE TABLE IF NOT EXISTS promotions (
    id               SERIAL          PRIMARY KEY,
    code             VARCHAR(30)     NOT NULL,
    title            VARCHAR(200)    NOT NULL,
    description      TEXT,
    banner_url       VARCHAR(500),
    discount_type    VARCHAR(10)     NOT NULL,
    discount_value   NUMERIC(12, 2)  NOT NULL,
    max_discount     NUMERIC(12, 2),
    min_order_value  NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    company_id       INTEGER,
    route_id         INTEGER,
    starts_at        TIMESTAMP(6)    NOT NULL,
    ends_at          TIMESTAMP(6)    NOT NULL,
    usage_limit      INTEGER,
    used_count       INTEGER         NOT NULL DEFAULT 0,
    is_featured      BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active        BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE promotions DROP CONSTRAINT IF EXISTS uk_promotions_code;
ALTER TABLE promotions ADD  CONSTRAINT uk_promotions_code UNIQUE (code);

ALTER TABLE promotions DROP CONSTRAINT IF EXISTS fk_promotions_company;
ALTER TABLE promotions ADD  CONSTRAINT fk_promotions_company
    FOREIGN KEY (company_id) REFERENCES bus_companies (id) ON DELETE CASCADE;

ALTER TABLE promotions DROP CONSTRAINT IF EXISTS fk_promotions_route;
ALTER TABLE promotions ADD  CONSTRAINT fk_promotions_route
    FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE CASCADE;

ALTER TABLE promotions DROP CONSTRAINT IF EXISTS ck_promotions_discount;
ALTER TABLE promotions ADD  CONSTRAINT ck_promotions_discount CHECK (
    (discount_type = 'PERCENT' AND discount_value > 0 AND discount_value <= 100)
    OR (discount_type = 'FIXED' AND discount_value > 0)
);

ALTER TABLE promotions DROP CONSTRAINT IF EXISTS ck_promotions_period;
ALTER TABLE promotions ADD  CONSTRAINT ck_promotions_period CHECK (ends_at > starts_at);

ALTER TABLE promotions DROP CONSTRAINT IF EXISTS ck_promotions_amounts;
ALTER TABLE promotions ADD  CONSTRAINT ck_promotions_amounts CHECK (
    min_order_value >= 0 AND (max_discount IS NULL OR max_discount > 0)
);

ALTER TABLE promotions DROP CONSTRAINT IF EXISTS ck_promotions_usage;
ALTER TABLE promotions ADD  CONSTRAINT ck_promotions_usage CHECK (
    used_count >= 0 AND (usage_limit IS NULL OR used_count <= usage_limit)
);

-- Trang chủ và tab Giảm giá đều lọc "đang hiệu lực" theo cặp cột này.
CREATE INDEX IF NOT EXISTS ix_promotions_active ON promotions (is_active, starts_at, ends_at);

-- =============================================================================
--  3. GHẾ
-- =============================================================================
--  Sơ đồ ghế 2 tầng. Bảng lớn nhất trong hệ thống: một chuyến
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

-- ------------------------------------------------- bookings: khách vãng lai
-- Quyết định B8 (db.md): khách chưa đăng ký vẫn đặt được vé, như Vexere.
--   user_id NULL                 -> đơn của khách vãng lai
--   booking_code                 -> mã vé hiển thị cho khách, dùng để tra cứu.
--                                   Không dùng id vì id tăng dần đoán được.
--   guest_token_hash             -> SHA-256 hex của token cấp MỘT LẦN khi tạo
--                                   đơn guest; khách gửi lại token để thanh toán/
--                                   huỷ. Không lưu bản gốc (giống refresh_tokens).
ALTER TABLE bookings ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS booking_code     VARCHAR(12);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS guest_token_hash VARCHAR(64);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS paid_at          TIMESTAMP(6);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS cancelled_at     TIMESTAMP(6);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS cancel_reason    VARCHAR(255);

-- Đơn tạo trước khi có cột: sinh mã rồi mới SET NOT NULL. Mã sinh ở đây chỉ
-- gồm hex in hoa; đơn mới do code sinh bằng bảng chữ không có 0/O/1/I.
UPDATE bookings
   SET booking_code = UPPER(SUBSTRING(MD5(id::text || RANDOM()::text) FROM 1 FOR 8))
 WHERE booking_code IS NULL;
ALTER TABLE bookings ALTER COLUMN booking_code SET NOT NULL;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS uk_bookings_code;
ALTER TABLE bookings ADD  CONSTRAINT uk_bookings_code UNIQUE (booking_code);

-- Mỗi đơn có đúng một kiểu chủ: tài khoản, hoặc token guest. Đơn guest bắt
-- buộc có SĐT vì tra cứu vé dùng cặp (booking_code, contact_phone).
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_owner;
ALTER TABLE bookings ADD  CONSTRAINT ck_bookings_owner CHECK (
    (user_id IS NOT NULL AND guest_token_hash IS NULL)
    OR (user_id IS NULL AND guest_token_hash IS NOT NULL AND contact_phone IS NOT NULL)
);

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_guest_token_hex;
ALTER TABLE bookings ADD  CONSTRAINT ck_bookings_guest_token_hex
    CHECK (guest_token_hash IS NULL OR guest_token_hash ~ '^[0-9a-f]{64}$');

-- Trang Tra cứu vé: mã + SĐT.
CREATE INDEX IF NOT EXISTS ix_bookings_code_phone ON bookings (booking_code, contact_phone);
-- "Liên kết vé đã đặt" khi khách vãng lai đăng ký bằng đúng email đã dùng.
CREATE INDEX IF NOT EXISTS ix_bookings_guest_email ON bookings (LOWER(contact_email))
    WHERE user_id IS NULL;
-- Dashboard thống kê theo ngày đặt.
CREATE INDEX IF NOT EXISTS ix_bookings_created ON bookings (created_at);

-- --------------------------------------------------- bookings: khuyến mãi
-- subtotal_price = tổng giá ghế trước giảm; total_price = số phải trả.
-- Chốt cả hai lúc đặt, giống booking_details.price: Admin sửa mã sau này
-- không làm sai hoá đơn cũ.
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS promotion_id    INTEGER;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS subtotal_price  NUMERIC(12, 2);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12, 2) DEFAULT 0;
UPDATE bookings SET discount_amount = 0           WHERE discount_amount IS NULL;
UPDATE bookings SET subtotal_price  = total_price WHERE subtotal_price IS NULL;
ALTER TABLE bookings ALTER COLUMN discount_amount SET DEFAULT 0;
ALTER TABLE bookings ALTER COLUMN discount_amount SET NOT NULL;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS fk_bookings_promotion;
ALTER TABLE bookings ADD  CONSTRAINT fk_bookings_promotion
    FOREIGN KEY (promotion_id) REFERENCES promotions (id) ON DELETE SET NULL;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_discount;
ALTER TABLE bookings ADD  CONSTRAINT ck_bookings_discount CHECK (
    discount_amount >= 0
    AND (subtotal_price IS NULL OR discount_amount <= subtotal_price)
);

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

-- Tên hành khách từng ghế (tuỳ chọn). NULL thì danh sách hành khách của Admin
-- dùng contact_name của đơn.
ALTER TABLE booking_details ADD COLUMN IF NOT EXISTS passenger_name  VARCHAR(255);
ALTER TABLE booking_details ADD COLUMN IF NOT EXISTS passenger_phone VARCHAR(20);

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

-- Trang chi tiết chuyến tính điểm trung bình và đếm bình luận
-- theo trip_id, chạy mỗi lần mở tab Đánh giá.
CREATE INDEX IF NOT EXISTS ix_reviews_trip ON reviews (trip_id);
CREATE INDEX IF NOT EXISTS ix_reviews_user ON reviews (user_id);

-- booking_id: chứng minh người viết đã đi chuyến (đơn COMPLETED của chính họ).
-- company_id: chép từ trips.company_id để tính điểm theo NHÀ XE như Vexere —
--   tính theo chuyến thì mỗi chuyến gần như chỉ có 0-1 đánh giá.
-- is_hidden: Admin ẩn bình luận vi phạm; ẩn thì không tính vào điểm trung bình.
-- Cả hai cột id để NULL được vì đánh giá seed cũ không gắn đơn.
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS booking_id INTEGER;
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS company_id INTEGER;
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS is_hidden  BOOLEAN DEFAULT FALSE;
UPDATE reviews SET is_hidden = FALSE WHERE is_hidden IS NULL;
ALTER TABLE reviews ALTER COLUMN is_hidden SET DEFAULT FALSE;
ALTER TABLE reviews ALTER COLUMN is_hidden SET NOT NULL;

UPDATE reviews r
   SET company_id = t.company_id
  FROM trips t
 WHERE t.id = r.trip_id AND r.company_id IS NULL;

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS fk_reviews_booking;
ALTER TABLE reviews ADD  CONSTRAINT fk_reviews_booking
    FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE;

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS fk_reviews_company;
ALTER TABLE reviews ADD  CONSTRAINT fk_reviews_company
    FOREIGN KEY (company_id) REFERENCES bus_companies (id) ON DELETE CASCADE;

-- Một đơn một đánh giá. UNIQUE cho phép nhiều NULL nên đánh giá cũ không vướng.
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS uk_reviews_booking;
ALTER TABLE reviews ADD  CONSTRAINT uk_reviews_booking UNIQUE (booking_id);

CREATE INDEX IF NOT EXISTS ix_reviews_company ON reviews (company_id, created_at DESC);

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

-- REFUNDED: giao dịch đã thành công rồi được hoàn (payment-service tự hoàn khi
-- tiền tới sau lúc ghế hết hạn giữ, hoặc Admin hoàn khi huỷ vé đã trả).
ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payments_status;
ALTER TABLE payments ADD  CONSTRAINT ck_payments_status
    CHECK (status IN ('SUCCESS', 'FAILED', 'PENDING', 'REFUNDED'));

ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payments_amount;
ALTER TABLE payments ADD  CONSTRAINT ck_payments_amount CHECK (amount >= 0);

ALTER TABLE payments ADD COLUMN IF NOT EXISTS refund_amount NUMERIC(12, 2);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refunded_at   TIMESTAMP(6);

ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payments_refund;
ALTER TABLE payments ADD  CONSTRAINT ck_payments_refund CHECK (
    refund_amount IS NULL OR (refund_amount >= 0 AND refund_amount <= amount)
);

CREATE INDEX IF NOT EXISTS ix_payments_booking ON payments (booking_id);
CREATE INDEX IF NOT EXISTS ix_payments_status  ON payments (status);
-- Dashboard doanh thu theo ngày giao dịch.
CREATE INDEX IF NOT EXISTS ix_payments_created ON payments (created_at);

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
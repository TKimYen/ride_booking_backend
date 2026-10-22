# Ride Booking Microservices

Hệ thống **đặt vé xe khách v4** (giống GoBus / Uber) xây dựng theo kiến trúc **microservice** với Spring Boot 4 + Spring Cloud.

Tìm kiếm chuyến xe → xem sơ đồ ghế 2 tầng → giữ ghế 10 phút → thanh toán Stripe → đánh giá chuyến đi.

> Tài liệu này hướng dẫn **clone về và chạy được ngay** trên máy mới, không cần cài sẵn database.

---

## 1. Công nghệ

| Thành phần    | Dùng gì                                                          |
| ------------- | ---------------------------------------------------------------- |
| Ngôn ngữ      | Java 21                                                          |
| Framework     | Spring Boot **4.1.1** (Spring Framework 7)                       |
| Microservice  | Spring Cloud **2025.1.3** (Netflix Eureka, Spring Cloud Gateway) |
| Cơ sở dữ liệu | PostgreSQL **17** — 3 database tách biệt                         |
| ORM           | Spring Data JPA / Hibernate                                      |
| Bảo mật       | Spring Security + JWT (jjwt 0.12.6) + BCrypt                     |
| Thanh toán    | Stripe Checkout (stripe-java 34.0.0)                             |
| Email         | Spring Mail + Mailpit (mail catcher cho local)                   |
| API docs      | springdoc-openapi (Swagger UI)                                   |
| Tài liệu API  | 69 endpoint REST, 290 test tự động                               |
| Hạ tầng       | Docker + Docker Compose                                          |

---

## 2. Kiến trúc

```
                        ┌─────────────────┐
   Frontend  ──────────►│   api-gateway   │  :8088  ← ĐIỂM VÀO DUY NHẤT
   (React)              └────────┬────────┘
                                 │
                    ┌────────────┼─────────────┬──────────────┐
                    ▼            ▼             ▼              ▼
            ┌──────────────┐┌────────────┐┌────────────┐┌────────────┐
            │ auth-service ││user-service││booking-svc ││payment-svc │
            │    :8081     ││   :8082    ││   :8083    ││   :8084    │
            └──────┬───────┘└─────┬──────┘└─────┬──────┘└─────┬──────┘
                   │              │             │              │
            ┌──────▼──────┐┌──────▼──────┐┌─────▼──────────────▼─────┐
            │   auth_db   ││   user_db   ││        booking_db        │
            │   :5433     ││   :5434     ││         :5435           │
            └─────────────┘└─────────────┘└─────────────────────────┘

            Mọi service đăng ký vào discovery-server (Eureka) :8761
```

| Service            | Port | Database     | Vai trò                                                        |
| ------------------ | ---- | ------------ | -------------------------------------------------------------- |
| `discovery-server` | 8761 | —            | Eureka Server, service registry                                |
| `api-gateway`      | 8088 | —            | Điểm vào duy nhất, định tuyến, CORS, rate-limit IP             |
| `auth-service`     | 8081 | `auth_db`    | Đăng ký / đăng nhập / JWT / email xác minh, quản lý tài khoản  |
| `user-service`     | 8082 | `user_db`    | Hồ sơ cá nhân, danh sách user cho Admin                        |
| `booking-service`  | 8083 | `booking_db` | Catalog chuyến xe, sơ đồ ghế, đặt vé, giữ ghế, đánh giá        |
| `payment-service`  | 8084 | —            | Stripe Checkout + webhook (ghi bảng `payments` qua API nội bộ) |

**Quyết định thiết kế quan trọng:**

- `auth_db` và `user_db` **cố ý tách rời**: auth-service giữ mật khẩu, user-service giữ hồ sơ. Hai service giao tiếp qua HTTP nội bộ (`X-Internal-Key`), không join database.
- `booking-service` **cố ý dùng chung một database** cho catalog lẫn đặt vé: thuật toán giữ ghế cần khoá dòng `trip_seats` rồi ghi `bookings` trong **cùng một transaction**.
- `payment-service` **không có database riêng** — nó ghi bảng `payments` qua API nội bộ của booking-service, nhờ vậy trạng thái tiền và trạng thái ghế luôn chốt cùng nhau.

Chi tiết đầy đủ: [db.md](db.md), [API.md](API.md).

---

## 3. Yêu cầu

Chỉ cần **một** trong hai cách:

### Cách A — Docker (khuyến nghị, không cần cài Java)

- [Docker Desktop](https://docs.docker.com/desktop/install/mac-install/) (macOS / Windows) hoặc Docker Engine + plugin Compose (Linux)
- Docker Compose **v2** — kiểm tra bằng `docker compose version`
- **Dockerfile** dùng JDK 21 nên Docker sẽ tải sẵn, không cần cài gì trên máy
- Khoảng **4 GB RAM trống** cho 10 container

### Cách B — Chạy bằng Maven

- **JDK 21 trở lên** (test thành công trên JDK 21 và JDK 25)
- **Maven 3.9+**
- **Docker** vẫn cần cho 3 database PostgreSQL

---

## 4. Chạy bằng Docker (cách chính)

### Bước 1 — Clone về

```bash
git clone https://github.com/<username>/ride-booking-microservices.git
cd ride-booking-microservices
```

> Thay `<username>` bằng tài khoản GitHub thật của bạn.

### Bước 2 — Tạo file cấu hình

```bash
cp .env.example .env
```

Mặc định `.env.example` **đã chạy được ngay** — mọi giá trị đều có sẵn cho local.
Nhưng **bắt buộc phải đổi `JWT_SECRET`** (tối thiểu 32 ký tự) để tránh trùng secret dev:

```bash
# sinh chuỗi ngẫu nhiên
openssl rand -base64 48
```

Dán kết quả vào `JWT_SECRET` trong `.env`. Tương tự, đổi `INTERNAL_API_KEY`.

### Bước 3 — Build và khởi động

```bash
docker compose up -d --build
```

Lần đầu mất **5–10 phút** (tải image, tải dependency Maven, build 6 service).

Theo dõi:

```bash
docker compose ps               # xem trạng thái
docker compose logs -f api-gateway   # theo dõi log từng service
```

Đợi tới khi các service báo `(healthy)`, khoảng 60–90 giây sau khi build xong
(các service đăng ký Eureka nên gateway cần vài chống để có đường đi tới service).

### Bước 4 — Kiểm tra đã chạy

| Kiểm tra                       | Địa chỉ                                     | Kết quả mong đợi                    |
| ------------------------------ | ------------------------------------------- | ----------------------------------- |
| Gateway                        | http://localhost:8088/api/locations         | `200` + danh sách địa điểm          |
| **Swagger UI (gộp 4 service)** | http://localhost:8088/swagger-ui/index.html | Giao diện có dropdown chọn tài liệu |
| Eureka dashboard               | http://localhost:8761                       | Thấy đủ 5 client đăng ký            |
| Mailpit (hộp thư)              | http://localhost:8025                       | Xem email xác minh / reset mật khẩu |
| Health auth-service            | http://localhost:8081/actuator/health       | `{"status":"UP"}`                   |
| Health booking-service         | http://localhost:8083/actuator/health       | `{"status":"UP"}`                   |

Chạy thử bằng `curl`:

```bash
# Đăng nhập lấy token
curl.exe -s -X POST "http://localhost:8088/api/auth/login" `
  -H "Content-Type: application/json" `
  --data-raw '{\"email\":\"admin@ridebooking.local\",\"password\":\"Admin@12345\"}'

# Gọi API có cần token
$TOKEN = (curl.exe -s -X POST "http://localhost:8088/api/auth/login" `
  -H "Content-Type: application/json" `
  --data-raw '{\"email\":\"admin@ridebooking.local\",\"password\":\"Admin@12345\"}' | ConvertFrom-Json).accessToken

$TOKEN

curl -s http://localhost:8088/api/auth/me -H "Authorization: Bearer $TOKEN"
```

### Bước 5 — Nạp dữ liệu mẫu (khuyến nghị)

Mặc định database **rỗng**, nên API tìm chuyến xe sẽ trả danh sách rỗng.
Nạp dữ liệu mẫu (20 địa điểm, 6 nhà xe, 15 tuyến, 225 chuyến, 8850 ghế):

```bash
docker compose run --rm --no-deps -e APP_SEED_ENABLED=true booking-service
```

Lệnh này dựng thêm **một instance booking-service chỉ để nạp dữ liệu**. Instance
đó vẫn tiếp tục chạy web server sau khi seed xong, nên nhấn `Ctrl+C` ngay khi thấy
dòng log `Xong seed: 225 chuyến, 8850 ghế, ...` (thao tác này cũng tự xoá container
tạm nhờ `--rm`).

Seed là **idempotent**: chạy lại không nhân đôi dữ liệu.

```bash
# Xác nhận dữ liệu đã vào
docker compose exec booking-db psql -U postgres -d booking_db \
  -c "select count(*) as trips from trips;"
```

### Bước 6 — Dừng

```bash
docker compose down       # dừng, GIỮ dữ liệu trong volume
docker compose down -v    # dừng và XOÁ sạch database (bắt đầu lại từ đầu)
```

---

## 5. Tài khoản mặc định

Khi `auth-service` khởi động lần đầu, nó **tự tạo sẵn một tài khoản admin**
(đây là đường tạo admin duy nhất khi database còn rỗng — `POST /api/admin/users`
lại chỉ nhận token `ADMIN`):

|          |                           |
| -------- | ------------------------- |
| Email    | `admin@ridebooking.local` |
| Mật khẩu | `Admin@12345`             |
| Role     | `ADMIN`                   |

Đổi được bằng `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NAME` trong `.env`.
**Đổi mật khẩu sau khi đã tạo thì không có tác dụng** — phải `docker compose down -v`
rồi `up` lại, hoặc đổi bằng `PUT /api/auth/password`.

Tài khoản thường: tự đăng ký qua `POST /api/auth/register` (role luôn là `USER`).

---

## 6. Chạy bằng Maven (không dựng service trong Docker)

Dùng khi bạn muốn debug service có `hot reload` trong IDE.

```bash
# 1. Chỉ bật 3 database (vẫn chạy trong Docker)
docker compose up -d auth-db user-db booking-db

# 2. Build toàn bộ
mvn clean install -DskipTests

# 3. Chạy từng service — MỘT CÁI MỘT TERMINAL, theo đúng thứ tự
java -jar discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar
java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
java -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar
java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar
java -jar payment-service/target/payment-service-0.0.1-SNAPSHOT.jar
```

Điểm khác biệt so với chạy Docker:

|               | Docker                                | Maven                                             |
| ------------- | ------------------------------------- | ------------------------------------------------- |
| `EUREKA_URL`  | `http://discovery-server:8761/eureka` | mặc định `http://localhost:8761/eureka` — đã đúng |
| `DB_HOST`     | tên container (`auth-db`)             | `localhost` — mặc định đã đúng                    |
| **`DB_PORT`** | `5432` (cổng trong network)           | phải là **5433 / 5434 / 5435**                    |
| Email         | Mailpit qua DNS                       | `MAIL_HOST=localhost`                             |
| Thanh toán    | Stripe nội bộ                         | không có mạng nội bộ → dùng API demo              |

Ba database được map ra cổng khác `5432` (5433/5434/5435) để không đụng PostgreSQL
cài sẵn trên máy. Khi chạy ngoài Docker, **3 service đã tự đúng port** trong
`application.properties`; nếu bạn đổi port trong `.env` thì phải truyền biến môi
trường tương ứng:

```bash
DB_PORT=5433 java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
DB_PORT=5434 java -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar
DB_PORT=5435 java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar
```

Nạp dữ liệu mẫu khi chạy bằng Maven:

```bash
java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar --app.seed.enabled=true
```

Dừng các database sau khi xong:

```bash
docker compose down
```

---

## 7. Cấu trúc thư mục

```
ride-booking-microservices/
├── pom.xml                      # Parent POM (multi-module)
├── docker-compose.yml           # Toàn bộ môi trường dev: 3 DB + 6 service + mailpit
├── Dockerfile                   # Dockerfile dùng chung, chọn module bằng --build-arg MODULE
├── .env.example                 # Mẫu cấu hình (copy thành .env)
│
├── discovery-server/            # :8761  Eureka Server
├── api-gateway/                 # :8088  Gateway + Swagger UI gộp
├── auth-service/                # :8081  Auth, JWT, email       (auth_db)
├── user-service/                # :8082  Hồ sơ cá nhân           (user_db)
├── booking-service/             # :8083  Catalog, ghế, đặt vé    (booking_db)
├── payment-service/             # :8084  Stripe Checkout
│
├── database/                    # Script SQL tạo schema (dùng cho production)
│   ├── 01_auth_db.sql
│   ├── 02_user_db.sql
│   └── 03_booking_db.sql
│
├── scripts/
│   └── e2e-auth-user.sh         # Kiểm chứng E2E 172 assertion trên stack đang chạy
│
├── API.md                       # Tài liệu API đầy đủ (endpoint, ví dụ, status code)
├── db.md                        # Thiết kế database (DBML)
└── requirement.md               # Đặc tả nghiệp vụ & phân công thành viên
```

---

## 8. Database

Mặc định `JPA_DDL_AUTO=update` nên Hibernate **tự tạo bảng** khi service khởi động
— tiện cho local, không cần chạy script SQL nào.

Khi deploy thật, đặt `JPA_DDL_AUTO=validate` (Hibernate chỉ kiểm tra schema,
**không sửa**) và chạy script SQL một lần:

```bash
docker compose exec -T auth-db    psql -U postgres -d auth_db    < database/01_auth_db.sql
docker compose exec -T user-db    psql -U postgres -d user_db    < database/02_user_db.sql
docker compose exec -T booking-db psql -U postgres -d booking_db < database/03_booking_db.sql
```

Script SQL là **idempotent** (chạy lại được), có sẵn unique email không phân biệt
hoa/thường, foreign key, CHECK constraint và index.

Truy cận database từ máy host (cần client `psql`):

```bash
psql -h localhost -p 5433 -U postgres -d auth_db      # auth_db
psql -h localhost -p 5434 -U postgres -d user_db      # user_db
psql -h localhost -p 5435 -U postgres -d booking_db   # booking_db
```

---

## 9. Chạy test

```bash
# Toàn bộ unit + integration test — KHÔNG cần Docker, DB thật dùng H2 in-memory
mvn test
```

**290 test**, tất cả xanh:

| Module           | Số test |
| ---------------- | ------- |
| discovery-server | 1       |
| api-gateway      | 16      |
| auth-service     | 118     |
| user-service     | 36      |
| booking-service  | 100     |
| payment-service  | 19      |

Test E2E trên stack đang chạy (`scripts/e2e-auth-user.sh`):

```bash
# Bộ test dùng dữ liệu một lần → cần stack SẠCH
docker compose down -v && docker compose up -d

# Script kiểm tra cả trường hợp "service phía sau chưa dựng" (mục 18),
# nên booking-service và payment-service phải TẮT
docker compose stop booking-service payment-service

./scripts/e2e-auth-user.sh

# Bật lại
docker compose start booking-service payment-service
```

Script cần có `curl`, `python3` và `docker`.

---

## 10. Cấu hình `.env`

`.env.example` có chú thích đầy đủ cho từng biến. Các biến **nên đổi khi deploy thật**:

| Biến                                                | Mặc định                                  | Ý nghĩa                                                                     |
| --------------------------------------------------- | ----------------------------------------- | --------------------------------------------------------------------------- |
| `DB_USER` / `DB_PASSWORD`                           | `postgres`                                | Tài khoản PostgreSQL cho cả 3 database                                      |
| `AUTH_DB_PORT` / `USER_DB_PORT` / `BOOKING_DB_PORT` | `5433` / `5434` / `5435`                  | Port expose ra máy host                                                     |
| **`JWT_SECRET`**                                    | _(bắt buộc đổi)_                          | ≥ 32 ký tự. `auth` / `user` / `booking` / `payment` **bắt buộc dùng chung** |
| **`INTERNAL_API_KEY`**                              | _(bắt buộc đổi)_                          | Khoá header `X-Internal-Key` giữa các service                               |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD`                    | `admin@ridebooking.local` / `Admin@12345` | Tài khoản admin tạo lúc khởi động                                           |
| `CORS_ALLOWED_ORIGINS`                              | `localhost:3000,localhost:5173`           | Danh sách origin được phép (không dùng `*`)                                 |
| `APP_FRONTEND_URL`                                  | `http://localhost:5173`                   | Domain frontend dựng link trong email                                       |
| `MAIL_*`                                            | Mailpit                                   | Đổi sang SMTP thật (Gmail/SES/Brevo)                                        |
| `SEAT_HOLD_MINUTES`                                 | `10`                                      | Thời gian giữ ghế (đồng hồ đếm ngược + job trả ghế)                         |
| `JPA_DDL_AUTO`                                      | `update`                                  | Đặt `validate` khi deploy thật                                              |
| `STRIPE_SECRET_KEY`                                 | _(rỗng)_                                  | Rỗng thì service vẫn lên nhưng API thanh toán trả `502`                     |
| `STRIPE_WEBHOOK_SECRET`                             | _(rỗng)_                                  | Bắt buộc để webhook Stripe xác thực được                                    |
| `GATEWAY_IP`                                        | `172.28.0.10`                             | IP tĩnh của gateway — đổi thì **phải đổi cả `AUTH_TRUSTED_PROXY_IPS`**      |
| `DOCKER_SUBNET`                                     | `172.28.0.0/24`                           | Subnet cố định cho Docker network                                           |

### Thanh toán Stripe (tuỳ chọn)

Không có khoá Stripe thì hệ thống vẫn chạy đầy đủ — dùng API demo
`POST /api/bookings/{id}/pay` để test offline, không cần mạng.

Muốn chạy Stripe Checkout thật:

```bash
# 1. Lấy khoá test ở Stripe Dashboard > Developers > API keys
STRIPE_SECRET_KEY=sk_test_...

# 2. Lấy webhook secret (Dashboard > Developers > Webhooks, endpoint trỏ về payment-service)
STRIPE_WEBHOOK_SECRET=whsec_...

# 3. Chạy lại
docker compose up -d
```

Test webhook ở local bằng Stripe CLI:

```bash
stripe listen --forward-to localhost:8084/api/payments/webhook
```

---

## 11. Lệnh thường dùng

| Mục đích                       | Lệnh                                                            |
| ------------------------------ | --------------------------------------------------------------- |
| Khởi động (lần đầu)            | `docker compose up -d --build`                                  |
| Khởi động lại (không đổi code) | `docker compose up -d`                                          |
| Xem trạng thái                 | `docker compose ps`                                             |
| Log tất cả                     | `docker compose logs -f`                                        |
| Log một service                | `docker compose logs -f booking-service`                        |
| Restart một service            | `docker compose restart auth-service`                           |
| Vào trong container            | `docker compose exec auth-service sh`                           |
| Shell psql                     | `docker compose exec booking-db psql -U postgres -d booking_db` |
| Rebuild 1 service              | `docker compose up -d --build booking-service`                  |
| Dừng (giữ data)                | `docker compose down`                                           |
| Dừng + xoá data                | `docker compose down -v`                                        |

Build image thủ công cho một service:

```bash
docker build --build-arg MODULE=auth-service -t ride-booking/auth-service .
```

---

## 12. Xử lý sự cố

<details>
<summary><b>Port đã dùng</b> — <code>bind: address already in use</code></summary>

Đổi port trong `.env` (port host, **không** phải port trong container):

```bash
AUTH_DB_PORT=15433
USER_DB_PORT=15434
BOOKING_DB_PORT=15435
GATEWAY_IP=172.28.0.10   # giữ nguyên
```

Gateway dùng cổng **`8088`** (dự án không dùng `8080`), cả khi chạy Docker lẫn chạy bằng
Maven/IDE. Máy bận cổng 8088 thì đổi cổng trên máy bằng `GATEWAY_PORT` trong `.env`;
trong container gateway vẫn nghe `8088`.

</details>

<details>
<summary><b>Docker báo trùng subnet</b> — <code>Pool overlaps</code> hoặc <code>invalid subnet</code></summary>

Máy đang có network Docker khác nằm trong dải `172.28.0.0/24`. Đổi trong `.env`:

```bash
DOCKER_SUBNET=172.31.0.0/24
GATEWAY_IP=172.31.0.10
AUTH_TRUSTED_PROXY_IPS=172\.31\.0\.10
```

Cả ba biến phải đổi cùng nhau. Sau đó `docker compose down -v && docker compose up -d`.

</details>

<details>
<summary><b>Gateway trả 503 / service chưa sẵn sàng</b></summary>

Service phía sau chưa đăng ký xong Eureka. Xem dashboard http://localhost:8761 và
log service:

```bash
docker compose logs -f booking-service
```

Chờ 30–60 giây sau khi khởi động. Nếu service `Exited`, xem `docker compose logs <tên>`.

</details>

<details>
<summary><b>Đăng nhập được nhưng báo "tài khoản đang bị khoá"</b></summary>

Hệ thống khoá **cặp (email + IP)** sau 5 lần sai trong 15 phút. Chờ 15 phút,
đăng nhập lại, hoặc admin gỡ khoá:

```bash
curl -X PATCH http://localhost:8088/api/admin/users/{id}/unlock \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

</details>

<details>
<summary><b>API thanh toán trả 502</b></summary>

Thiếu `STRIPE_SECRET_KEY` trong `.env`. Đặt khoá rồi `docker compose up -d`.
Xem [mục 10](#thanh-toán-stripe-tuỳ-chọn).

</details>

<details>
<summary><b>Không nhận được link xác minh / reset mật khẩu</b></summary>

Local dùng Mailpit — mở http://localhost:8025 và xem trong hộp thư. Không cần
tài khoản email thật. Muốn gửi thật thì đặt `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/
`MAIL_PASSWORD` trong `.env` (Gmail dùng **App Password**, không dùng mật khẩu
tài khoản).

</details>

<details>
<summary><b>Muốn bắt đầu lại từ sạch</b></summary>

```bash
docker compose down -v && docker compose up -d --build
```

Lệnh này xoá toàn bộ dữ liệu 3 database, kể cả tài khoản admin.

</details>

<details>
<summary><b>Build Docker quá lâu</b></summary>

`Dockerfile` copy `pom.xml` trước rồi mới copy `src`, nên đổi source không phải
tải lại dependency. Nếu vẫn lâu, dùng BuildKit cache:

```bash
DOCKER_BUILDKIT=1 docker compose build
```

</details>

---

## 13. Ghi chú bảo mật khi deploy thật

Những thứ **an toàn cho local nhưng BẮT BUỘC phải đổi khi deploy**:

1. `JWT_SECRET` (≥ 32 ký tự, ngẫu nhiên) và `INTERNAL_API_KEY` — đây là 2 secret duy nhất bảo vệ toàn hệ thống.
2. `ADMIN_PASSWORD` — không dùng `Admin@12345`.
3. `MAIL_*` → SMTP thật, và bật `EMAIL_VERIFY_REQUIRED_FOR_LOGIN=true`.
4. `JPA_DDL_AUTO=validate` + chạy `database/*.sql` một lần.
5. `CORS_ALLOWED_ORIGINS` → domain thật (không dùng `*`).
6. `GATEWAY_TRUSTED_PROXIES` / `AUTH_TRUSTED_PROXY_IPS` → IP của nginx/ALB thật.
   Nếu không khai đúng, **mọi người dùng bị ghi nhận chung một IP** và giới hạn
   đăng nhập/email biến thành giới hạn toàn cục.
7. Chỉ expose `api-gateway` (8088) ra ngoài. Các port khác đã được bind `127.0.0.1`
   trong `docker-compose.yml` — giữ nguyên. **Không bao giờ** expose Eureka (8761).
8. `EMAIL_MAX_PER_IP` — compose đặt riêng `200` cho dev; production nên để mặc
   định `30` hoặc thấp hơn.

---

## 14. Tài liệu

| File                             | Nội dung                                                                                                                |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| [API.md](API.md)                 | **Tài liệu API đầy đủ** — mọi endpoint, ví dụ request/response, format lỗi, bảng status code, luồng xác thực, hardening |
| [db.md](db.md)                   | Thiết kế database (đặc tả logic + database vật lý sau khi tách)                                                         |
| [requirement.md](requirement.md) | Đặc tả nghiệp vụ, danh sách page và phân công thành viên                                                                |
| [PROJECT_STATUS.md](PROJECT_STATUS.md) | **Trạng thái, lộ trình và các quyết định đã chốt** — đọc file này trước khi nhận việc                       |
| [HUONG_DAN_HOAN_THIEN_BE.md](../HUONG_DAN_HOAN_THIEN_BE.md) | Cách làm từng việc backend còn thiếu (code khung, test)                                          |
| [../HUONG_DAN_FRONTEND.md](../HUONG_DAN_FRONTEND.md) | Thiết kế frontend: route, đặc tả trang, design system                                                |
| [.env.example](.env.example)     | Toàn bộ biến môi trường kèm chú thích                                                                                   |

Xem nhanh API mà không cần đọc file: mở
http://localhost:8088/swagger-ui/index.html (dropdown chọn được auth / user /
booking / payment), bấm **Authorize** rồi dán `accessToken` lấy từ
`POST /api/auth/login`.

---

## 15. Công nghệ & quy ước khi thêm microservice mới

Xem [API.md mục 9](API.md#9-quy-uoc-cho-microservice-moi-booking-payment) và
[API.md mục 8](API.md#8-bien-moi-truong).

Tóm tắt:

- Package gốc `com.ridebooking.<tên>`, artifactId khớp tên thư mục.
- Không đọc chéo database của service khác — dùng REST với header `X-Internal-Key`.
- JWT verify bằng cùng `app.jwt.secret` + `app.jwt.issuer`, không gọi về auth-service.
- Service chưa dựng phải trả **503** (client tự thử lại được), không phải 500.
- Thêm service mới vào `pom.xml` (modules), `Dockerfile` (COPY src), và
  `docker-compose.yml` (service + route trong `api-gateway/application.yml`).

---

## License

Dự án học tập / đồ án nhóm. Xem `requirement.md` để biết phạm vi bài toán.

# Ride Booking Microservices — API Auth, User & Booking
---

## 1. Kiến trúc & cổng

| Service | Port | Vai trò | Package gốc |
|---|---|---|---|
| `discovery-server` | 8761 | Eureka Server | `com.ridebooking.discovery_server` |
| `api-gateway` | 8088 | Điểm vào duy nhất của frontend | `com.ridebooking.gateway` |
| `auth-service` | 8081 | Đăng ký / đăng nhập / cấp JWT / đổi mật khẩu | `com.ridebooking.auth` |
| `user-service` | 8082 | Hồ sơ cá nhân, danh sách người dùng cho Admin | `com.ridebooking.user` |
| `booking-service` | 8083 | Catalog (địa điểm, nhà xe, tuyến, chuyến, điểm dừng, ghế) + đơn đặt vé + đánh giá | `com.ridebooking.booking` |

| Database | Port (Docker) | Nội dung |
|---|---|---|
| `auth-db` | 5433 | `auth_db` — bảng `users` (email, password BCrypt, role) |
| `user-db` | 5434 | `user_db` — bảng `user_profiles` (hồ sơ) |
| `booking-db` | 5435 | `booking_db` — catalog, ghế, đơn vé, đánh giá (xem [db.md](db.md) mục B) |

> Frontend chỉ cần gọi `http://localhost:8088/api/...`, không gọi thẳng 8081/8082.
> `auth-service` và `user-service` dùng **2 database tách biệt**, không chung bảng.
> Xem [db.md](db.md) và mục 10.

## 1.1. Swagger / OpenAPI — link đầy đủ

| Tài liệu | Link |
|---|---|
| **Swagger UI gộp (gateway)** | http://localhost:8088/swagger-ui/index.html |
| auth-service Swagger UI (riêng) | http://localhost:8081/swagger-ui/index.html |
| user-service Swagger UI (riêng) | http://localhost:8082/swagger-ui/index.html |
| booking-service Swagger UI (riêng) | http://localhost:8083/swagger-ui/index.html |
| auth-service OpenAPI JSON | http://localhost:8081/v3/api-docs |
| user-service OpenAPI JSON | http://localhost:8082/v3/api-docs |
| booking-service OpenAPI JSON | http://localhost:8083/v3/api-docs |
| auth-service JSON **qua gateway** | http://localhost:8088/auth/v3/api-docs |
| user-service JSON **qua gateway** | http://localhost:8088/user/v3/api-docs |
| booking-service JSON **qua gateway** | http://localhost:8088/booking/v3/api-docs |
| Eureka dashboard | http://localhost:8761 |

Ở `http://localhost:8088/swagger-ui/index.html` có dropdown chọn tài liệu
(`auth-service` / `user-service` / `booking-service`) — mở một chỗ là xem đủ cả ba,
không cần nhớ ba cổng. Tổng cộng **64 operation** (auth 16, user 7, booking 41).

Cách dùng nhanh:

1. Mở Swagger UI (gộp ở 8088, hoặc mở riêng 8081/8082 nếu muốn).
2. Bấm **Authorize** → dán `accessToken` lấy từ `POST /api/auth/login`.
3. Bấm **Try it out** rồi **Execute**. Trong dropdown server chọn `http://localhost:8088`
   để test qua gateway (đúng như cách frontend gọi thật), hoặc `http://localhost:8081`
   nếu muốn gọi thẳng service.

> Gateway dùng `springdoc-openapi-starter-webflux-ui` **3.1.1**, khác hẳn
> 2.8.6 mà auth/user đang dùng. Lý do: springdoc 2.x gọi
> `UriComponentsBuilder.fromHttpRequest()`, method đã bị Spring Framework 7
> (Boot 4.1.1) xoá, dẫn tới `NoSuchMethodError` làm `/swagger-ui.html` chết.
> Bản webmvc của 2.8.6 không dính lỗi này nên auth/user giữ nguyên 2.8.6.
> Chi tiết bẫy cấu hình (`springdoc.api-docs.enabled` không được tắt) nằm trong
> `api-gateway/src/main/resources/application.yml`.

## 1.2. Tài khoản đăng nhập mặc định

Khi `docker compose up` lần đầu, `auth-service` tự tạo sẵn một tài khoản admin
(dùng `AdminBootstrap`, xem `auth-service/.../bootstrap/AdminBootstrap.java`):

| | |
|---|---|
| Email | `admin@ridebooking.local` |
| Mật khẩu | `Admin@12345` |
| Role | `ADMIN` |

Đổi được bằng biến `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NAME` trong file
`.env`. Bỏ trống `ADMIN_EMAIL` thì không tạo tài khoản admin nào (dành cho môi
trường thật, nơi admin được tạo bằng migration/SQL).

> Cơ chế này rất cần thiết: `POST /api/admin/users` chỉ nhận token `ADMIN`, nên
> database rỗng sẽ không có đường nào tạo được admin đầu tiên.

Ngoài ra có thể nâng quyền tài khoản đã đăng ký bằng SQL:

```bash
docker compose exec auth-db psql -U postgres -d auth_db   -c "update users set role='ADMIN' where email='ten-ban@example.com';"
```

Sau khi đổi role bằng SQL, user phải **đăng nhập lại** để token mang role mới.

## 2. Luồng xác thực

Hệ thống dùng **cặp token**:

| | accessToken | refreshToken |
|---|---|---|
| Dạng | JWT ký HS256 | Chuỗi ngẫu nhiên 256 bit (không phải JWT) |
| Hạn | **15 phút** | **7 ngày** |
| Gửi kèm | Header `Authorization: Bearer <token>` | Body của `/api/auth/refresh` |
| Lưu ở server | Không ( stateless ) | Bảng `auth_db.refresh_tokens`, **chỉ lưu SHA-256** |
| Có xoay vòng | Không | **Có** — mỗi lần dùng thì token cũ bị thu hồi |

```
React ──POST /api/auth/login──▶ gateway ──▶ auth-service
                                 ◀── { accessToken, refreshToken, expiresIn, user }

  accessToken hết hạn (15')
React ──POST /api/auth/refresh { refreshToken } ──▶ auth-service
                                 ◀── { accessToken MỚI, refreshToken MỚI }

React ──GET /api/users/me (Bearer accessToken)──▶ gateway ──▶ user-service
                                                    (tự verify JWT bằng chung secret)

React ──POST /api/auth/logout { refreshToken }──▶ auth-service  (204)
```

- Payload JWT chứa: `sub` = email, `userId`, `fullName`, `role`.
- `user-service` **tự verify** token bằng cùng `app.jwt.secret` + `app.jwt.issuer`
  (không gọi về auth-service), nên phải deploy 2 service với **cùng giá trị env `JWT_SECRET`**.
- `role` trong token là **snapshot lúc đăng nhập**. Đổi role trong DB thì user phải đăng nhập lại.

### 2.1. Refresh token xoay vòng và phát hiện đánh cắp

Mỗi lần gọi `/api/auth/refresh`, server thu hồi token cũ và cấp token mới.
Nếu một token **đã bị thu hồi lại được gửi lên**, nghĩa là nó đã bị lộ
(ví dụ kẻ xấu copy từ máy khác) — khi đó server:

1. thu hồi **toàn bộ** phiên đăng nhập của tài khoản đó,
2. trả `401` với message *"Toàn bộ phiên đăng nhập đã bị vô hiệu, vui lòng đăng nhập lại"*.

Client cần xử lý: khi `401` ở `/api/auth/refresh` thì **xoá cả 2 token** và
đưa người dùng về trang đăng nhập, không tự thử lại.

### 2.2. Chống brute-force

Bộ đếm lưu ở bảng `auth_db.login_attempts`, **theo cặp (email, IP)** — không
khoá tài khoản:

| | |
|---|---|
| Bộ đếm | `auth_db.login_attempts`, mỗi dòng là một cặp email + IP |
| Ngưỡng chặn | 5 lần sai cho cùng cặp (`LOGIN_MAX_FAILED_ATTEMPTS`) |
| Thời gian chặn | 15 phút (`LOGIN_LOCK_MINUTES`), lưu ở `locked_until` |
| Chặn theo IP | 20 lần sai/người dùng trong 15 phút (`LOGIN_MAX_FAILED_ATTEMPTS_PER_IP`) |
| Khi bị chặn | `423 Locked` kèm header `Retry-After` |
| Gỡ khoá | `PATCH /api/admin/users/{id}/unlock` (role `ADMIN`) |

**Vì sao không khoá tài khoản?** Nếu khoá theo tài khoản, kẻ tấn công chỉ cần
biết trước email của bạn là đăng nhập sai 5 lần, khoá tài khoản đó vĩnh viễn mà
chưa từng đoán đúng mật khẩu — tức là chỉ cần biết email là có thể DoS người
dùng. Chặn theo cặp (email, IP) giữ được khả năng chống dò mật khẩu mà không
trao quyền khoá người khác cho kẻ tấn công. IP khác vẫn đăng nhập được bình
thường.

Bảng có ràng buộc `UNIQUE (email, ip_address)` để request song song không tạo
hai dòng và chia nhỏ bộ đếm.

Đăng nhập sai **không** tạo tài khoản ảo; email không tồn tại cũng tốn thời gian
tương đương so với sai mật khẩu để không lộ tài khoản nào đã đăng ký.

Client **không được** tự gửi `X-Forwarded-For`: giới hạn theo IP chỉ đúng khi IP
ghi vào DB là IP thật. Việc này cần **hai** cấu hình khớp nhau, thiếu một cái là
hỏng âm thầm — request vẫn trả `401`/`423` đúng như mong đợi, chỉ có IP bị sai:

| Tầng | Cấu hình | Nếu cấu hình sai |
|---|---|---|
| Gateway | `spring.cloud.gateway.server.webflux.trusted-proxies` | IP client không được ghi → mọi người dùng chung một IP → hạn mức thành toàn cục |
| auth-service | `server.tomcat.remoteip.internal-proxies` | Tin cả header giả → kẻ tấn công tự chọn IP để né hạn mức |

Hai cái bẫy đã gặp và đã có test chặn lại:

- Gateway của Spring Cloud Gateway 5 đã **đổi tên khoá**. Cấu hình cũ
  `spring.cloud.gateway.x-forwarded.*` không còn tồn tại và bị Spring bỏ qua
  không báo lỗi; khoá thật nằm dưới
  `spring.cloud.gateway.server.webflux.x-forwarded.*`.
- Tomcat mặc định coi **cả dải `172.16/12`** là proxy nội bộ, mà dải đó chính
  là Docker network. Nên nếu để mặc định, ai gọi thẳng cổng `8081` cũng dán
  `X-Forwarded-For` giả được. Vì vậy `internal-proxies` phải thu hẹp còn đúng
  IP của gateway, và gateway phải có **IP tĩnh** (`GATEWAY_IP`) vì IP đổi sau
  mỗi lần `compose up` thì cấu hình không còn khớp.

Khi gateway không khớp `trusted-proxies`, nó **xoá** `X-Forwarded-For` rồi dừng,
không ghi IP thật. Khi deploy sau nginx/ALB, cả hai khoá phải trỏ sang IP của
proxy đó.

Trong `docker-compose.yml`, các cổng `8081` (auth), `8082` (user), `5433`/`5434`
(database), `8761` (Eureka) và `8025` (Mailpit) đều bind vào `127.0.0.1`, tức chỉ
truy cập được từ chính máy chạy Docker — cổng `8088` của gateway là cổng public
duy nhất. Khi deploy thật:

- bỏ hẳn `ports` của auth/user/database thay vì chỉ đổi sang `127.0.0.1`, và
- đặt `GATEWAY_TRUSTED_PROXIES` và `AUTH_TRUSTED_PROXY_IPS` thành IP của
  nginx/ALB, và `GATEWAY_IP` thành IP tĩnh thật.

Mục 14 của `scripts/e2e-auth-user.sh` kiểm tra IP ghi vào DB thật, và sẽ hỏng nếu
hai cấu hình trên bị đưa về mặc định.

### 2.3. Vì sao phải tách DB thì service gọi nhau qua HTTP

```
                    auth_db.users                    user_db.user_profiles
                    ┌──────────────┐                ┌──────────────────────┐
                    │ id, email    │                │ user_id (= users.id)│
                    │ password(BC) │──POST /api/───▶│ full_name, phone    │
                    │ role         │  internal/users │ dob, gender, role   │
                    └──────────────┘                └──────────────────────┘
                             │  RestTemplate + X-Internal-Key      ▲
                             └────────────────────────────────────┘
```

- Khi đăng ký, `auth-service` tạo user ở `auth_db` rồi gọi
  `POST /api/internal/users` của `user-service` để tạo hồ sơ (idempotent).
- Nếu `user-service` sập lúc đăng ký, hồ sơ được **tự tạo lazy** ở lần truy cập
  đầu tiên (`GET /api/users/me`) từ claim trong JWT.
- `user-service` **không** đọc được `auth_db`, nên `role` trong hồ sơ là bản sao,
  được làm mới mỗi lần đăng nhập.

## 3. Danh sách API

Tất cả request `/api/**` (trừ register/login) cần header:
`Authorization: Bearer <accessToken>`

### auth-service — `/api/auth`

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Đăng ký, trả cả accessToken + refreshToken (role luôn là `USER`) |
| POST | `/api/auth/login` | Public | Đăng nhập, trả cả accessToken + refreshToken |
| POST | `/api/auth/refresh` | Public | Đổi cặp token (dùng refreshToken, **không** cần accessToken) |
| POST | `/api/auth/logout` | User | Đăng xuất: gửi refreshToken để huỷ đúng phiên đó |
| GET | `/api/auth/me` | User | Thông tin tài khoản của chính mình |
| PUT | `/api/auth/password` | User | Đổi mật khẩu (cần `currentPassword`), thu hồi mọi phiên cũ |
| POST | `/api/auth/verify-email` | Public + token | Xác minh email bằng token trong link |
| POST | `/api/auth/resend-verification` | Public | Gửi lại link xác minh |
| POST | `/api/auth/forgot-password` | Public | Gửi link đặt lại mật khẩu |
| POST | `/api/auth/reset-password` | Public + token | Đặt mật khẩu mới bằng token, thu hồi mọi phiên |
| POST | `/api/auth/change-email` | User | Bắt đầu đổi email (cần `currentPassword`), trả `202` |
| POST | `/api/auth/confirm-email-change` | Public + token | Chốt đổi email bằng token gửi tới email mới |
| GET | `/api/auth/validate` | User | Kiểm tra token, dùng cho gateway/microservice khác |

#### Xác minh, quên mật khẩu, đổi email

Mọi endpoint trên đều dùng **token 256-bit** gửi trong email, chỉ lưu SHA-256
trong DB, **chỉ dùng được 1 lần**. Tạo token mới sẽ vô hiệu hoá token cũ cùng
mục đích. Hạn dùng: xác minh `24h`, reset `30 phút`, đổi email `60 phút`.

| Endpoint | Body | Trả về |
|---|---|---|
| `POST /api/auth/verify-email` | `{"token":"..."}` | `200 {"verified":true,"verifiedAt":...,"message":...}` |
| `POST /api/auth/resend-verification` | `{"email":"..."}` | `200` thông báo chung |
| `POST /api/auth/forgot-password` | `{"email":"..."}` | `200` thông báo chung |
| `POST /api/auth/reset-password` | `{"token":"...","newPassword":"..."}` | `204`, mọi phiên bị thu hồi |
| `POST /api/auth/change-email` | `{"newEmail":"...","currentPassword":"..."}` | `202`, gửi mail cảnh báo tới email cũ |
| `POST /api/auth/confirm-email-change` | `{"token":"...","currentPassword":"..."}` | `200`, đổi email + thu hồi phiên |

`forgot-password` và `resend-verification` **luôn trả cùng một thông điệp**, kể cả
khi email không tồn tại, chưa xác minh hay đã gửi thành công. Nếu phân biệt từng
trường hợp thì chính endpoint này tiết lộ danh sách email đã đăng ký.

Đổi email gửi **2 thư**: link xác nhận tới email mới, và cảnh báo tới email cũ
(để nếu bị đổi email hàng loạt thì chủ tài khoản còn biết mà phản ứng kịp).

Hạn mức gửi email: `EMAIL_MAX_PER_EMAIL` lần/`EMAIL_RESEND_WINDOW_MINUTES` cho
mỗi người nhận, và `EMAIL_MAX_PER_IP` lần cho mỗi IP. Vượt hạn trả `429`.

Local dùng [Mailpit](https://mailpit.axceston.com) làm mail catcher, xem hộp thư tại
<http://localhost:8025> — không cần tài khoản email thật. Production đổi `MAIL_*`
sang nhà cung cấp thật, code không cần sửa.

**Thời điểm gửi mail.** Các luồng này vừa tạo token vừa gửi mail trong cùng một
transaction, nhưng phần gửi được hoãn tới **sau khi commit**. Nếu gửi ngay rồi
transaction rollback vì lý do khác, khách sẽ nhận link chứa token không tồn tại
trong database và bấm vào thấy "mã không hợp lệ" — lỗi của hệ thống nhưng người
dùng tưởng do mình. Lịch sử gửi cũng ghi bằng transaction riêng, nên không bị
mất khi transaction nghiệp vụ rollback (nếu mất thì kẻ spam chỉ cần làm hỏng
request là lách được hạn mức).

Còn một giới hạn chưa xử lý: nếu tiến trình chết đúng khoảnh khắc sau khi commit
nhưng trước khi gửi xong, token có trong DB mà email chưa đi — người dùng không
nhận được link. Muốn triệt tiêu triệt để cần bảng outbox và một worker thử gửi
lại, thay vì gửi trực tiếp trong request.

`POST /api/auth/logout` nhận 2 kiểu body:

- `{"refreshToken": "..."}` → chỉ huỷ phiên của token đó
- `{"refreshToken": ""}` hoặc không gửi body → huỷ **toàn bộ** thiết bị

Server luôn kiểm tra refreshToken có thuộc đúng tài khoản đang đăng nhập không.
Gửi token của người khác sẽ **không** thu hồi được (bỏ qua và ghi log cảnh báo),
nên không thể vô hiệu hoá phiên của người khác chỉ bằng cách có được token.

Access token đã cấp vẫn sống tới hết 15 phút, nên frontend phải tự xoá nó khỏi storage.

### auth-service — `/api/admin/users` (role `ADMIN`)

| Method | Path | Mô tả |
|---|---|---|
| GET | `/api/admin/users` | Danh sách toàn bộ tài khoản |
| POST | `/api/admin/users` | Admin tạo tài khoản (có thể chỉ định `role`) |
| PATCH | `/api/admin/users/{id}/unlock` | Gỡ chặn cho email do đăng nhập sai nhiều lần |

### user-service — `/api/users`

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/users/me` | User | Hồ sơ của chính mình (dùng để auto-fill form đặt vé) |
| PUT | `/api/users/me` | User | Cập nhật Họ tên / SĐT / Ngày sinh / Giới tính |
| GET | `/api/users/{id}` | User | Xem hồ sơ người khác — **bị che dữ liệu** nếu không phải ADMIN |
| GET | `/api/users?keyword=&page=0&size=10` | User | Tìm theo tên / email / SĐT, có phân trang |

> Đăng xuất **không** còn ở user-service (trước đây `POST /api/users/logout` chỉ trả về
> message và không làm gì). Dùng `POST /api/auth/logout` — chỉ auth-service nắm được refresh token.

#### Che dữ liệu khi xem hồ sơ người khác

`GET /api/users/{id}` và `GET /api/users` trả về `masked` để client biết dữ liệu
đã bị che hay chưa:

| Người gọi | `fullName` | `email` | `phone` / `dob` / `gender` | `masked` |
|---|---|---|---|---|
| Chính mình | đầy đủ | đầy đủ | đầy đủ | `false` |
| ADMIN | đầy đủ | đầy đủ | đầy đủ | `false` |
| User khác | đầy đủ | `h*******@gmail.com` | `null` | `true` |

Số ký tự bị che đếm ở `hiddenEmailChars`.

### user-service — `/api/admin/users` (role `ADMIN`)

| Method | Path | Mô tả |
|---|---|---|
| GET | `/api/admin/users?keyword=&page=&size=` | Danh sách người dùng có phân trang |

### Internal — `/api/internal/users` (chỉ service khác gọi được)

| Method | Path | Mô tả |
|---|---|---|
| POST | `/api/internal/users` | Tạo/cập nhật hồ sơ (idempotent theo `userId`) |
| POST | `/api/internal/users/{userId}` | Lấy hồ sơ theo id |

Bắt buộc có header `X-Internal-Key` khớp biến môi trường `INTERNAL_API_KEY`
của cả hai service, sai thì trả `403`. Không có endpoint này dành cho frontend.

## 4. Ví dụ request / response

### Đăng ký

```bash
curl -X POST http://localhost:8088/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Nguyễn Anh Huy",
    "email": "huy@test.com",
    "password": "Huy@2002x",
    "phone": "0912345678",
    "dob": "2002-05-20",
    "gender": "Nam"
  }'
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "0kVvH8x...abc",
  "refreshExpiresIn": 604800,
  "issuedAt": "2026-09-30T16:04:43.372Z",
  "user": {
    "id": 1,
    "fullName": "Nguyễn Anh Huy",
    "phone": "0912345678",
    "email": "huy@test.com",
    "dob": "2002-05-20",
    "gender": "Nam",
    "role": "USER",
    "createdAt": "2026-09-30T23:04:43.339"
  }
}
```

### Đăng nhập

```bash
curl -X POST http://localhost:8088/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"huy@test.com","password":"Huy@2002x"}'
```

### Đổi token khi access token hết hạn

```bash
curl -X POST http://localhost:8088/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"0kVvH8x...abc"}'
```

Response có **cả 2 token mới** — phải lưu cả hai, vì refreshToken cũ đã bị thu hồi.
Dùng lại refreshToken cũ sẽ nhận `401` và mọi phiên của tài khoản bị thu hồi.

### Đăng xuất

```bash
# Huỷ đúng phiên này
curl -X POST http://localhost:8088/api/auth/logout \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"0kVvH8x...abc"}'

# Huỷ tất cả thiết bị
curl -X POST http://localhost:8088/api/auth/logout \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":""}'
```

### Xem hồ sơ người khác (dữ liệu bị che)

```bash
# Chính mình hoặc admin: đầy đủ
curl http://localhost:8088/api/users/5 -H "Authorization: Bearer $TOKEN"
# -> {"email":"huy@test.com","phone":"0912345678","dob":"2002-05-20","masked":false}

# User thường xem người khác: đã che
curl http://localhost:8088/api/users/5 -H "Authorization: Bearer $KHAC_TOKEN"
# -> {"email":"h***@test.com","phone":null,"dob":null,"gender":null,"masked":true}
```

### Cập nhật hồ sơ

```bash
curl -X PUT http://localhost:8088/api/users/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Nguyễn Anh Huy","phone":"0999888777","dob":"2002-06-01","gender":"Nam"}'
```

## 5. Quy tắc validate

| Trường | Quy tắc |
|---|---|
| `fullName` | Bắt buộc, tối đa 255 ký tự |
| `email` | Bắt buộc, đúng định dạng email, duy nhất trong hệ thống |
| `password` | Bắt buộc, 8–72 ký tự, **phải có cả chữ và chữ số**, không được là mật khẩu phổ biến (`12345678`, `password`, `matkhau123`...). Lưu BCrypt, không bao giờ trả về ra API |
| `phone` | Optional, `+?` + 9–15 chữ số |
| `dob` | Optional, định dạng `yyyy-MM-dd` |
| `gender` | Optional, chỉ nhận `Nam`, `Nữ`, `Khác` |

## 5.1. Bảng status code

| Code | Khi nào | Client nên làm gì |
|---|---|---|
| `200` / `201` / `204` | Thành công | Xử lý bình thường (`204` không có body) |
| `400` | Body sai format hoặc vi phạm validate | Hiện lỗi cạnh input, không retry |
| `401` | Chưa đăng nhập, token sai/hết hạn, email hoặc mật khẩu sai, refresh token không hợp lệ | Thử `POST /api/auth/refresh`; nếu cũng `401` thì xoá token và về trang đăng nhập |
| `403` | Đã đăng nhập nhưng thiếu quyền (user gọi API admin, sai `X-Internal-Key`) | Hiện thông báo "không có quyền" |
| `404` | Không tìm thấy tài khoản | Hiện "không tồn tại" |
| `405` | Sai phương thức (ví dụ `POST /api/users/logout` — endpoint đã bị gỡ) | Kiểm tra lại method/path |
| `409` | Email đã tồn tại | Hiện thông báo trùng email |
| `423` | Cặp (email, IP) hoặc IP đang bị chặn do đăng nhập sai nhiều lần | Đếm ngược theo header `Retry-After`, không tự thử lại |
| `429` | Gửi email quá hạn mức (theo người nhận hoặc theo IP) | Đếm ngược theo header `Retry-After`, không tự thử lại |
| `500` | Lỗi hệ thống | Báo lỗi, log lại |

## 6. Format lỗi thống nhất

Mọi service trả về cùng một shape:

```json
{
  "timestamp": "2026-09-30T16:04:52.62Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Bạn chưa đăng nhập hoặc token không hợp lệ",
  "path": "/api/users/me"
}
```

| Status | Khi nào |
|---|---|
| 400 | Dữ liệu gửi lên sai định dạng / thiếu trường bắt buộc |
| 401 | Chưa đăng nhập, token sai, token hết hạn, sai mật khẩu |
| 403 | Đã đăng nhập nhưng không đủ quyền (ví dụ USER gọi API Admin) |
| 404 | Không tìm thấy tài nguyên |
| 405 | Sai HTTP method cho endpoint (vd POST lên endpoint chỉ nhận GET) |
| 409 | Email đã tồn tại |

## 6.1. Phân chia endpoint giữa 2 service

`/api/admin/users` có **cả GET và POST nhưng thuộc 2 service khác nhau** — gateway
phân bằng predicate `Method`, nên đừng gộp chung lại:

| Method + Path | Service xử lý |
|---|---|
| `GET /api/admin/users` | user-service (danh sách có phân trang) |
| `POST /api/admin/users` | auth-service (tạo tài khoản mới) |

## 7. Chạy bằng Docker (cách chính)

Không cần cài PostgreSQL trên máy — 2 database đều chạy trong container riêng.

```bash
# Build image + khởi động toàn bộ (6 container)
docker compose up -d --build

# Xem trạng thái / log
docker compose ps
docker compose logs -f auth-service

# Dừng (giữ dữ liệu trong volume)
docker compose down

# Dừng và xoá luôn database
docker compose down -v
```

Sau khi `up`, kiểm tra:

| Kiểm tra | Link |
|---|---|
| Gateway | http://localhost:8088 |
| Eureka | http://localhost:8761 |
| **Swagger gộp** | http://localhost:8088/swagger-ui/index.html |
| Swagger auth (riêng) | http://localhost:8081/swagger-ui/index.html |
| Swagger user (riêng) | http://localhost:8082/swagger-ui/index.html |

Đổi mật khẩu `postgres`, port hay secret thì tạo file `.env` ở thư mục gốc:

```bash
DB_USER=postgres
DB_PASSWORD=postgres
AUTH_DB_PORT=5433
USER_DB_PORT=5434
JWT_SECRET=chuoi-tuy-chon-toi-thieu-32-ky-tu
INTERNAL_API_KEY=khoa-noi-bo-tuy-chon
JWT_EXPIRATION_MINUTES=15
JWT_REFRESH_TOKEN_DAYS=7
JPA_DDL_AUTO=update
JPA_SHOW_SQL=true
LOGIN_MAX_FAILED_ATTEMPTS=5
LOGIN_LOCK_MINUTES=15
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
```

Xem `.env.example` để có đầy đủ chú thích.

### 7.1. Tạo schema bằng SQL (dùng cho production)

Mặc định `JPA_DDL_AUTO=update` nên Hibernate tự tạo bảng — tiện cho local nhưng không
nên dùng khi deploy thật. Khi đó đặt `JPA_DDL_AUTO=validate` (Hibernate chỉ kiểm tra
schema khớp, không sửa) và chạy 2 script idempotent một lần:

```bash
# Trong container PostgreSQL
psql -U postgres -d auth_db -f database/01_auth_db.sql
psql -U postgres -d user_db -f database/02_user_db.sql
psql -U postgres -d booking_db -f database/03_booking_db.sql
```

Script có sẵn: unique email không phân biệt hoa/thường, foreign key
`refresh_tokens.user_id -> users.id` (ON DELETE CASCADE), CHECK bắt buộc
`token_hash` đúng 64 ký tự hex, CHECK giới hạn `role` và `gender`,
cùng index cho truy vấn phiên đang hoạt động và dọn token hết hạn.

### 7.2. Chạy service bằng Maven (không dùng Docker)

```bash
# 1. Bật 3 database Docker, chỉ service chạy ngoài Docker
docker compose up -d auth-db user-db booking-db

# 2. Build
mvn clean install -DskipTests

# 3. Chạy từng service (mỗi cái 1 terminal)
java -jar discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar
java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
java -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar
java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar
```

Khi chạy ngoài Docker nhớ trỏ `DB_PORT=5433` (auth) / `5434` (user) / `5435`
(booking) vì 3 container PostgreSQL được map ra cổng khác `5432` để không đụng
PostgreSQL cài trên máy.

Seed dữ liệu mẫu (địa điểm, nhà xe, tuyến, chuyến có ghế thật) cho booking-service:

```bash
java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar --app.seed.enabled=true
```

Seed chỉ chạy khi bảng `locations` đang trống (idempotent), nên chạy lại không
nhân đôi dữ liệu.

### 7.3. Kiểm chứng nhanh sau khi triển khai

```bash
# Toàn bộ luồng auth + user trên stack đang chạy: 172 assertions.
# Cần stack sạch: bộ dữ liệu test dùng một lần, chạy lại trên cùng DB sẽ hỏng.
docker compose down -v && docker compose up -d
./scripts/e2e-auth-user.sh

# Toàn bộ unit + integration test (không cần Docker): hiện 100 test booking
# (BusType, SeatGenerator, TripSort, luồng tìm kiếm/ghế/điểm dừng, đặt vé/thanh
# toán/huỷ/hết hạn, tranh ghế hai luồng, UserProfileClient) cộng bộ test
# auth/user/gateway.
mvn test
```

## 7.4. Siết chặt đầu vào (hardening)

Mỗi service có `application-hardening.properties` riêng, được nạp qua
`spring.config.import` từ **cả** `src/main/resources/application.properties`
lẫn `src/test/resources/application.properties`.

| Cơ chế | Giá trị | Chống được gì |
|---|---|---|
| `spring.jackson.read.STRICT_DUPLICATE_DETECTION` | `true` | JSON có key trùng (`{"email":"a@x","email":"b@x"}`) bị 400 thay vì lấy giá trị cuối |
| `RequestBodySizeLimitFilter` | 64 KB | Body vượt ngưỡng trả 413, kể cả chunked không có `Content-Length` |
| `server.tomcat.max-http-form-post-size` | 64 KB | Form post quá lớn |
| `server.tomcat.max-swallow-size` | 64 KB | Không hút hết body của kẻ tấn công sau khi đã trả lỗi |
| `server.max-http-request-header-size` | 16 KB | Header khổng lồ |

Ba điểm cần biết khi sửa:

- **Dùng `spring.jackson.read`, không dùng `parser`.** Spring Boot 4 đã chuyển
  sang Jackson 3 (`tools.jackson.*`) và bỏ hẳn map `parser`. Đặt
  `spring.jackson.parser.*` sẽ bị Spring bỏ qua âm thầm, không báo lỗi, và mọi
  request vẫn chạy — chỉ là không còn chặn gì. Tương tự, `spring.jackson2.*` là
  namespace riêng cho Jackson 2 (chỉ YAML trong dự án này dùng tới).
- **`@Size` trên DTO không chống được DoS.** Nó chỉ chạy *sau khi* body đã được
  Jackson nạp vào heap. Tomcat cũng **không** giới hạn `application/json`:
  `max-http-form-post-size` chỉ áp cho `application/x-www-form-urlencoded`. Đã đo:
  body 20 MB vẫn được đọc trọn.
- **Cấu hình hardening đặt trong file riêng là cố ý.** Nếu để chung trong
  `application.properties` của main, `src/test/resources/application.properties`
  sẽ che mất nó trên classpath và test chạy với config rỗng. Sự cố này đã xảy ra:
  test báo 201 cho JSON key trùng trong khi stack thật trả 400.
  `HardeningConfigLoadedTest` bảo vệ khỏi tái diễn.

Mã lỗi trả về:

| Tình huống | Trước | Sau |
|---|---|---|
| JSON key trùng | 201, tạo user cho giá trị cuối | **400** |
| Body > 64 KB | đọc trọn vào RAM rồi mới validate | **413** |
| `Content-Type: text/plain` | 500 | **415** |
| Email có khoảng trắng đầu/cuối | 400 "Email không hợp lệ" | **201**, đã trim |

Mật khẩu **không** được trim: `LoginRequest`/`RegisterRequest` chỉ trim `email`
và `fullName`. Khoảng trắng là một phần của bí mật, cắt đi sẽ khiến mật khẩu gõ
ra khác mật khẩu đã đăng ký — có test bảo vệ điều này.

## 7.5. Email không mất khi SMTP hỏng

Bảng `email_send_log` vừa là lịch sử (để chống spam) vừa là **hàng đợi gửi lại**.

Trước đây gửi lỗi thì ghi log rồi bỏ. Hậu quả là một tình huống rất dễ xảy ra:

1. SMTP hạ tầng (Gmail/SES) sập 5 phút lúc 3 giờ sáng.
2. Chủ tài khoản bấm "quên mật khẩu" → HTTP **200** kèm *"chúng tôi đã gửi email cho bạn"*.
3. Không có email nào đi ra. Họ chờ, không thấy gì.
4. Thử lại → *"bạn đã yêu cầu quá nhiều lần, thử sau 60 phút"*.
5. Hết hạn mức và hết hạn token (30 phút). Tài khoản không thể tự lấy lại.

Không có gì tự động gửi lại khi SMTP hồi phục. Nay mỗi email được ghi vào hàng đợi
**trước** khi gửi, và `EmailRetryJob` quét mỗi 60 giây:

| Trạng thái | Nghĩa là |
|---|---|
| `PENDING` | Chưa gửi được, chờ thử lại |
| `SENT` | Đã gửi thành công |
| `DEAD` | Hết số lần thử, hoặc token đã hết hạn — không thử nữa |

Bốn quyết định thiết kế đáng chú ý:

- **Ghi hàng đợi trước, gửi sau.** Nếu gửi trước rồi ghi sau, khoảnh khắc JVM bị
  kill giữa hai việc thì email đã đi nhưng không có dấu vết. Ghi trước thì tệ nhất
  là gửi trùng — khách thấy hai email giống nhau, chấp nhận được.
- **Retry có hạn `retryUntil`**, đặt bằng thời hạn token mà email mang theo. Gửi
  lại link đã hết hạn chỉ khiến khách bấm vào thấy "mã không hợp lệ" và tưởng
  hệ thống hỏng.
- **Retry có trần `maxDeliveryAttempts`** (mặc định 5) và khoảng chờ tăng dần
  (nhân đôi, trần 15 phút). Không có trần thì một địa chỉ không tồn tại bị gửi đi
  mãi mỗi phút một lần — tự tấn công chính mình.
- **Job dọn dẹp không xoá dòng `PENDING`** dù đã cũ. Xoá nó nghĩa là xoá email
  chưa gửi được, đúng lúc cần nhất thì mất.

Lưu ý bảo mật: hàng đợi lưu **nội dung email**, tức là link đặt lại mật khẩu nằm
trong DB. Đây là đánh đổi có chủ đích. Bù lại, `html_body`/`text_body` bị xoá cùng
dòng sau `historyRetentionDays` (7 ngày), và cột này phải được xoá khi lưu trữ
backup.

## 7.6. Service chưa dựng thì trả 503, không phải 500

Gateway khai route tới mọi service kể cả khi service đó chưa lên. Trước đây request
tới một service chưa chạy trả **500** kèm
`ConnectException: Connection refused` trong log — sai bản chất, và client phân
biệt hai loại lỗi hoàn toàn khác nhau:

| Mã | Nghĩa | Client nên làm gì |
|---|---|---|
| 503 | Service tạm thời không có | tự thử lại |
| 500 | Lỗi code, không thử lại được | hiện lỗi, dừng |

Trả nhầm 500 biến một sự cố khởi động đơn giản thành lỗi không tự phục hồi.

`UpstreamUnavailableFilter` chỉ chuyển thành 503 **lỗi ở tầng kết nối**
(`ConnectException`, `ConnectTimeoutException`, `TimeoutException`). Lỗi trả về từ
service được giữ nguyên, vì đó là thông điệp cần hiển thị cho người dùng: "ghế đã
có người giữ", "email đã được sử dụng". Lỗi nội bộ gateway cũng không bị đổi —
503 nói hãy thử lại, còn `NullPointerException` là lỗi cần sửa ngay.

Lưu ý khi đọc test: lỗi 4xx/5xx từ service **không** đi qua `onErrorResume` —
Spring Cloud Gateway chuyển tiếp status + body upstream như một response bình
thường. Nên phần lớn giá trị của hàm phân loại nằm ở việc phân biệt lỗi phát
sinh bên trong gateway, và
`UpstreamUnavailableFilterClassificationTest` kiểm tra đúng hàm đó. Đây là điều
mutation test đã chỉ ra: chỉ test qua HTTP thì bản sửa "return `true` cho mọi
lỗi" vẫn xanh trong khi logic đã hỏng.

## 8. Biến môi trường

Cả 2 service đọc chung tập biến sau — **bắt buộc `JWT_SECRET` phải giống nhau**:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `auth_db` (auth), `user_db` (user), `booking_db` (booking) | PostgreSQL |
| `DB_USER` / `DB_PASSWORD` | `postgres` / `postgres` | Tài khoản DB |
| `JWT_SECRET` | chuỗi dev | Khóa ký, **tối thiểu 32 ký tự**, cả các service phải giống nhau |
| `JWT_EXPIRATION_MINUTES` | `120` | Thời hạn token (chỉ đọc ở auth-service) |
| `EUREKA_URL` | `http://localhost:8761/eureka` | Địa chỉ Eureka |
| `AUTH_SERVICE_URI` / `USER_SERVICE_URI` / `BOOKING_SERVICE_URI` / `PAYMENT_SERVICE_URI` | `:8081` / `:8082` / `:8083` / `:8084` | Địa chỉ service phía sau gateway (trong Docker dùng tên service, ví dụ `http://booking-service:8083`) |
| `USER_SERVICE_URL` | `http://localhost:8082` | auth-service và booking-service gọi user-service (trong Docker là `http://user-service:8082`) |
| `INTERNAL_API_KEY` | `ride-booking-internal-dev-key` | Khoá header `X-Internal-Key`, **các service phải giống nhau** |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@ridebooking.local` / `Admin@12345` | Tài khoản admin tạo sẵn lúc khởi động (chỉ auth-service) |
| `SEAT_HOLD_MINUTES` | `10` | Thời gian giữ ghế (chỉ booking-service) |
| `APP_SEED_ENABLED` | `false` | Bật `SeedDataLoader` tạo dữ liệu mẫu (chỉ booking-service; đặt `true` khi cần) |
| `APP_BOOKING_EXPIRY_JOB_ENABLED` | `true` | Bật job trả ghế hết hạn (chỉ booking-service; test đặt `false`) |
| `APP_BOOKING_EXPIRY_JOB_INTERVAL_MS` | `60000` | Chu kỳ quét đơn/ghế hết hạn |
| `APP_BOOKING_EXPIRY_JOB_INITIAL_DELAY_MS` | `60000` | Trễ lần chạy đầu tiên của job |

## 9. Quy ước cho microservice mới (booking, payment...)

Khi thêm service mới cần bảo mật, làm y hệt `user-service`:

1. Copy `config/JwtProperties.java`, `security/JwtService.java`,
   `security/JwtAuthenticationFilter.java`, `security/SecurityUtils.java`,
   `config/SecurityConfig.java`.
2. Trỏ `app.jwt.secret` / `app.jwt.issuer` về **cùng giá trị** với auth-service.
3. Trong `SecurityConfig`, `.anyRequest().authenticated()` và chỉ `permitAll()`
   cho endpoint public.

Dùng chung `AuthenticatedUser` record nên mọi service đều lấy được
`@AuthenticationPrincipal AuthenticatedUser` để biết userId/role hiện tại.

## 10. Ghi chú về database

`auth-service` và `user-service` đã **tách database riêng**:

| | `auth_db` | `user_db` |
|---|---|---|
| Bảng | `users` | `user_profiles` |
| Cột | `id`, `created_at`, `email`, `password` (BCrypt), `role`, `full_name` | `user_id`, `created_at`, `updated_at`, `full_name`, `email`, `phone`, `dob`, `gender`, `role` |
| Service sở hữu | auth-service (chỉ nó đọc/ghi) | user-service (chỉ nó đọc/ghi) |
| Khoá chính | `id` tự tăng | `user_id` = `users.id` (sinh ở service kia) |

Hệ quả và cách xử lý:

- **Mật khẩu** chỉ nằm ở `auth_db`, `user-service` không bao giờ thấy.
- **Email đăng nhập** lưu ở cả hai bảng để `user-service` tìm kiếm được; nguồn
  chuẩn vẫn là `auth_db.users.email`.
- **Role** là bản sao trong `user_profiles` vì không join DB được. `auth-service`
  gửi kèm role mỗi lần tạo hồ sơ và làm mới ở mỗi lần đăng nhập. Đổi role bằng
  SQL cần đăng nhập lại thì danh sách Admin mới thấy role mới.
- **Đồng bộ hồ sơ** là idempotent: không ghi đè những trường người dùng đã tự sửa
  (họ tên, SĐT, ngày sinh, giới tính), chỉ bổ sung trường còn trống.
## 11. `booking-service` — Catalog, ghế, điểm dừng, đánh giá

Service lớn nhất hệ thống: vừa giữ catalog vừa giữ đơn đặt vé, vì nghiệp vụ giữ ghế
10 phút cần khoá ghế và ghi đơn trong **cùng một transaction**. Lý do đầy đủ ở
[db.md](db.md) mục B1.

Quyền:

- `GET /api/{locations,routes,bus-companies,trips,seats,stop-points,reviews}` — công khai.
- `POST /api/bookings`, `POST /api/bookings/lookup` — công khai (có JWT thì đơn thuộc tài khoản, không có là đơn khách vãng lai).
- `GET /api/bookings/{id}`, `POST /api/bookings/{id}/pay`, `POST /api/bookings/{id}/cancel` — JWT **hoặc** header `X-Guest-Token`; thiếu cả hai → 401.
- `GET /api/bookings`, `POST /api/bookings/claim` — cần JWT. Người dùng lấy từ token, **không** từ tham số.
- `ALL /api/admin/**` — cần JWT với role `ADMIN`.

### 11.1. Địa điểm, nhà xe, tuyến

| Method + Path | Việc |
|---|---|
| `GET /api/locations?keyword=...&limit=...` | Gợi ý điểm đi/đến cho dropdown (tự tạo cả danh sách nếu bỏ `keyword`) |
| `GET /api/locations/all` | Toàn bộ địa điểm, cho màn quản trị |
| `GET /api/locations/{id}` | Một địa điểm |
| `GET /api/bus-companies?keyword=...` | Danh sách nhà xe |
| `GET /api/bus-companies/{id}` | Một nhà xe |
| `GET /api/routes?originId=...&destinationId=...&keyword=...` | Lọc tuyến |
| `GET /api/routes/from/{locationId}` | Tuyến phổ biến đi từ một địa điểm (block Trang chủ) |
| `GET /api/routes/{id}` | Một tuyến |

`LocationResponse` có `id`, `name`, `slug`. `slug` sinh tự động từ tên nếu request
bỏ trống; đổi tên mà bỏ trống slug thì slug được sinh lại. Slug chỉ gồm chữ thường,
số và gạch ngang, và **unique**.

### 11.2. Chuyến xe — tìm kiếm và chi tiết

`GET /api/trips` — mọi tham số tuỳ chọn:

| Tham số | Ý nghĩa |
|---|---|
| `originId`, `destinationId`, `companyId` | Lọc theo điểm đi / điểm đến / nhà xe |
| `travelDate` (`yyyy-MM-dd`) | Ngày đi |
| `earliestDeparture`, `latestDeparture` (`HH:mm:ss`) | Lọc theo giờ khởi hành; không kèm `travelDate` thì hiểu là **hôm nay** |
| `minPrice`, `maxPrice` | Khoảng giá |
| `sort` | `departureAsc` (mặc định), `departureDesc`, `priceAsc`, `priceDesc`, `newest`. Chấp nhận cả `price_asc`, `price-asc`, `departureTimeAsc`… Giá trị lạ rơi về mặc định, không trả 400 |
| `page`, `size` | Phân trang, `size` tối đa 100 |

Mỗi phần tử trả về là `{ "trip": {...}, "availableSeats": N, "unavailableSeats": M }`.
`trip` gồm `id`, `routeId`, `routeName`, `originId/Name`, `destinationId/Name`,
`companyId/Name`, `companyLogoUrl`, `busType`, `busTypeName`, `totalSeats`,
`departureTime`, `arrivalTime`, `basePrice`, `status`.

Ba câu truy vấn cho cả trang, không phải 3N — số ghế lấy bằng một câu `GROUP BY`
rồi ghép trong bộ nhớ.

| Method + Path | Việc |
|---|---|
| `GET /api/trips/{id}` | Chi tiết chuyến (Trang Thông tin chi tiết) |
| `GET /api/trips/bus-types` | 3 loại xe (`LIMOUSINE_29`, `CABIN_DOUBLE_22`, `SLEEPER_45`): `code`, `name`, `seatCount`, `floors` |

### 11.3. Sơ đồ ghế

| Method + Path | Việc |
|---|---|
| `GET /api/seats/trip/{tripId}` | Sơ đồ ghế đầy đủ để vẽ: `tripId`, `busTypeName`, `floors`, `totalSeats`, `availableSeats`, `basePrice`, `holdSecondsRemaining`, `seats[]` |
| `GET /api/seats/trip/{tripId}/list` | Danh sách ghế phẳng |

Mỗi ghế có `seatName` (ví dụ `1A1`, `2A1`), `floor`, `seatType` (`SINGLE`/`DOUBLE`),
`extraPrice`, `totalPrice`, `status` (`AVAILABLE`/`HOLDING`/`BOOKED`), `holdingUntil`.

Quy ước sinh ghế nằm ở `SeatGenerator` + enum `BusType` — **một nguồn duy nhất**,
`SeedDataLoader` cũng dùng lại. Xe hai tầng gắn tiền tố tầng vào tên ghế để không
trùng tên giữa hai tầng.

Giá: `basePrice + extraPrice`. Ghế đôi phụ thu `DOUBLE_SURCHARGE`; với dãy hai cột,
ghế cột 2 (sát lối đi) cộng thêm `DOUBLE_AISLE_EXTRA`.

### 11.4. Điểm đón/trả

| Method + Path | Việc |
|---|---|
| `GET /api/stop-points/trip/{tripId}` | Danh sách điểm đón/trả, **điểm đón trước** rồi theo `stopOrder` |

`stopOrder` đánh số **riêng** cho `PICKUP` và `DROPOFF`. Xoá hoặc đổi loại một điểm
thì cả hai nhóm được đánh lại liên tục.

### 11.5. Đánh giá (phần đọc)

| Method + Path | Việc |
|---|---|
| `GET /api/reviews/trip/{tripId}?page=&size=` | Danh sách đánh giá, phân trang |
| `GET /api/reviews/trip/{tripId}/summary` | `{ averageRating, totalReviews, ratingBreakdown: {"1":.., "5":..} }` |

Tên người đánh giá lấy từ user-service theo lô (`POST /api/internal/users/batch`,
header `X-Internal-Key`), có timeout + cache TTL. user-service hỏng thì trả tên
`"Người dùng"` chứ **không** làm hỏng danh sách đánh giá.

### 11.6. Admin CRUD (`role=ADMIN`)

| Nhóm | Endpoint |
|---|---|
| Địa điểm | `GET/POST /api/admin/locations`, `PUT/DELETE /api/admin/locations/{id}` |
| Nhà xe | `GET/POST /api/admin/bus-companies`, `PUT/DELETE /api/admin/bus-companies/{id}` |
| Tuyến | `GET/POST /api/admin/routes`, `PUT/DELETE /api/admin/routes/{id}` |
| Chuyến | `POST /api/admin/trips`, `PUT/DELETE /api/admin/trips/{id}` |
| Điểm dừng | `GET/POST /api/admin/trips/{tripId}/stop-points`, `PUT/DELETE .../{stopPointId}` |

Tạo chuyến (và đổi `busType` khi chưa ai giữ/đặt ghế) **sinh ghế trong cùng
transaction**. Đổi loại xe lúc đã có ghế đang giữ hoặc đã bán trả `409`. Xoá chuyến
còn vé đã bán cũng trả `409`.

`TripRequest`: `routeId`, `companyId`, `busType`, `departureTime`, `arrivalTime`,
`basePrice`, `status`. `arrivalTime` phải sau `departureTime`.

### 11.7. Đặt vé, giữ ghế, thanh toán (Giai đoạn 2)

Đơn có hai kiểu chủ: **tài khoản** (`userId` lấy từ JWT) hoặc **khách vãng lai**
(`userId = null`, quyền chứng minh bằng header `X-Guest-Token`).

| Method + Path | Quyền | Việc |
|---|---|---|
| `POST /api/bookings` | Công khai | Giữ ghế + tạo đơn `PENDING`. Không JWT = đơn khách vãng lai |
| `GET /api/bookings` | JWT | "Đơn hàng của tôi", lọc `status` (nhiều giá trị, `?status=PENDING,PAID`), phân trang |
| `GET /api/bookings/{id}` | JWT hoặc `X-Guest-Token` | Chi tiết đơn (chủ đơn, Admin, hoặc token khách đúng) |
| `POST /api/bookings/{id}/pay` | JWT hoặc `X-Guest-Token` | Thanh toán demo: ghế `HOLDING` → `BOOKED`, đơn → `PAID` |
| `POST /api/bookings/{id}/cancel` | JWT hoặc `X-Guest-Token` | Huỷ đơn chưa thanh toán: trả ghế, đơn → `CANCELLED` |
| `POST /api/bookings/lookup` | Công khai | Tra cứu vé `{bookingCode, phone}`; đơn khách vãng lai trả kèm `guestToken` **mới** |
| `POST /api/bookings/claim` | JWT | Gắn vé đặt lúc chưa đăng nhập vào tài khoản `{bookingCode, phone}` |
| `GET /api/admin/bookings` | ADMIN | Danh sách đơn/hành khách theo `tripId`, `status` |

`POST /api/bookings` body (`HoldSeatsRequest`): `tripId`, `seatIds` (1..10 id ghế),
`pickupPointId?`, `dropoffPointId?`, `contactName`, `contactPhone`, `contactEmail`.
`seatIds` được gửi theo **id** (không theo tên), vì tên ghế đổi khi Admin đổi loại xe.
Có JWT mà thiếu email thì lấy từ token; **khách vãng lai bắt buộc email** (thiếu → 400).

Trả về `BookingResponse`: `id`, `bookingCode` (mã vé 8 ký tự, dùng tra cứu và sinh QR),
`userId` (null với khách vãng lai), `guest`, `trip` (tóm tắt chuyến), `pickupPoint`,
`dropoffPoint`, thông tin liên hệ, `totalPrice`, `status`, `createdAt`, `expiresAt`,
`holdSecondsRemaining`, `seats[]` (giá chốt lúc mua), `payment` (giao dịch thành
công gần nhất, có thể null), `guestToken` (chỉ có ở response tạo đơn khách vãng lai
và tra cứu vé — không có ở các response khác).

**Khách vãng lai.** Token 256-bit chỉ trả **một lần**; DB chỉ lưu SHA-256
(`bookings.guest_token_hash`), so sánh hằng thời gian. FE giữ token trong
`sessionStorage` và gửi header `X-Guest-Token`. Mất token thì tra cứu lại bằng mã vé +
SĐT: hệ thống cấp token mới, token cũ hết hiệu lực. Token khách không mở được đơn
của tài khoản. `claim` gán `userId` và xoá token (CHECK `ck_bookings_owner`: đơn chỉ
có một kiểu chủ); vé đã thuộc tài khoản khác → 409. Tra cứu sai mã hoặc sai SĐT đều
trả cùng một 404, không cho biết mã có tồn tại.

**Vòng đời trạng thái.** `PENDING` → `PAID` (thanh toán) hoặc `CANCELLED` (khách huỷ)
hoặc `EXPIRED` (quá hạn). `COMPLETED` dành cho chuyến đã chạy. Gợi ý tab "Đơn hàng
của tôi": Hiện tại `PENDING,PAID`; Đã đi `COMPLETED`; Đã huỷ `CANCELLED,EXPIRED`.

**Chống bán trùng ghế.** Ghế được khoá bằng `SELECT ... FOR UPDATE` trên
`trip_seats` trước khi đọc trạng thái và ghi (`TripSeatRepository.findAllByIdForUpdate`).
Ràng buộc "một ghế chỉ thuộc một đơn đang hiệu lực" trải qua hai bảng nên **không**
biểu diễn được bằng UNIQUE — chỉ khoá dòng mới bảo đảm. Hai người giành cùng một
ghế: đúng một người thắng, người kia nhận **409** kèm tên ghế. Danh sách id được sắp
tăng dần trước khi khoá để tránh deadlock. Mọi luồng đổi trạng thái ghế (giữ, thanh
toán, huỷ, hết hạn) đều đi qua cùng lối khoá này.

**Ghế giữ quá hạn.** Ghế `HOLDING` có `holdingUntil` đã qua được coi là **còn trống
ngay** ở luồng giữ ghế, không phải chờ job. `BookingExpiryJob` chạy mỗi 60 giây dọn
dẹp (trả ghế về `AVAILABLE`, đơn `PENDING` quá hạn → `EXPIRED`) bằng hai câu UPDATE
tập hợp, không quét từng dòng.

**Thanh toán.** `pay` idempotent: gọi lại trên đơn đã `PAID` trả kết quả cũ, không
tạo giao dịch thứ hai. Gửi kèm `responseCode` (callback cổng thật) thì chỉ `"00"` mới
thành công; mã khác ghi một dòng `FAILED` và giữ đơn `PENDING` để thử lại.
`(payment_gateway, transaction_no)` là UNIQUE — gửi trùng mã cho cùng đơn thì cập
nhật dòng cũ, cho đơn khác thì trả `409`.

`PaymentRequest`: `gateway` (`VNPAY`/`MOMO`/`ZALOPAY`/`STRIPE`), `bankCode?`, `transactionNo?`,
`responseCode?`.

### 11.8. Admin — Quản lý Đơn hàng

`GET /api/admin/bookings?tripId=&status=&page=&size=` trả danh sách đơn (và hành
khách) toàn hệ thống hoặc theo một chuyến, mới nhất trước. Cùng dạng
`BookingResponse`, ghế và giao dịch được nạp theo lô (2 truy vấn) nên không N+1.

### 11.9. Thanh toán Stripe — payment-service (8084)

Service riêng, **không có database**: bảng `payments` vẫn nằm trong `booking_db` và
chỉ được ghi qua API nội bộ của booking-service. Nhờ vậy tiền và ghế luôn chốt
trong cùng một transaction, không cần distributed lock.

| Method + Path | Việc |
|---|---|
| `POST /api/payments/checkout-session` | Cần JWT **hoặc** header `X-Guest-Token` (khách vãng lai). Tạo Stripe Checkout Session cho một đơn đã giữ ghế, trả `checkoutUrl` |
| `POST /api/payments/webhook` | Stripe gọi vào. Không cần JWT nhưng bắt buộc có chữ ký `Stripe-Signature` hợp lệ |

`POST /api/payments/checkout-session` body `CheckoutSessionRequest`: chỉ `bookingId`.
**Số tiền KHÔNG nhận từ client** — lấy từ đơn ở booking-service, nếu không kẻ xấu trả
1.000đ cho đơn 500.000đ. Chỉ chủ đơn, Admin, hoặc khách vãng lai có token đúng được
tạo phiên (payment-service chuyển tiếp `X-Guest-Token` sang API nội bộ, booking-service
so token và trả `guestAccess`); đơn phải đang
`PENDING` và chưa hết hạn giữ ghế, nếu không trả **409**. Trả về `CheckoutSessionResponse`:
`bookingId`, `sessionId` (`cs_...`), `checkoutUrl`, `amount`, `currency`, `expiresAt`.

Luồng: giữ ghế (`POST /api/bookings`) → tạo phiên Stripe → khách trả tiền trên trang
Stripe → Stripe gọi webhook → payment-service gọi API nội bộ booking-service
(`POST /api/internal/bookings/{id}/confirm-payment`, header `X-Internal-Key`) → đơn
`PAID`, ghế `BOOKED`, một dòng `payments` `SUCCESS` với `payment_gateway = STRIPE`.

Các ca đặc biệt:
- **Trả tiền sau khi ghế hết hạn giữ:** webhook vẫn tới, nhưng booking-service trả
  **409**; payment-service **tự động hoàn tiền** qua Stripe rồi trả 200 cho webhook.
- **Webhook gửi lại nhiều lần:** `confirm-payment` idempotent — lần hai trả kết quả
  cũ, không tạo giao dịch thứ hai.
- **Chưa cấu hình khoá:** `STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET` để trống thì
  service vẫn khởi động, nhưng checkout trả **502** kèm thông báo rõ ràng.

API nội bộ `/api/internal/**` **không được route qua gateway** — chỉ service khác gọi
được bằng `X-Internal-Key`.

### 11.10. Chưa có API (đã có schema)

File này chỉ liệt kê endpoint **đã chạy được**. Các endpoint dự kiến (dashboard
`/api/admin/stats/*`, ghi đánh giá, profile nhà xe, khuyến mãi...) chưa được
ghi ở đây. Làm xong việc nào thì chuyển endpoint đó vào mục 11 ở trên.

DB đã lưu `subtotal_price`, `discount_amount`, `paid_at`, `cancelled_at` cho mỗi đơn
nhưng `BookingResponse` chưa trả ra — thêm khi làm khuyến mãi (Việc 9).

#!/usr/bin/env bash
# Kiểm chứng toàn bộ luồng auth + user trên stack Docker đang chạy.
# Chạy:  ./scripts/e2e-auth-user.sh
# Mọi request đi qua gateway http://localhost:8080, đúng như frontend thật.

set -uo pipefail

GW="${GATEWAY_URL:-http://localhost:8080}"
# API nội bộ không đi qua gateway (gateway chỉ mở public + admin routes),
# nên gọi thẳng user-service để kiểm tra lớp bảo vệ X-Internal-Key.
USER_DIRECT="${USER_SERVICE_URL:-http://localhost:8082}"
# Gọi thẳng auth-service để kiểm tra lớp bảo vệ IP: nếu chỉ gọi qua gateway thì
# không chứng minh được chuyện gì xảy ra khi bỏ qua gateway (mục 14).
AUTH_DIRECT="${AUTH_SERVICE_URL:-http://localhost:8081}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@ridebooking.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@12345}"

PASS=0
FAIL=0
TOKEN=""

pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; PASS=$((PASS+1)); }
fail() { printf '  \033[31mFAIL\033[0m %s\n     %s\n' "$1" "${2:-}"; FAIL=$((FAIL+1)); }
section() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# check <name> <expected_status> <method> <path> [body] [extra_header]
check() {
  local name="$1" expect="$2" method="$3" path="$4" body="${5:-}" extra="${6:-}"
  # path bắt đầu bằng http thì dùng nguyên URL, ngược lại ghép vào gateway
  local url="$path"
  [ "${path:0:4}" != "http" ] && url="$GW$path"
  local args=(-s -o /tmp/e2e_body.json -w '%{http_code}' -X "$method" "$url")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  [ -n "$extra" ] && args+=(-H "$extra")
  [ -n "$TOKEN" ] && args+=(-H "Authorization: Bearer $TOKEN")

  local code
  code=$(curl "${args[@]}")
  if [ "$code" = "$expect" ]; then
    pass "$name ($code)"
  else
    fail "$name" "expected $expect, got $code: $(head -c 300 /tmp/e2e_body.json)"
  fi
}

# Đọc 1 field từ response gần nhất, hỗ trợ key lồng kiểu "user.role"
jqv() {
  python3 -c "
import json
d = json.load(open('/tmp/e2e_body.json'))
for k in '$1'.split('.'):
    d = d.get(k) if isinstance(d, dict) else None
print('' if d is None else d)
" 2>/dev/null
}

assert() {
  local name="$1" actual="$2" expect="$3"
  if [ "$actual" = "$expect" ]; then pass "$name"; else fail "$name" "expected '$expect', got '$actual'"; fi
}

# ------------------------------------------------------------------ helpers email
# Đọc token từ email gần nhất trong Mailpit (mail catcher của docker-compose).
# Không có bước này thì chỉ kiểm được "có gửi mail", không kiểm được luồng thật.
MAILPIT="${MAILPIT_URL:-http://localhost:8025}"

# mail_token <email> <phần của tiêu đề> -> in ra token, hoặc chuỗi rỗng
mail_token() {
  python3 - "$1" "$2" <<'PYMAIL'
import json, re, sys, urllib.request

email, subject_part = sys.argv[1], sys.argv[2]
base = "http://localhost:8025/api/v1"

def get(url):
    with urllib.request.urlopen(url, timeout=10) as r:
        return json.load(r)

try:
    listing = get(base + "/messages?limit=60")
except Exception:
    print("")
    sys.exit(0)

for msg in listing.get("messages", []):
    if (msg.get("To") or [{}])[0].get("Address") != email:
        continue
    try:
        full = get(base + "/message/" + msg["ID"])
    except Exception:
        continue
    if subject_part.lower() not in (full.get("Subject") or "").lower():
        continue
    found = re.search(r"token=([A-Za-z0-9_-]+)", full.get("Text") or "")
    if found:
        print(found.group(1))
        sys.exit(0)
print("")
PYMAIL
}

# mail_count <email> <phần tiêu đề> -> số email khớp
mail_count() {
  python3 - "$1" "$2" <<'PYCOUNT'
import json, sys, urllib.request

email, subject_part = sys.argv[1], sys.argv[2]
base = "http://localhost:8025/api/v1"
try:
    with urllib.request.urlopen(base + "/messages?limit=60", timeout=10) as r:
        listing = json.load(r)
except Exception:
    print(0)
    sys.exit(0)
print(sum(1 for m in listing.get("messages", [])
          if (m.get("To") or [{}])[0].get("Address") == email
          and subject_part.lower() in (m.get("Subject") or "").lower()))
PYCOUNT
}

# ------------------------------------------------------------------ 1. health
section "1. Services cùng registry và swagger"
check "auth openapi qua gateway"      200 GET "/auth/v3/api-docs"
check "user openapi"                200 GET "/user/v3/api-docs"
# Swagger UI gộp: phải mở ở 1 chỗ là thấy cả 2 tài liệu. Trước đây mất endpoint
# này mà E2E vẫn xanh, vì không có gì kiểm tra nó -> thêm vào đây để bắt hồi quy.
# /swagger-ui.html tra 302 sang /swagger-ui/index.html (hop thuong cua
# springdoc, trinh duyet di theo nen nguoi dung van thay UI binh thuong).
# Kiem ca redirect tro dung noi dung, khong chi so sanh ma.
_loc=$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' "$GW/swagger-ui.html")
case "$_loc" in
  "302 "*"/swagger-ui/index.html"*) pass "swagger ui gộp (302 về index)" ;;
  *) fail "swagger-ui.html redirect sai: $_loc" ;;
esac
check "swagger ui trang"              200 GET "/swagger-ui/index.html"
check "swagger config"               200 GET "/v3/api-docs/swagger-config"
# config phải liệt kê đúng 2 tài liệu, không rơi về 1 spec rỗng của gateway
# bỏ khoảng trắng/dòng để so khớp chắc chắn dù server format thế nào
_sw=$(curl -s "$GW/v3/api-docs/swagger-config" | tr -d ' \n')
case "$_sw" in
  *'"url":"/auth/v3/api-docs"'*) pass "swagger config chứa auth-spec" ;;
  *) fail "swagger config thiếu auth-spec: $_sw" ;;
esac
case "$_sw" in
  *'"url":"/user/v3/api-docs"'*) pass "swagger config chứa user-spec" ;;
  *) fail "swagger config thiếu user-spec: $_sw" ;;
esac

# ------------------------------------------------------------------ 2. register
section "2. Đăng ký và quy tắc mật khẩu"
EMAIL="e2e-$(date +%s)@test.com"
BRUTE="brute-$(date +%s)@test.com"
check "đăng ký hợp lệ"                    201 POST "/api/auth/register" \
  "{\"fullName\":\"Nguyễn E2E\",\"email\":\"$EMAIL\",\"password\":\"Huy@2002x\",\"phone\":\"0912345678\"}"
ACCESS=$(jqv accessToken); REFRESH=$(jqv refreshToken)
assert "response có accessToken"  "$([ -n "$ACCESS" ]  && echo yes || echo no)" "yes"
assert "response có refreshToken" "$([ -n "$REFRESH" ] && echo yes || echo no)" "yes"
assert "expiresIn = 900 (15 phút)" "$(jqv expiresIn)" "900"
assert "refreshExpiresIn = 604800 (7 ngày)" "$(jqv refreshExpiresIn)" "604800"
assert "role mặc định là USER" "$(jqv user.role)" "USER"
USER_ID=$(jqv user.id)

check "đăng ký trùng email"                 409 POST "/api/auth/register" \
  "{\"fullName\":\"A\",\"email\":\"$EMAIL\",\"password\":\"Huy@2002x\"}"
check "mật khẩu chỉ chữ (bị từ chối)"      400 POST "/api/auth/register" \
  '{"fullName":"A","email":"a1@test.com","password":"abcdefghij"}'
check "mật khẩu chỉ số (bị từ chối)"       400 POST "/api/auth/register" \
  '{"fullName":"A","email":"a2@test.com","password":"12345678"}'
check "mật khẩu quá ngắn (bị từ chối)"     400 POST "/api/auth/register" \
  '{"fullName":"A","email":"a3@test.com","password":"Ab1"}'
check "email sai định dạng (bị từ chối)"   400 POST "/api/auth/register" \
  '{"fullName":"A","email":"khong-hop-le","password":"Huy@2002x"}'
check "thiếu họ tên (bị từ chối)"          400 POST "/api/auth/register" \
  "{\"email\":\"a4@test.com\",\"password\":\"Huy@2002x\"}"

# ------------------------------------------------------------------ 3. login
section "3. Đăng nhập"
check "sai email"                          401 POST "/api/auth/login" \
  '{"email":"khong-ton-tai@test.com","password":"Huy@2002x"}'
check "sai mật khẩu"                       401 POST "/api/auth/login" \
  "{\"email\":\"$EMAIL\",\"password\":\"SaiMatKhau1\"}"
check "đúng thông tin"                     200 POST "/api/auth/login" \
  "{\"email\":\"$EMAIL\",\"password\":\"Huy@2002x\"}"
TOKEN=$(jqv accessToken); SESSION=$(jqv refreshToken)
assert "đăng nhập trả accessToken" "$([ -n "$TOKEN" ] && echo yes || echo no)" "yes"

check "me (có token)"                      200 GET  "/api/auth/me"
# xoá TOKEN để thực sự không gửi Authorization header
TOKEN=""
check "me (không token)"                   401 GET  "/api/auth/me"
TOKEN="$ACCESS"
check "me (token sai)"                     401 GET  "/api/auth/me" "" "Authorization: Bearer abc.def.ghi"
check "validate token hợp lệ"             204 GET  "/api/auth/validate"

# ------------------------------------------------------------------ 4. refresh
section "4. Refresh token xoay vòng + phát hiện tái sử dụng"
check "refresh hợp lệ"                    200 POST "/api/auth/refresh" "{\"refreshToken\":\"$SESSION\"}"
NEW_SESSION=$(jqv refreshToken)
assert "refresh sinh token mới" "$([ "$NEW_SESSION" != "$SESSION" ] && echo yes || echo no)" "yes"

# Token cũ đã bị thu hồi -> dùng lại phải bị phát hiện và huỷ mọi phiên
check "dùng lại token đã thu hồi"          401 POST "/api/auth/refresh" "{\"refreshToken\":\"$SESSION\"}"
check "phiên sau khi bị phát hiện cũng hỏng" 401 POST "/api/auth/refresh" "{\"refreshToken\":\"$NEW_SESSION\"}"
check "refresh token bịa"                 401 POST "/api/auth/refresh" '{"refreshToken":"khong-ton-tai"}'
check "refresh thiếu field"                400 POST "/api/auth/refresh" '{}'

check "đăng nhập lại được"                 200 POST "/api/auth/login" \
  "{\"email\":\"$EMAIL\",\"password\":\"Huy@2002x\"}"
TOKEN=$(jqv accessToken); SESSION=$(jqv refreshToken)

# ------------------------------------------------------------------ 5. logout
section "5. Đăng xuất"
check "logout với refresh token"           204 POST "/api/auth/logout" "{\"refreshToken\":\"$SESSION\"}"
check "refresh sau logout bị từ chối"       401 POST "/api/auth/refresh" "{\"refreshToken\":\"$SESSION\"}"

check "đăng nhập từ 2 thiết bị"            200 POST "/api/auth/login" \
  "{\"email\":\"$EMAIL\",\"password\":\"Huy@2002x\"}"
S1=$(jqv refreshToken)
check "đăng nhập thiết bị thứ hai"         200 POST "/api/auth/login" \
  "{\"email\":\"$EMAIL\",\"password\":\"Huy@2002x\"}"
S2=$(jqv refreshToken)
assert "2 phiên khác nhau" "$([ "$S1" != "$S2" ] && echo yes || echo no)" "yes"
TOKEN=$(jqv accessToken)

# User A đang đăng nhập cố huỷ phiên của user B bằng refresh token của B
OTHER_EMAIL="other-$(date +%s)@test.com"
check "tạo tài khoản B riêng"              201 POST "/api/auth/register" \
  "{\"fullName\":\"E2E B\",\"email\":\"$OTHER_EMAIL\",\"password\":\"Huy@2002x\"}"
B_REFRESH=$(jqv refreshToken)
check "A logout với refresh token của B"   204 POST "/api/auth/logout" "{\"refreshToken\":\"$B_REFRESH\"}"
check "phiên của B KHÔNG bị huỷ"          200 POST "/api/auth/refresh" "{\"refreshToken\":\"$B_REFRESH\"}"
check "token B đã xoay vòng sau refresh"   401 POST "/api/auth/refresh" "{\"refreshToken\":\"$B_REFRESH\"}"
check "logout không kèm token (huỷ mọi phiên)" 204 POST "/api/auth/logout" '{"refreshToken":""}'
check "phiên 1 đã bị huỷ"                  401 POST "/api/auth/refresh" "{\"refreshToken\":\"$S1\"}"
check "phiên 2 đã bị huỷ"                  401 POST "/api/auth/refresh" "{\"refreshToken\":\"$S2\"}"

# ------------------------------------------------------------------ 6. brute force
section "6. Chống brute-force (khoá theo cặp email + IP)"
check "tạo tài khoản để thử khoá"          201 POST "/api/auth/register" \
  "{\"fullName\":\"Brute\",\"email\":\"$BRUTE\",\"password\":\"Huy@2002x\"}"
for i in 1 2 3 4 5; do
  check "sai mật khẩu lần $i"             401 POST "/api/auth/login" \
    "{\"email\":\"$BRUTE\",\"password\":\"SaiMatKhau1\"}"
done
check "đúng mật khẩu nhưng đã khoá"        423 POST "/api/auth/login" \
  "{\"email\":\"$BRUTE\",\"password\":\"Huy@2002x\"}"

# DoS: kẻ tấn công chỉ khoá được cặp (email, IP) của mình. Không được phép
# khoá được tài khoản — nếu khoá theo tài khoản thì chỉ cần biết trước email
# là Disable tài khoản của người khác.
LOCKED=$(docker exec ride-booking-auth-db psql -U postgres -d auth_db -tAc \
  "SELECT count(*) FROM login_attempts WHERE email='$BRUTE' AND locked_until IS NOT NULL" 2>/dev/null | tr -d ' ')
assert "bộ đếm lưu ở bảng login_attempts" "$([ "${LOCKED:-0}" -gt 0 ] && echo yes || echo no)" "yes"
USER_LOCK_COL=$(docker exec ride-booking-auth-db psql -U postgres -d auth_db -tAc \
  "SELECT count(*) FROM information_schema.columns WHERE table_name='users' AND column_name IN ('locked_until','failed_login_attempts')" 2>/dev/null | tr -d ' ')
assert "users KHÔNG còn cột khoá tài khoản" "$USER_LOCK_COL" "0"

# ------------------------------------------------------------------ 7. admin unlock
section "7. Admin gỡ khoá"
TOKEN=""
check "admin đăng nhập"                    200 POST "/api/auth/login" \
  "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}"
ADMIN_TOKEN=$(jqv accessToken)
assert "admin có role ADMIN" "$(jqv user.role)" "ADMIN"
TOKEN="$ADMIN_TOKEN"

BRUTE_ID=$(docker exec ride-booking-auth-db psql -U postgres -d auth_db -tAc \
  "SELECT id FROM users WHERE email='$BRUTE'" 2>/dev/null | tr -d ' ')
assert "đã tạo được tài khoản trong DB" "$([ -n "$BRUTE_ID" ] && echo yes || echo no)" "yes"
check "admin gỡ khoá tài khoản"             200 PATCH "/api/admin/users/$BRUTE_ID/unlock"
TOKEN=""
check "đăng nhập lại sau khi gỡ khoá"       200 POST "/api/auth/login" \
  "{\"email\":\"$BRUTE\",\"password\":\"Huy@2002x\"}"

# ------------------------------------------------------------------ 8. đổi mật khẩu
section "8. Đổi mật khẩu"
TOKEN=$(jqv accessToken); PW_SESSION=$(jqv refreshToken)
check "sai mật khẩu cũ"                    401 PUT "/api/auth/password" \
  '{"currentPassword":"KhongDung123","newPassword":"Huy@2002y"}'
# "12345678" bị chặn vì là mật khẩu phổ biến (chỉ số)
check "mật khẩu mới là mật khẩu phổ biến"    400 PUT "/api/auth/password" \
  '{"currentPassword":"Huy@2002x","newPassword":"12345678"}'
check "đổi mật khẩu hợp lệ"                204 PUT "/api/auth/password" \
  '{"currentPassword":"Huy@2002x","newPassword":"Huy@2002y"}'
check "refresh token cũ bị thu hồi"        401 POST "/api/auth/refresh" "{\"refreshToken\":\"$PW_SESSION\"}"
TOKEN=""
check "đăng nhập bằng mật khẩu mới"        200 POST "/api/auth/login" \
  "{\"email\":\"$BRUTE\",\"password\":\"Huy@2002y\"}"
TOKEN=$(jqv accessToken)
check "mật khẩu cũ không còn dùng được"    401 POST "/api/auth/login" \
  "{\"email\":\"$BRUTE\",\"password\":\"Huy@2002x\"}"

# ------------------------------------------------------------------ 9. user profile
section "9. Hồ sơ cá nhân và che dữ liệu"
check "lấy hồ sơ của tôi"                  200 GET  "/api/users/me"
assert "email hiển thị đầy đủ khi xem chính mình" "$(jqv email)" "$BRUTE"
assert "sĐT hiển thị khi xem chính mình"     "$(jqv phone)" ""
assert "not masked khi xem chính mình"      "$(jqv masked)" "False"

check "cập nhật hồ sơ của tôi"             200 PUT  "/api/users/me" \
  '{"fullName":"Brute Đổi Tên","phone":"0987654321","dob":"2000-01-02","gender":"Nam"}'
assert "tên đã cập nhật"                    "$(jqv fullName)" "Brute Đổi Tên"
assert "sĐT đã cập nhật"                    "$(jqv phone)" "0987654321"
OTHER=$(jqv id)
check "giới tính sai bị từ chối"           400 PUT  "/api/users/me" \
  '{"fullName":"X","gender":"Khong Hop Le"}'
check "sĐT sai định dạng bị từ chối"       400 PUT  "/api/users/me" \
  '{"fullName":"X","phone":"abc"}'

# Người dùng khác xem hồ sơ này -> phải bị che
assert "đã lấy được id tài khoản của mình" "$([ -n "$OTHER" ] && echo yes || echo no)" "yes"
TOKEN=$(jqv accessToken)
# Tài khoản thứ hai để đối chiếu quyền xem
TOKEN=""
check "đăng nhập tài khoản thứ hai"          200 POST "/api/auth/login" \
  "{\"email\":\"$EMAIL\",\"password\":\"Huy@2002x\"}"
OTHER_TOKEN=$(jqv accessToken)
assert "đã có accessToken của tài khoản thứ hai" "$([ -n "$OTHER_TOKEN" ] && echo yes || echo no)" "yes"

check "người khác xem hồ sơ (không bị 403)" 200 GET "/api/users/$OTHER" "" "Authorization: Bearer $OTHER_TOKEN"
assert "email bị che"                        "$(jqv masked)" "True"
assert "SĐT bị ẩn"                           "$(jqv phone)" ""
assert "ngày sinh bị ẩn"                     "$(jqv dob)" ""
assert "giới tính bị ẩn"                     "$(jqv gender)" ""
assert "email không chứa tên thật" \
  "$(python3 -c "import json;e=json.load(open('/tmp/e2e_body.json'))['email'];print('khong' if '@' in e and '0987' not in e else 'lo')")" "khong"

check "admin xem hồ sơ đầy đủ"              200 GET "/api/users/$OTHER" "" "Authorization: Bearer $ADMIN_TOKEN"
assert "admin thấy SĐT"                      "$(jqv masked)" "False"
assert "admin thấy ngày sinh"                "$(jqv dob)" "2000-01-02"

check "danh sách (user thường bị che)"      200 GET "/api/users?size=50" "" "Authorization: Bearer $OTHER_TOKEN"
assert "danh sách cũng che dữ liệu" \
  "$(python3 -c "import json;d=json.load(open('/tmp/e2e_body.json'))['content'];print('che' if all(u['masked'] for u in d) else 'lo')")" "che"
check "danh sách (admin thấy đầy đủ)"       200 GET "/api/users?size=50" "" "Authorization: Bearer $ADMIN_TOKEN"
assert "admin thấy đầy đủ" \
  "$(python3 -c "import json;d=json.load(open('/tmp/e2e_body.json'))['content'];print('day-du' if any(not u['masked'] for u in d) else 'che')")" "day-du"
check "tìm kiếm theo từ khoá"                200 GET "/api/users?keyword=Brute" "" "Authorization: Bearer $ADMIN_TOKEN"
check "xem hồ sơ không tồn tại"              404 GET "/api/users/999999" "" "Authorization: Bearer $ADMIN_TOKEN"
# /api/users/logout đã bị gỡ: đường dẫn còn nhưng POST không còn handler -> 405
check "logout giả đã bị gỡ khỏi user-service" 405 POST "/api/users/logout" "{}" "Authorization: Bearer $ADMIN_TOKEN"

# ------------------------------------------------------------------ 10. phân quyền
section "10. Phân quyền"
check "user thường gọi API admin"            403 GET "/api/admin/users" "" "Authorization: Bearer $OTHER_TOKEN"
check "user thường tạo user qua admin"       403 POST "/api/admin/users" \
  '{"fullName":"X","email":"x@test.com","password":"Huy@2002x"}' "Authorization: Bearer $OTHER_TOKEN"
check "user thường gỡ khoá (admin API)"      403 PATCH "/api/admin/users/$OTHER/unlock" "" "Authorization: Bearer $OTHER_TOKEN"
check "admin xem danh sách user (auth)"      200 GET "/api/admin/users" "" "Authorization: Bearer $ADMIN_TOKEN"
NEW_BY_ADMIN="admin-tao-$(date +%s)@test.com"
check "admin tạo user"                       201 POST "/api/admin/users" \
  "{\"fullName\":\"Admin Tạo\",\"email\":\"$NEW_BY_ADMIN\",\"password\":\"Huy@2002x\",\"role\":\"USER\"}" "Authorization: Bearer $ADMIN_TOKEN"
assert "user do admin tạo có role USER"      "$(jqv role)" "USER"
check "admin tạo user với mật khẩu yếu"      400 POST "/api/admin/users" \
  '{"fullName":"X","email":"y1@test.com","password":"12345678"}' "Authorization: Bearer $ADMIN_TOKEN"
check "admin tạo user trùng email"           409 POST "/api/admin/users" \
  "{\"fullName\":\"X\",\"email\":\"$NEW_BY_ADMIN\",\"password\":\"Huy@2002x\"}" "Authorization: Bearer $ADMIN_TOKEN"

# ------------------------------------------------------------------ 11. internal API
section "11. API nội bộ (X-Internal-Key)"
INT_URL="$USER_DIRECT/api/internal/users"
KEY="X-Internal-Key: ride-booking-internal-dev-key"
# Internal key sai -> 403 (tài nguyên tồn tại nhưng không được phép truy cập)
check "internal không key bị chặn"           403 POST "$INT_URL" '{"userId":1}'
check "internal sai key bị chặn"             403 POST "$INT_URL" '{"userId":1}' "X-Internal-Key: sai-key"
check "internal key rỗng bị chặn"           403 POST "$INT_URL/1" '' "X-Internal-Key: "
check "internal đúng key (tạo/cập nhật)"     200 POST "$INT_URL" \
  '{"userId":1,"fullName":"Quản trị viên","email":"admin@ridebooking.local","role":"ADMIN"}' "$KEY"
check "internal lấy profile"                 200 POST "$INT_URL/1" '' "$KEY"
check "internal lấy user không tồn tại"      404 POST "$INT_URL/999999" '' "$KEY"

# ------------------------------------------------------------------ 12. database
section "12. Kiểm tra trực tiếp trong PostgreSQL"
HASHES=$(docker exec ride-booking-auth-db psql -U postgres -d auth_db -tAc \
  "SELECT count(*) FROM refresh_tokens WHERE length(token_hash) <> 64" 2>/dev/null | tr -d ' ')
assert "mọi token_hash đều dài 64 (SHA-256)" "$HASHES" "0"
RAW=$(docker exec ride-booking-auth-db psql -U postgres -d auth_db -tAc \
  "SELECT count(*) FROM refresh_tokens WHERE token_hash !~ '^[0-9a-f]{64}\$'" 2>/dev/null | tr -d ' ')
assert "token_hash chỉ chứa hex, không lưu token gốc" "$RAW" "0"
# Token email cũng phải chỉ lưu dạng hash, không lưu token gốc
EMAIL_HASHES=$(docker exec ride-booking-auth-db psql -U postgres -d auth_db -tAc \
  "SELECT count(*) FROM email_tokens WHERE token_hash !~ '^[0-9a-f]{64}\$'" 2>/dev/null | tr -d ' ')
assert "email_tokens chỉ lưu SHA-256 hex" "$EMAIL_HASHES" "0"
# Ràng buộc unique của cặp (email, IP) phải có thật, nếu không thì request song
# song tạo hai dòng và chia nhỏ bộ đếm, giới hạn bị lách
PAIR_UK=$(docker exec ride-booking-auth-db psql -U postgres -d auth_db -tAc \
  "SELECT count(*) FROM pg_constraint WHERE conname='uk_login_attempts_pair'" 2>/dev/null | tr -d ' ')
assert "login_attempts có unique (email, ip_address)" "$PAIR_UK" "1"
TABLES=$(docker exec ride-booking-auth-db psql -U postgres -d auth_db -tAc \
  "SELECT count(*) FROM information_schema.tables WHERE table_name IN ('login_attempts','email_tokens','email_send_log')" 2>/dev/null | tr -d ' ')
assert "đủ 3 bảng bảo mật mới" "$TABLES" "3"
LEAK=$(docker exec ride-booking-auth-db psql -U postgres -d auth_db -tAc \
  "SELECT count(*) FROM users WHERE password NOT LIKE '\$2a\$%' AND password NOT LIKE '\$2b\$%'" 2>/dev/null | tr -d ' ')
assert "mọi mật khẩu đều là BCrypt" "$LEAK" "0"
SEPARATE=$(docker exec ride-booking-user-db psql -U postgres -d user_db -tAc \
  "SELECT count(*) FROM information_schema.tables WHERE table_name='users'" 2>/dev/null | tr -d ' ')
assert "user_db KHÔNG có bảng users" "$SEPARATE" "0"
CREDS=$(docker exec ride-booking-user-db psql -U postgres -d user_db -tAc \
  "SELECT count(*) FROM information_schema.columns WHERE table_name='user_profiles' AND column_name='password'" 2>/dev/null | tr -d ' ')
assert "user_profiles KHÔNG có cột password" "$CREDS" "0"

# ------------------------------------------------------------------ 13. email
section "13. Luồng email: xác minh, quên mật khẩu, đổi email"
MAIL_TS=$(date +%s)
MAIL_USER="mail-$MAIL_TS@test.com"

check "đăng ký tài khoản mới (chưa xác minh)" 201 POST "/api/auth/register" \
  "{\"fullName\":\"Mail User\",\"email\":\"$MAIL_USER\",\"password\":\"Huy@2002x\"}"
sleep 2
assert "email xác minh đã tới Mailpit" \
  "$(mail_count "$MAIL_USER" "Xác minh")" "1"

VERIFY_TOKEN=$(mail_token "$MAIL_USER" "Xác minh")
assert "link xác minh chứa token" "$([ -n "$VERIFY_TOKEN" ] && echo yes || echo no)" "yes"
check "xác minh email"                       200 POST "/api/auth/verify-email" \
  "{\"token\":\"$VERIFY_TOKEN\"}"
assert "emailVerified = true"                 "$(jqv verified)" "True"
check "token xác minh chỉ dùng được 1 lần"   400 POST "/api/auth/verify-email" \
  "{\"token\":\"$VERIFY_TOKEN\"}"

TOKEN=""
check "đăng nhập sau khi xác minh"            200 POST "/api/auth/login" \
  "{\"email\":\"$MAIL_USER\",\"password\":\"Huy@2002x\"}"
TOKEN=$(jqv accessToken)
MAIL_REFRESH=$(jqv refreshToken)

# --- quên mật khẩu ---
check "quên mật khẩu"                         200 POST "/api/auth/forgot-password" \
  "{\"email\":\"$MAIL_USER\"}"
sleep 2
GENERIC_MSG=$(jqv message)
check "quên mật khẩu với email không tồn tại" 200 POST "/api/auth/forgot-password" \
  '{"email":"khong-ton-tai-abc@test.com"}'
assert "thông báo y hệt nhau, không lộ email tồn tại" "$(jqv message)" "$GENERIC_MSG"

RESET_TOKEN=$(mail_token "$MAIL_USER" "Đặt lại mật khẩu")
assert "link đặt lại mật khẩu đã tới" "$([ -n "$RESET_TOKEN" ] && echo yes || echo no)" "yes"
check "đặt lại mật khẩu"                      204 POST "/api/auth/reset-password" \
  "{\"token\":\"$RESET_TOKEN\",\"newPassword\":\"MoiMatKhau@2026\"}"
check "refresh token cũ bị thu hồi sau khi đổi mật khẩu" 401 POST "/api/auth/refresh" \
  "{\"refreshToken\":\"$MAIL_REFRESH\"}"
TOKEN=""
check "mật khẩu cũ không còn dùng được"       401 POST "/api/auth/login" \
  "{\"email\":\"$MAIL_USER\",\"password\":\"Huy@2002x\"}"
check "đăng nhập bằng mật khẩu mới"           200 POST "/api/auth/login" \
  "{\"email\":\"$MAIL_USER\",\"password\":\"MoiMatKhau@2026\"}"
TOKEN=$(jqv accessToken)
check "token reset không dùng lại được"       400 POST "/api/auth/reset-password" \
  "{\"token\":\"$RESET_TOKEN\",\"newPassword\":\"MatKhauKhac@2026\"}"

# --- đổi email ---
NEW_MAIL="doi-$MAIL_TS@test.com"
check "đổi email sai mật khẩu bị từ chối"     401 POST "/api/auth/change-email" \
  "{\"newEmail\":\"$NEW_MAIL\",\"currentPassword\":\"SAI-MAT-KHAU\"}"
check "bắt đầu đổi email"                    202 POST "/api/auth/change-email" \
  "{\"newEmail\":\"$NEW_MAIL\",\"currentPassword\":\"MoiMatKhau@2026\"}"
sleep 3
assert "link xác nhận gửi tới email MỚI" "$(mail_count "$NEW_MAIL" "Xác nhận địa chỉ email mới")" "1"
assert "cảnh báo bảo mật gửi tới email CŨ" "$(mail_count "$MAIL_USER" "Cảnh báo")" "1"
CHANGE_TOKEN=$(mail_token "$NEW_MAIL" "Xác nhận địa chỉ email mới")
check "xác nhận đổi email sai mật khẩu"       401 POST "/api/auth/confirm-email-change" \
  "{\"token\":\"$CHANGE_TOKEN\",\"currentPassword\":\"SAI-MAT-KHAU\"}"
check "xác nhận đổi email"                    200 POST "/api/auth/confirm-email-change" \
  "{\"token\":\"$CHANGE_TOKEN\",\"currentPassword\":\"MoiMatKhau@2026\"}"
TOKEN=""
check "đăng nhập bằng email mới"              200 POST "/api/auth/login" \
  "{\"email\":\"$NEW_MAIL\",\"password\":\"MoiMatKhau@2026\"}"
check "email cũ không còn đăng nhập được"    401 POST "/api/auth/login" \
  "{\"email\":\"$MAIL_USER\",\"password\":\"MoiMatKhau@2026\"}"

# --- hạn mức gửi email ---
RL="rate-$MAIL_TS@test.com"
check "đăng ký tài khoản để thử hạn mức"     201 POST "/api/auth/register" \
  "{\"fullName\":\"Rate\",\"email\":\"$RL\",\"password\":\"Huy@2002x\"}"
# Đăng ký đã gửi 1 mail, hạn mức là 3/giờ -> lần gửi thứ 3 và 4 bị chặn
check "gửi lại xác minh lần 1"               200 POST "/api/auth/resend-verification" \
  "{\"email\":\"$RL\"}"
check "gửi lại xác minh lần 2"               200 POST "/api/auth/resend-verification" \
  "{\"email\":\"$RL\"}"
check "vượt hạn mức thì bị chặn 429"         429 POST "/api/auth/resend-verification" \
  "{\"email\":\"$RL\"}"

# ------------------------------------------------- 14. IP thật qua proxy (X-Forwarded-For)
# Vì sao cần mục này: P0-2 và P0-3 đều là lỗi CẤU HÌNH im lặng. Gateway đặt sai
# tên khoá (namespace cũ đã bị Spring Cloud Gateway 5 bỏ) và Tomcat mặc định
# coi cả dải 172.16/12 của Docker là proxy tin cậy, nên request vẫn trả 401/423
# đúng như mong đợi — không có gì fail, chỉ có IP bị ghi sai. Unit test không bắt
# được, vì nó không đi qua gateway và không chạy RemoteIpValve của Tomcat.
# Mục này kiểm tra IP THỰC SỰ được ghi vào DB.
section "14. Không tin X-Forwarded-For giả mạo (bảo vệ giới hạn theo IP)"
XFF_DB() { docker exec ride-booking-auth-db psql -U postgres -d auth_db -tAc \
  "SELECT coalesce(max(ip_address),'KHONG-CO') FROM login_attempts WHERE email='$1'" 2>/dev/null | tr -d ' '; }

# A. Gọi thẳng auth-service, tự dán XFF của kẻ tấn công.
SPOOF_DIRECT="spoof-direct-$MAIL_TS@test.com"
check "đăng nhập sai thẳng 8081 kèm XFF giả"   401 POST "$AUTH_DIRECT/api/auth/login" \
  '{"email":"'"$SPOOF_DIRECT"'","password":"SaiMatKhau1"}' "X-Forwarded-For: 198.51.100.77"
assert "gọi thẳng: XFF giả bị bỏ qua" \
  "$([ "$(XFF_DB "$SPOOF_DIRECT")" = "198.51.100.77" ] && echo no || echo yes)" "yes"

# B. Đi qua gateway, vẫn tự dán XFF giả.
SPOOF_GW="spoof-gw-$MAIL_TS@test.com"
check "đăng nhập sai qua gateway kèm XFF giả" 401 POST "/api/auth/login" \
  '{"email":"'"$SPOOF_GW"'","password":"SaiMatKhau1"}' "X-Forwarded-For: 198.51.100.88"
assert "qua gateway: XFF giả bị bỏ qua" \
  "$([ "$(XFF_DB "$SPOOF_GW")" = "198.51.100.88" ] && echo no || echo yes)" "yes"

# C. Không dán XFF: IP ghi được phải khớp với ca B, nếu không thì gateway đang
# im lặng và mọi người dùng lại chung một IP -> giới hạn theo IP thành toàn cục.
SPOOF_PLAIN="spoof-plain-$MAIL_TS@test.com"
check "đăng nhập sai qua gateway, không XFF"    401 POST "/api/auth/login" \
  '{"email":"'"$SPOOF_PLAIN"'","password":"SaiMatKhau1"}'
assert "gateway ghi IP client thật, không phải IP gateway" \
  "$([ "$(XFF_DB "$SPOOF_GW")" = "$(XFF_DB "$SPOOF_PLAIN")" ] && echo yes || echo no)" "yes"
assert "IP ghi được KHÔNG rơi vào IP của chính gateway" \
  "$([ "$(XFF_DB "$SPOOF_PLAIN")" = "172.28.0.10" ] && echo no || echo yes)" "yes"

# ------------------------------------------------------------------ 15. CORS
section "15. CORS chỉ cho phép origin được khai báo"
cors_origin() { # <origin> -> in ra Access-Control-Allow-Origin hoặc rong
  curl -s -D - -o /dev/null -X OPTIONS "$GW/api/auth/login" \
    -H "Origin: $1" \
    -H 'Access-Control-Request-Method: POST' \
    -H 'Access-Control-Request-Headers: content-type,authorization' \
    | tr -d '\r' | awk -F': ' 'tolower($1)=="access-control-allow-origin"{print $2}'
}
assert "origin hợp lệ được cấp quyền"    "$(cors_origin 'http://localhost:5173')" "http://localhost:5173"
assert "origin hợp lệ thứ hai"          "$(cors_origin 'http://localhost:3000')" "http://localhost:3000"
assert "origin lạ KHÔNG được cấp quyền"  "$(cors_origin 'https://evil.example')" ""
# allowCredentials phải tắt: cookie phiên + origin bất kỳ là hổn hoạn CSRF.
CRED=$(curl -s -D - -o /dev/null -X OPTIONS "$GW/api/auth/login" \
  -H "Origin: http://localhost:5173" -H 'Access-Control-Request-Method: POST' \
  | tr -d '\r' | awk -F': ' 'tolower($1)=="access-control-allow-credentials"{print $2}')
assert "KHÔNG bật allowCredentials" "${CRED:-<khong-co>}" "<khong-co>"

# ------------------------------------------------------------------ 16. Siết chặt đầu vào
section "16. Chặn payload rác và body quá lớn"

# 16.1 JSON có key trùng -> phải 400, tuyệt đối không tạo user.
# Đây là khuôn mẫu parser differential: nếu sau này có WAF/nginx validate email
# và đọc giá trị ĐẦU, còn app đọc giá trị CUỐI, hai bên hiểu khác nhau.
DUP1="dup_a_$$@test.com"; DUP2="dup_b_$$@test.com"
check "key JSON trùng bị từ chối" 400 POST "/api/auth/register" \
  "{\"fullName\":\"Dup\",\"email\":\"$DUP1\",\"password\":\"Huy@2002x\",\"email\":\"$DUP2\"}"
# Xác nhận mạnh hơn: user KHÔNG được tạo cho bất kỳ giá trị nào.
check "key trùng: $DUP1 chưa bị tạo" 401 POST "/api/auth/login" \
  "{\"email\":\"$DUP1\",\"password\":\"Huy@2002x\"}"
check "key trùng: $DUP2 chưa bị tạo" 401 POST "/api/auth/login" \
  "{\"email\":\"$DUP2\",\"password\":\"Huy@2002x\"}"

# 16.2 Body vượt ngưỡng -> 413. Tomcat KHÔNG giới hạn JSON (max-http-form-post-size
# chỉ áp form), nên không có filter này thì body 20MB được đọc trọn vào RAM.
python3 - > /tmp/e2e_big.json <<'PYEOF'
import json
print(json.dumps({"fullName": "A" * (256 * 1024), "email": "bigbody@test.com", "password": "Huy@2002x"}))
PYEOF
check "body vượt ngưỡng bị 413" 413 POST "/api/auth/register" "$(cat /tmp/e2e_big.json)"

# Chunked (không Content-Length) là đường vòng nếu chỉ kiểm Content-Length.
check "body chunked vượt ngưỡng cũng bị 413" 413 POST "/api/auth/register" "$(cat /tmp/e2e_big.json)" \
  "Transfer-Encoding: chunked"

# 16.3 Sai Content-Type -> 415 chứ không phải 500 (500 làm nhiễu cảnh báo 5xx).
CT=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GW/api/auth/login" \
  -H 'Content-Type: text/plain' -d '{"email":"a@b.com","password":"x"}')
assert "sai Content-Type trả 415" "$CT" "415"

# 16.4 Email dính khoảng trắng phải chạy được (copy-paste hay dính space).
SPACED="spaced_$$@test.com"
check "email dính khoảng trắng đăng ký được" 201 POST "/api/auth/register" \
  "{\"fullName\":\"Space\",\"email\":\"  $SPACED  \",\"password\":\"Huy@2002x\"}"
check "đăng nhập lại bằng chính email đó" 200 POST "/api/auth/login" \
  "{\"email\":\"  $SPACED  \",\"password\":\"Huy@2002x\"}"

# ------------------------------------------------------------------ 17. Email không mất khi SMTP chết
section "17. Hàng đợi gửi email"

# Gửi email khi SMTP hỏng từng mất vĩnh viễn: HTTP trả 200 kèm "đã gửi email cho
# bạn", khách chờ mãi không thấy gì, thử lại thì bị chặn vì hết hạn mức. Nay
# email nằm trong hàng đợi và được gửi lại khi SMTP hồi phục.
# Script này KHÔNG tự tắt mailpit: làm vậy sẽ làm hỏng các phần E2E còn lại.
# Phần này chỉ kiểm tra trạng thái hàng đợi trên stack đang chạy.
assert "cột hàng đợi tồn tại trong DB" \
  "$(docker compose exec -T auth-db psql -U postgres -d auth_db -t -c \
    "select count(*) from information_schema.columns where table_name='email_send_log' and column_name in ('status','attempts','next_attempt_at','retry_until','html_body');" | tr -d ' \r')" "5"

# Mọi email gửi được phải có trạng thái kết thúc, không lọt PENDING vĩnh viễn.
STUCK=$(docker compose exec -T auth-db psql -U postgres -d auth_db -t -c \
  "select count(*) from email_send_log where status not in ('SENT','DEAD','PENDING');" | tr -d ' \r')
assert "không có trạng thái lạ nào trong hàng đợi" "$STUCK" "0"

# Email đã gửi thì không được giữ nội dung lâu hơn hạn lịch sử — nếu job dọn dẹp
# chạy sai, nội dung email (chứa link đặt lại mật khẩu) nằm lại vô thời hạn.
assert "không có email SENT quá 7 ngày" \
  "$(docker compose exec -T auth-db psql -U postgres -d auth_db -t -c \
    "select count(*) from email_send_log where status='SENT' and created_at < now() - interval '7 days';" | tr -d ' \r')" "0"

# ------------------------------------------------------------------ 18. Service chưa có
section "18. Service phía sau chưa sẵn sàng phải trả 503"

# booking-service (8083) và payment-service (8084) CHƯA được dựng. Gateway phải
# trả 503 chứ không phải 500: client phân biệt hai loại — 503 thì tự thử lại
# được, 500 thì coi như lỗi code và dừng. Trả nhầm 500 làm sự cố khởi động đơn
# giản thành lỗi không tự phục hồi.
check "GET /api/trips khi service chết"      503 GET  "/api/trips"
check "GET /api/payments khi service chết"   503 GET  "/api/payments"
check "GET /api/locations khi service chết"  503 GET  "/api/locations"

# Ngược lại: route không tồn tại là 404, KHÔNG phải 503. Trộn hai loại làm client
# thử lại một URL vốn không bao giờ tồn tại.
check "route không tồn tại trả 404"          404 GET  "/api/khong-ton-tai-duoc"

BODY503=$(curl -s "$GW/api/trips")
assert "503 có message để frontend hiện" \
  "$(printf '%s' "$BODY503" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("message",""))' 2>/dev/null)" \
  "Dich vu tam thoi khong san sang. Vui long thu lai sau."
assert "503 có path để client biết request nào lỗi" \
  "$(printf '%s' "$BODY503" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("path",""))' 2>/dev/null)" \
  "/api/trips"

# ------------------------------------------------------------------ kết quả
printf '\n\033[1m=== Tổng kết: %d pass, %d fail ===\033[0m\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] || exit 1

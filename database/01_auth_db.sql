-- =============================================================================
--  auth_db — schema chuẩn cho auth-service
-- =============================================================================
--  Script IDEMPOTENT: chạy lại nhiều lần vẫn an toàn.
--  Dùng khi deploy thật (nên tắt spring.jpa.hibernate.ddl-auto=update),
--  hoặc khi muốn dựng schema bằng tay thay vì để Hibernate tự tạo.
--
--  psql -U postgres -d auth_db -f database/01_auth_db.sql
-- =============================================================================

-- --------------------------------------------------------------------- users
CREATE TABLE IF NOT EXISTS users (
    id                    SERIAL       PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    password              VARCHAR(255) NOT NULL,
    full_name             VARCHAR(255),
    role                  VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at            TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at         TIMESTAMP(6),

    -- Trạng thái xác minh email
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at     TIMESTAMP(6),

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role  CHECK (role IN ('GUEST', 'USER', 'ADMIN'))
);

-- Nâng cấp từ schema cũ (đã bỏ cột khoá tài khoản, thêm cột xác minh email).
-- Cột cũ để nguyên cho tới khi đã dọn xong dữ liệu, xem ghi chú cuối file.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP(6);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at     TIMESTAMP(6);

-- email luôn lưu dạng chữ thường để tránh trùng do khác hoa/thường
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower ON users (LOWER(email));

-- ----------------------------------------------------------- login_attempts
-- Chống brute-force theo CẶP (email, IP), không khoá cả tài khoản.
-- Nhờ vậy không ai khoá nhầm tài khoản của người khác chỉ vì biết trước email.
CREATE TABLE IF NOT EXISTS login_attempts (
    id              BIGSERIAL    PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    ip_address      VARCHAR(45)  NOT NULL,
    failed_count    INTEGER      NOT NULL DEFAULT 0,
    first_failed_at TIMESTAMP(6) NOT NULL,
    last_failed_at  TIMESTAMP(6) NOT NULL,
    locked_until    TIMESTAMP(6),

    -- Một dòng cho mỗi cặp. Thiếu ràng buộc này thì hai request song song có
    -- thể cùng tạo dòng và chia nhỏ bộ đếm, khiến giới hạn bị lách.
    CONSTRAINT uk_login_attempts_pair UNIQUE (email, ip_address),
    CONSTRAINT ck_login_attempts_count CHECK (failed_count >= 0)
);

CREATE INDEX IF NOT EXISTS ix_login_attempts_email ON login_attempts (email);
CREATE INDEX IF NOT EXISTS ix_login_attempts_ip    ON login_attempts (ip_address);

-- Truy vấn "IP này sai bao nhiêu lần trong cửa sổ thời gian" chạy mỗi lần
-- đăng nhập, nên index theo (ip_address, last_failed_at) là bắt buộc.
CREATE INDEX IF NOT EXISTS ix_login_attempts_ip_recent
    ON login_attempts (ip_address, last_failed_at DESC);

-- ------------------------------------------------------------- email_tokens
-- Token xác minh / quên mật khẩu / đổi email. Chỉ lưu SHA-256 hex, không lưu
-- token gốc — token gốc chỉ tồn tại trong nội dung email gửi đi.
CREATE TABLE IF NOT EXISTS email_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    INTEGER      NOT NULL,
    purpose    VARCHAR(30)  NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    new_email  VARCHAR(255),
    expires_at TIMESTAMP(6) NOT NULL,
    used_at    TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_email_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_email_tokens_purpose CHECK (
        purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'EMAIL_CHANGE')),
    CONSTRAINT ck_email_tokens_hash_hex CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

-- Tìm token khi người dùng mở link -> index theo hash
CREATE INDEX IF NOT EXISTS ix_email_tokens_hash ON email_tokens (token_hash);

-- Vô hiệu hoá token cũ khi cấp token mới cho cùng mục đích
CREATE INDEX IF NOT EXISTS ix_email_tokens_user_purpose
    ON email_tokens (user_id, purpose);

-- Job dọn token hết hạn không phải seq scan
CREATE INDEX IF NOT EXISTS ix_email_tokens_expires ON email_tokens (expires_at);

-- ---------------------------------------------------------- email_send_log
-- Lịch sử gửi để chống spam: giới hạn theo người nhận và theo IP.
-- Ghi cả dòng thất bại — nếu không, kẻ spam chỉ cần làm hỏng SMTP là né được.
CREATE TABLE IF NOT EXISTS email_send_log (
    id              BIGSERIAL    PRIMARY KEY,
    recipient       VARCHAR(255) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    kind            VARCHAR(40)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    failure_reason  VARCHAR(500),
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_email_send_log_status CHECK (status IN ('SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS ix_email_send_log_to ON email_send_log (recipient, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_email_send_log_ip ON email_send_log (ip_address, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_email_send_log_created ON email_send_log (created_at);


-- ----------------------------------------------------------- refresh_tokens
-- Chỉ lưu SHA-256 hex 64 ký tự, KHÔNG BAO GIỜ lưu token gốc.
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    INTEGER      NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_agent VARCHAR(255),
    ip_address VARCHAR(45),

    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    -- Token thuộc user nào: xoá user thì token cũng phải mất, không để lại
    -- dữ liệu rác có thể dùng để đăng nhập.
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,

    -- Bảo đảm đúng định dạng SHA-256 hex ở tầng database, không chỉ ở tầng Java
    CONSTRAINT ck_refresh_tokens_hash_hex CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

-- Liệt kê phiên đang hoạt động của một user
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_user_active
    ON refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;

-- Dọn token hết hạn (job lúc 03:00) không phải quét toàn bảng
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_expires
    ON refresh_tokens (expires_at);

-- Bảo đảm không trùng (user_id, expires_at) là không cần thiết, nhưng giữ index
-- trên để truy vấn "phiên nào còn hiệu lực" không phải seq scan.

-- ============================================================ dọn schema cũ
-- users.failed_login_attempts và users.locked_until là schema cũ (khoá theo
-- tài khoản). Giữ chúng lại là chỗ dễ gây nhầm lẫn: một người đọc schema sẽ
-- tưởng hệ thống vẫn khoá cả tài khoản. Dọn ở đây.
--
-- Trước khi xoá, chuyển trạng thái khoá còn dư sang bảng login_attempts —
-- nếu xoá thẳng thì một tài khoản đang bị khoá sẽ "mở khoá" ngoài ý muốn.
-- IP ghi 'unknown' vì schema cũ không lưu IP (bản chất đã ghi nhận chỉ đúng
-- tài khoản nào đang bị khoá, chứ không biết từ IP nào).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'failed_login_attempts'
    ) THEN
        INSERT INTO login_attempts
            (email, ip_address, failed_count, first_failed_at, last_failed_at, locked_until)
        SELECT
            email,
            'unknown',
            GREATEST(failed_login_attempts, 0),
            COALESCE(last_login_at, CURRENT_TIMESTAMP),
            CURRENT_TIMESTAMP,
            locked_until
        FROM users
        WHERE failed_login_attempts > 0 OR locked_until IS NOT NULL
        ON CONFLICT (email, ip_address) DO UPDATE
            SET failed_count = GREATEST(login_attempts.failed_count, EXCLUDED.failed_count),
                locked_until  = GREATEST(login_attempts.locked_until, EXCLUDED.locked_until);

        ALTER TABLE users DROP COLUMN failed_login_attempts;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'locked_until'
    ) THEN
        ALTER TABLE users DROP COLUMN locked_until;
    END IF;
END
$$;

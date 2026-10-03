-- =============================================================================
--  user_db — schema chuẩn cho user-service
-- =============================================================================
--  Script IDEMPOTENT: chạy lại nhiều lần vẫn an toàn.
--
--  psql -U postgres -d user_db -f database/02_user_db.sql
-- =============================================================================

-- ---------------------------------------------------------------- user_profiles
-- user_id KHÔNG có foreign key tới auth_db.users.id: hai database tách biệt,
-- không join được. Tính toàn vẹn tham chiếu do auth-service chịu trách nhiệm
-- (nó gọi /api/internal/users ngay sau khi tạo user).
--
-- Không có cột password: user_db không bao giờ chạm vào credential.
CREATE TABLE IF NOT EXISTS user_profiles (
    user_id    INTEGER      PRIMARY KEY,
    email      VARCHAR(255),
    full_name  VARCHAR(255),
    phone      VARCHAR(20),
    dob        DATE,
    gender     VARCHAR(20),
    role       VARCHAR(20),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6)
);

CREATE INDEX IF NOT EXISTS ix_user_profiles_email ON user_profiles (email);
CREATE INDEX IF NOT EXISTS ix_user_profiles_name  ON user_profiles (full_name);

-- SĐT chỉ giữ tối đa 20 ký tự; chặn sẵn giá trị vô nghĩa ở tầng DB
ALTER TABLE user_profiles
    DROP CONSTRAINT IF EXISTS ck_user_profiles_phone;
ALTER TABLE user_profiles
    ADD  CONSTRAINT ck_user_profiles_phone
    CHECK (phone IS NULL OR phone ~ '^\+?[0-9]{9,15}$');

ALTER TABLE user_profiles
    DROP CONSTRAINT IF EXISTS ck_user_profiles_gender;
ALTER TABLE user_profiles
    ADD  CONSTRAINT ck_user_profiles_gender
    CHECK (gender IS NULL OR gender IN ('Nam', 'Nữ', 'Khác'));

-- =============================================================================
--  Kiểm tra sau khi chạy:
--    SELECT count(*) FROM users;
--    SELECT count(*) FROM refresh_tokens;
--    \d refresh_tokens
-- =============================================================================

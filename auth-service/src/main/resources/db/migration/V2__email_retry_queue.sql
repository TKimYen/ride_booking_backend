-- Hàng đợi gửi lại email: entity EmailSendLog đã dùng các cột và trạng thái này, nhưng
-- V1 (chép từ script SQL cũ) chưa có. Trước đây ddl-auto=update tự thêm cột nên không ai
-- thấy; riêng CHECK cũ thì update không sửa, DB tạo từ script sẽ từ chối dòng PENDING.
--
-- IF NOT EXISTS vì database cũ do ddl-auto=update tạo đã có sẵn các cột này.

ALTER TABLE email_send_log ADD COLUMN IF NOT EXISTS html_body       TEXT;
ALTER TABLE email_send_log ADD COLUMN IF NOT EXISTS text_body       TEXT;
ALTER TABLE email_send_log ADD COLUMN IF NOT EXISTS attempts        INTEGER;
ALTER TABLE email_send_log ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP(6);
ALTER TABLE email_send_log ADD COLUMN IF NOT EXISTS retry_until     TIMESTAMP(6);

UPDATE email_send_log SET attempts = 0 WHERE attempts IS NULL;
ALTER TABLE email_send_log ALTER COLUMN attempts SET DEFAULT 0;
ALTER TABLE email_send_log ALTER COLUMN attempts SET NOT NULL;

-- Giữ FAILED cho các dòng lịch sử ghi trước khi có hàng đợi.
ALTER TABLE email_send_log DROP CONSTRAINT IF EXISTS ck_email_send_log_status;
ALTER TABLE email_send_log ADD  CONSTRAINT ck_email_send_log_status
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD'));

-- Job gửi lại tìm dòng PENDING đã tới giờ thử.
CREATE INDEX IF NOT EXISTS ix_email_send_log_retry ON email_send_log (status, next_attempt_at);

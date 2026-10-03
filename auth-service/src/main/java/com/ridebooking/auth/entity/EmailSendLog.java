package com.ridebooking.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Lịch sử gửi email — dùng để chống spam, để tra cứu khi có khiếu nại, và (quan
 * trọng) làm <b>hàng đợi gửi lại</b> khi SMTP hỏng.
 *
 * <p>Việc giới hạn số lần gửi dựa trên bảng này thay vì bộ đếm trong memory để
 * không mất khi restart, và để nhiều bản sao của service vẫn dùng chung một hạn
 * mức.
 *
 * <p><b>Vì sao lại lưu nội dung email.</b> Trước đây bảng này chỉ ghi log và cố
 * tình không lưu nội dung, nên khi SMTP chết là email mất vĩnh viễn: HTTP vẫn
 * trả 200 kèm "chúng tôi đã gửi email cho bạn", khách chờ mãi không thấy gì, thử
 * lại thì bị chặn vì hết hạn mức. Không có gì tự gửi lại khi SMTP hồi phục. Nay
 * {@code status = PENDING} nghĩa là "chưa gửi được, còn trong hàng đợi", và
 * {@code html_body}/{@code text_body} là nội dung để gửi lại.
 *
 * <p><b>Vì sao không bỏ dòng PENDING khi dọn dẹp.</b> Job dọn dẹp chỉ xoá dòng
 * đã kết thúc (SENT/DEAD). Xoá cả PENDING nghĩa là xoá email chưa gửi được vì
 * tuổi của nó lớn — đúng lúc cần nhất thì mất.
 */
@Entity
@Table(name = "email_send_log", indexes = {
        @Index(name = "ix_email_send_log_to", columnList = "recipient,created_at"),
        @Index(name = "ix_email_send_log_ip", columnList = "ip_address,created_at"),
        @Index(name = "ix_email_send_log_retry", columnList = "status,next_attempt_at")
})
public class EmailSendLog {

    /** Chờ gửi lần đầu hoặc chờ thử lại. */
    public static final String STATUS_PENDING = "PENDING";
    /** Đã gửi thành công. */
    public static final String STATUS_SENT = "SENT";
    /** Hết số lần thử, hoặc token đã hết hạn — không thử nữa. */
    public static final String STATUS_DEAD = "DEAD";


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Nội dung email. NULL với dòng thuần lịch sử (dữ liệu cũ trước khi có hàng đợi).
    @Column(name = "html_body", columnDefinition = "TEXT")
    private String htmlBody;

    @Column(name = "text_body", columnDefinition = "TEXT")
    private String textBody;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    /** Khi nào worker thử gửi lại lần nữa. NULL = chưa cần thử lại. */
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    /**
     * Hạn chót để gửi lại. Đặt bằng thời hạn của token mà email mang theo: gửi
     * lại link đã hết hạn chỉ khiến khách bấm vào thấy "mã không hợp lệ" và tưởng
     * hệ thống hỏng.
     */
    @Column(name = "retry_until")
    private LocalDateTime retryUntil;

    public EmailSendLog() {
    }

    public EmailSendLog(String recipient, String subject, String kind, String status,
                        String failureReason, String ipAddress, LocalDateTime createdAt) {
        this.recipient = recipient;
        this.subject = subject;
        this.kind = kind;
        this.status = status;
        this.failureReason = failureReason;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
        this.attempts = 0;
    }

    /** Tạo dòng chờ gửi, đã kèm nội dung để gửi lại được. */
    public static EmailSendLog pending(String recipient, String subject, String kind,
                                       String ipAddress, String htmlBody, String textBody,
                                       LocalDateTime retryUntil) {
        EmailSendLog row = new EmailSendLog(recipient, subject, kind, STATUS_PENDING,
                null, ipAddress, LocalDateTime.now());
        row.htmlBody = htmlBody;
        row.textBody = textBody;
        row.retryUntil = retryUntil;
        // Số 0 nhỏ hơc mọi mốc thời gian hiện tại nên dòng này được coi là "đến hạn"
        // ngay, kể cả khi worker lấy trong cùng lần quét.
        row.nextAttemptAt = LocalDateTime.now();
        return row;
    }

    /**
     * Hai dòng là cùng một dòng khi có cùng {@code id}. Quan trọng cho worker: sau
     * khi {@code markSent} sửa trạng thái trong bộ nhớ đệm, {@code save} phải ghi
     * đè đúng dòng đó, không phải tạo dòng mới — nếu không, sẽ có một dòng PENDING
     * mồ côi và một dòng SENT trùng nội dung.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EmailSendLog that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public LocalDateTime getRetryUntil() {
        return retryUntil;
    }

    public void setRetryUntil(LocalDateTime retryUntil) {
        this.retryUntil = retryUntil;
    }
}

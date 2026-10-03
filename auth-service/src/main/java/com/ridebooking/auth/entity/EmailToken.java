package com.ridebooking.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Token một lần dùng cho các luồng qua email: xác minh địa chỉ, đặt lại mật khẩu,
 * xác nhận đổi email.
 *
 * <p>Chỉ lưu SHA-256 của token, giống refresh token: đọc lừa được database cũng
 * không dựng lại được link. Mỗi token chỉ dùng một lần, hết hạn và bị thu hồi
 * khi tạo token mới cho cùng mục đích.
 *
 * <p>Vì sao dùng chung một bảng cho cả 3 loại? Chúng có cùng đặc tính bảo mật
 * (token ngẫu nhiên, hash, một lần, hạn theo thời gian) nên dùng chung giúp
 * chỉ cần một chính sách hạn token và một job dọn dữ liệu.
 */
@Entity
@Table(name = "email_tokens", indexes = {
        @Index(name = "ix_email_tokens_hash", columnList = "token_hash"),
        @Index(name = "ix_email_tokens_user_purpose", columnList = "user_id,purpose")
})
public class EmailToken {

    public enum Purpose {
        /** Xác nhận địa chỉ email lúc đăng ký hoặc sau khi đổi email. */
        EMAIL_VERIFICATION,
        /** Đặt lại mật khẩu qua link quên mật khẩu. */
        PASSWORD_RESET,
        /** Xác nhận thay đổi địa chỉ email. */
        EMAIL_CHANGE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private Purpose purpose;

    /** SHA-256 hex 64 ký tự. Không lưu bản gốc. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * Với {@link Purpose#EMAIL_CHANGE}: địa chỉ email mới đang chờ xác nhận.
     * Với các mục đích khác thì null.
     */
    @Column(name = "new_email", length = 255)
    private String newEmail;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public EmailToken() {
    }

    public EmailToken(Integer userId, Purpose purpose, String tokenHash, String newEmail,
                      Duration ttl, LocalDateTime now) {
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.newEmail = newEmail;
        this.createdAt = now;
        this.expiresAt = now.plus(ttl);
    }

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void markUsed(LocalDateTime now) {
        this.usedAt = now;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public void setPurpose(Purpose purpose) {
        this.purpose = purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public void setNewEmail(String newEmail) {
        this.newEmail = newEmail;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

package com.ridebooking.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * Bộ đếm đăng nhập sai theo CẶP (email, ip).
 *
 * <p>Vì sao không đếm trên bảng {@code users}? Nếu khoá cả tài khoản sau 5 lần sai
 * thì bất kỳ ai biết email của nạn nhân cũng khoá được nạn nhân (từ chối dịch vụ),
 * hoàn toàn không cần biết mật khẩu. Đếm theo cặp (email, ip) thì kẻ tấn công
 * phải gánh 5 lần sai riêng cho từng IP, còn nạn nhân đăng nhập từ IP sạch của
 * mình vẫn bình thường.
 *
 * <p>Ngoài ra còn có giới hạn tổng theo IP (xem {@code IpRateLimiter}) để chặn
 * kiểu tấn công rải mật khẩu trên nhiều tài khoản.
 *
 * <p>Dòng này bị xoá khi đăng nhập đúng, hoặc khi đổi mật khẩu / xác minh email
 * — tức là mọi hành động chứng minh được người dùng là chính chủ sẽ tự mở khoá.
 */
@Entity
@Table(name = "login_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uk_login_attempts_pair",
                columnNames = {"email", "ip_address"}),
        indexes = {
        @Index(name = "ix_login_attempts_email", columnList = "email"),
        @Index(name = "ix_login_attempts_ip", columnList = "ip_address"),
        // Truy vấn giới hạn theo IP chạy mỗi lần đăng nhập
        @Index(name = "ix_login_attempts_ip_recent", columnList = "ip_address, last_failed_at")
})
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email đang thử đăng nhập (đã chuẩn hoá về chữ thường).
     *
     * <p>Cặp (email, ip_address) là UNIQUE ở tầng DB — xem {@code @Table} bên trên.
     * Không có ràng buộc này thì hai request song song tạo hai dòng cho cùng một
     * cặp, bộ đếm bị chia nhỏ và giới hạn bị lách.
     */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    /** Số lần sai liên tiếp trên cặp này. */
    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    /** Lần sai đầu tiên của chuỗi hiện tại, dùng cho cửa sổ trượt. */
    @Column(name = "first_failed_at", nullable = false)
    private LocalDateTime firstFailedAt;

    @Column(name = "last_failed_at", nullable = false)
    private LocalDateTime lastFailedAt;

    /** Không null = cặp (email, ip) này đang bị khoá tới thời điểm này. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public LocalDateTime getFirstFailedAt() {
        return firstFailedAt;
    }

    public void setFirstFailedAt(LocalDateTime firstFailedAt) {
        this.firstFailedAt = firstFailedAt;
    }

    public LocalDateTime getLastFailedAt() {
        return lastFailedAt;
    }

    public void setLastFailedAt(LocalDateTime lastFailedAt) {
        this.lastFailedAt = lastFailedAt;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }
}

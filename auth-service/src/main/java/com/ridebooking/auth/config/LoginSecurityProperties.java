package com.ridebooking.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chính sách chống brute-force.
 *
 * <p>Không khoá cả tài khoản, mà khoá theo CẶP (email, IP): sau
 * {@code maxFailedAttempts} lần sai liên tiếp trên cùng một cặp đó mới khoá,
 * nên kẻ khác không thể khoá nhầm tài khoản của người dùng chỉ bằng cách biết
 * email. Kèm thêm {@code maxFailedAttemptsPerIp} để chặn tấn công rải mật khẩu
 * trên nhiều tài khoản từ một IP.
 *
 * <p>Đổi mật khẩu, xác minh email hoặc đổi email sẽ xoá toàn bộ bộ đếm của
 * tài khoản — tức là mọi cách chứng minh mình là chủ tài khoản đều tự mở khoá.
 */
@ConfigurationProperties(prefix = "app.login-security")
public class LoginSecurityProperties {

    /** Số lần sai liên tiếp trên cặp (email, IP) trước khi khoá. */
    private int maxFailedAttempts = 5;

    /** Thời gian khoá (phút). */
    private long lockMinutes = 15;

    /**
     * Tổng số lần sai cho phép từ một IP trong {@link #ipWindowMinutes},
     * bất kể thử tài khoản nào. Chặn password spray và dò email tồn tại.
     */
    private int maxFailedAttemptsPerIp = 20;

    /** Độ rộng cửa sổ thời gian cho giới hạn theo IP (phút). */
    private long ipWindowMinutes = 15;

    /** Cho phép admin gỡ khoá thủ công qua endpoint quản trị. */
    private boolean adminUnlockEnabled = true;

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public long getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(long lockMinutes) {
        this.lockMinutes = lockMinutes;
    }

    public int getMaxFailedAttemptsPerIp() {
        return maxFailedAttemptsPerIp;
    }

    public void setMaxFailedAttemptsPerIp(int maxFailedAttemptsPerIp) {
        this.maxFailedAttemptsPerIp = maxFailedAttemptsPerIp;
    }

    public long getIpWindowMinutes() {
        return ipWindowMinutes;
    }

    public void setIpWindowMinutes(long ipWindowMinutes) {
        this.ipWindowMinutes = ipWindowMinutes;
    }

    public boolean isAdminUnlockEnabled() {
        return adminUnlockEnabled;
    }

    public void setAdminUnlockEnabled(boolean adminUnlockEnabled) {
        this.adminUnlockEnabled = adminUnlockEnabled;
    }
}
package com.ridebooking.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình gửi email và các luồng dựa trên email.
 *
 * <p>Không gắn cứng nhà cung cấp: cùng code này chạy được với mailpit ở local
 * (bắt email trong Docker), và với Gmail / AWS SES / Brevo ở production —
 * chỉ cần đổi {@code spring.mail.*}.
 */
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

    /** Địa chỉ người gửi. */
    private String from = "no-reply@ridebooking.local";

    /** Tên hiển thị cùng địa chỉ người gửi. */
    private String fromName = "Ride Booking";

    /**
     * Nếu đặt, mọi email cảnh báo bảo mật (đổi email, phát hiện đăng nhập lạ)
     * cũng được gửi tới địa chỉ này.
     */
    private String securityTo = "";

    /** Domain frontend, dùng dựng link xác minh / đặt lại mật khẩu. */
    private String frontendUrl = "http://localhost:5173";

    /**
     * Bắt buộc email đã xác minh mới được đăng nhập hay không.
     *
     * <p>Mặc định {@code false}: tài khoản dùng ngay sau khi đăng ký, phù hợp khi
     * đang dùng mailpit ở local. Bật {@code true} ở production.
     */
    private boolean requireVerifiedForLogin = false;

    /** Hạn link xác minh email (phút). Mặc định 1 ngày. */
    private long verifyTtlMinutes = 1440;

    /** Hạn link đặt lại mật khẩu (phút). Mặc định 30 phút. */
    private long passwordResetTtlMinutes = 30;

    /** Hạn link xác nhận đổi email (phút). Mặc định 60 phút. */
    private long emailChangeTtlMinutes = 60;

    /** Số lần gửi lại cùng một loại email cho 1 địa chỉ trong {@link #resendWindowMinutes}. */
    private int maxPerEmailPerWindow = 3;

    /** Số lần gửi cho 1 IP trong {@link #resendWindowMinutes}. */
    private int maxPerIpPerWindow = 10;

    /** Độ rộng cửa sổ đếm số lần gửi (phút). */
    private long resendWindowMinutes = 60;

    /** Số ngày giữ lịch sử gửi để chống gửi lại vô hạn. */
    private long historyRetentionDays = 7;

    /**
     * Số lần thử tối đa cho một email, tính cả lần gửi đầu tiên. Hết số lần thì
     * dòng chuyển sang DEAD và không thử nữa — nếu không giới hạn, một địa chỉ
     * không tồn tại sẽ bị gửi đi mãi mỗi phút một lần.
     */
    private int maxDeliveryAttempts = 5;

    /** Số email gửi lại trong một lượt quét. Trần để một lần quét không chiếm CPU. */
    private int retryBatchSize = 50;

    /** Chu kỳ quét hàng đợi gửi lại (giây). */
    private int retryScanSeconds = 60;

    /** Lần chờ trước khi thử gửi lại lần đầu. Các lần sau nhân dần. */
    private int retryBaseDelaySeconds = 60;

    /** Trần cho khoảng chờ giữa hai lần thử (giây). */
    private int retryMaxDelaySeconds = 900;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getSecurityTo() {
        return securityTo;
    }

    public void setSecurityTo(String securityTo) {
        this.securityTo = securityTo;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public boolean isRequireVerifiedForLogin() {
        return requireVerifiedForLogin;
    }

    public void setRequireVerifiedForLogin(boolean requireVerifiedForLogin) {
        this.requireVerifiedForLogin = requireVerifiedForLogin;
    }

    public Duration getVerifyTtl() {
        return Duration.ofMinutes(verifyTtlMinutes);
    }

    public Duration getPasswordResetTtl() {
        return Duration.ofMinutes(passwordResetTtlMinutes);
    }

    public Duration getEmailChangeTtl() {
        return Duration.ofMinutes(emailChangeTtlMinutes);
    }

    public int getMaxPerEmailPerWindow() {
        return maxPerEmailPerWindow;
    }

    public void setMaxPerEmailPerWindow(int maxPerEmailPerWindow) {
        this.maxPerEmailPerWindow = maxPerEmailPerWindow;
    }

    public int getMaxPerIpPerWindow() {
        return maxPerIpPerWindow;
    }

    public void setMaxPerIpPerWindow(int maxPerIpPerWindow) {
        this.maxPerIpPerWindow = maxPerIpPerWindow;
    }

    public long getResendWindowMinutes() {
        return resendWindowMinutes;
    }

    public void setResendWindowMinutes(long resendWindowMinutes) {
        this.resendWindowMinutes = resendWindowMinutes;
    }

    public long getHistoryRetentionDays() {
        return historyRetentionDays;
    }

    public void setHistoryRetentionDays(long historyRetentionDays) {
        this.historyRetentionDays = historyRetentionDays;
    }

    public int getMaxDeliveryAttempts() {
        return maxDeliveryAttempts;
    }

    public void setMaxDeliveryAttempts(int maxDeliveryAttempts) {
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    public int getRetryBatchSize() {
        return retryBatchSize;
    }

    public void setRetryBatchSize(int retryBatchSize) {
        this.retryBatchSize = retryBatchSize;
    }

    public int getRetryScanSeconds() {
        return retryScanSeconds;
    }

    public void setRetryScanSeconds(int retryScanSeconds) {
        this.retryScanSeconds = retryScanSeconds;
    }

    public int getRetryBaseDelaySeconds() {
        return retryBaseDelaySeconds;
    }

    public void setRetryBaseDelaySeconds(int retryBaseDelaySeconds) {
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
    }

    public int getRetryMaxDelaySeconds() {
        return retryMaxDelaySeconds;
    }

    public void setRetryMaxDelaySeconds(int retryMaxDelaySeconds) {
        this.retryMaxDelaySeconds = retryMaxDelaySeconds;
    }
}

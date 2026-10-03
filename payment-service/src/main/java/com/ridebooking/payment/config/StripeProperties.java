package com.ridebooking.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình Stripe.
 *
 * <p>{@code secretKey} và {@code webhookSecret} KHÔNG được hardcode: đọc từ biến
 * môi trường {@code STRIPE_SECRET_KEY} / {@code STRIPE_WEBHOOK_SECRET}. Chúng bắt
 * buộc phải có, nếu thiếu service dừng ngay lúc khởi động thay vì chạy với khoá
 * rỗng rồi lỗi mơ hồ khi khách bấm thanh toán.
 */
@ConfigurationProperties(prefix = "app.stripe")
public class StripeProperties {

    /** Bắt buộc. Test key bắt đầu bằng {@code sk_test_}, live bằng {@code sk_live_}. */
    private String secretKey;

    /** Bắt buộc. Lấy từ endpoint webhook trên Stripe Dashboard hoặc Stripe CLI. */
    private String webhookSecret;

    /** Nơi Stripe đưa khách về sau khi trả tiền thành công. */
    private String successUrl = "http://localhost:3000/my-bookings";

    /** Nơi Stripe đưa khách về khi khách bỏ ngang. */
    private String cancelUrl = "http://localhost:3000/booking/cancel";

    /** Đơn vị tiền tệ gửi Stripe. VND là tiền tệ 0 chữ số thập phân. */
    private String currency = "vnd";

    /**
     * Có xác thực chữ ký webhook không. Chỉ nên đặt {@code false} trong test
     * tích hợp chạy offline; production BẮT BUỘC {@code true}.
     */
    private boolean verifyWebhookSignature = true;

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isVerifyWebhookSignature() {
        return verifyWebhookSignature;
    }

    public void setVerifyWebhookSignature(boolean verifyWebhookSignature) {
        this.verifyWebhookSignature = verifyWebhookSignature;
    }
}

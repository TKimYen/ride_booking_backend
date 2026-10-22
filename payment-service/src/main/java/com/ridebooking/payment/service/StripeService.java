package com.ridebooking.payment.service;

import com.ridebooking.payment.config.StripeProperties;
import com.ridebooking.payment.exception.BadRequestException;
import com.ridebooking.payment.exception.PaymentGatewayException;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Bọc Stripe SDK lại một chỗ.
 *
 * <p>Tách khỏi {@link PaymentService} để phần nghiệp vụ (kiểm quyền, gọi
 * booking-service, hoàn tiền) test được bằng mock mà không cần khoá thật hay
 * mạng.
 */
@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final StripeProperties properties;

    public StripeService(StripeProperties properties) {
        this.properties = properties;
    }

    /**
     * Tạo Stripe Checkout Session cho một đơn.
     *
     * <p>VND là tiền tệ 0 chữ số thập phân nên {@code unitAmount} chính là số tiền
     * thật, KHÔNG nhân 100 như USD. Nhân 100 sẽ thu gấp trăm lần.
     *
     * <p>Cố ý KHÔNG đặt {@code expiresAt} cho phiên: Stripe bắt tối thiểu 30 phút,
     * còn hệ thống chỉ giữ ghế 10 phút. Nếu ép theo hạn giữ ghế thì Stripe từ chối,
     * còn nếu ép 30 phút thì phiên sống lâu hơn ghế. Thay vào đó để mặc định và xử
     * lý ca "trả tiền sau khi hết hạn" ở webhook bằng hoàn tiền.
     */
    public Session createCheckoutSession(Integer bookingId, Integer userId, long amount) {
        configureApiKey();
        if (amount <= 0) {
            throw new BadRequestException("Số tiền thanh toán phải lớn hơn 0");
        }

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(properties.getSuccessUrl())
                    .setCancelUrl(properties.getCancelUrl())
                    .setClientReferenceId(String.valueOf(bookingId))
                    .putMetadata("bookingId", String.valueOf(bookingId))
                    // userId null = đơn của khách vãng lai
                    .putMetadata("userId", userId == null ? "guest" : String.valueOf(userId))
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(properties.getCurrency())
                                    .setUnitAmount(amount)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Vé xe - đơn #" + bookingId)
                                            .build())
                                    .build())
                            .build())
                    .build();

            return Session.create(params);
        } catch (StripeException ex) {
            throw new PaymentGatewayException("Không tạo được phiên thanh toán Stripe: " + ex.getMessage(), ex);
        }
    }

    /** Xác thực chữ ký và dịch payload webhook thành {@link Event}. */
    public Event parseWebhook(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new BadRequestException("Thiếu header Stripe-Signature");
        }
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) {
            throw new PaymentGatewayException("Chưa cấu hình STRIPE_WEBHOOK_SECRET cho payment-service");
        }
        try {
            return Webhook.constructEvent(payload, signatureHeader, properties.getWebhookSecret());
        } catch (SignatureVerificationException ex) {
            throw new BadRequestException("Chữ ký webhook Stripe không hợp lệ");
        }
    }

    /**
     * Hoàn tiền một payment_intent. Lỗi hoàn tiền chỉ được ghi log, không ném ra:
     * webhook vẫn nên trả 200 để Stripe ngừng gửi lại, còn việc hoàn tiền sai sót
     * cần người vận hành xử lý qua Dashboard.
     */
    public void refund(String paymentIntentId, Integer bookingId) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            log.error("Không hoàn tiền được cho đơn #{} vì webhook thiếu payment_intent", bookingId);
            return;
        }
        try {
            configureApiKey();
            Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .build());
            log.warn("Đã hoàn tiền payment_intent {} cho đơn #{} (ghế không còn giữ được)",
                    paymentIntentId, bookingId);
        } catch (StripeException ex) {
            log.error("Hoàn tiền THẤT BẠI cho payment_intent {} (đơn #{}): {}",
                    paymentIntentId, bookingId, ex.getMessage(), ex);
        }
    }

    private void configureApiKey() {
        String secretKey = properties.getSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new PaymentGatewayException(
                    "Chưa cấu hình STRIPE_SECRET_KEY cho payment-service");
        }
        Stripe.apiKey = secretKey;
    }
}

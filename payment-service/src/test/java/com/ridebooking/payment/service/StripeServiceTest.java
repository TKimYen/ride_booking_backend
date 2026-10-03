package com.ridebooking.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ridebooking.payment.config.StripeProperties;
import com.ridebooking.payment.exception.BadRequestException;
import com.ridebooking.payment.exception.PaymentGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Nghiệm thu phần "cấu hình thiếu / chữ ký sai" của StripeService.
 *
 * <p>Không gọi mạng: các ca này phải bị chặn TRƯỚC khi chạm Stripe, nếu không sẽ
 * thành lỗi 500 mơ hồ hoặc (tệ hơn) nuốt webhook giả.
 */
class StripeServiceTest {

    private StripeProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StripeProperties();
    }

    @Test
    @DisplayName("Thiếu STRIPE_SECRET_KEY thì báo lỗi rõ ràng, không gọi Stripe")
    void createCheckoutSessionFailsWhenKeyMissing() {
        properties.setSecretKey("  ");
        StripeService service = new StripeService(properties);

        assertThatThrownBy(() -> service.createCheckoutSession(1, 1, 1000L))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }

    @Test
    @DisplayName("Số tiền <= 0 bị từ chối trước khi gọi Stripe")
    void createCheckoutSessionRejectsNonPositiveAmount() {
        properties.setSecretKey("sk_test_dummy");
        StripeService service = new StripeService(properties);

        assertThatThrownBy(() -> service.createCheckoutSession(1, 1, 0L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Webhook thiếu header Stripe-Signature bị từ chối")
    void parseWebhookRequiresSignatureHeader() {
        properties.setWebhookSecret("whsec_test");
        StripeService service = new StripeService(properties);

        assertThatThrownBy(() -> service.parseWebhook("{}", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stripe-Signature");
    }

    @Test
    @DisplayName("Webhook chữ ký sai bị từ chối")
    void parseWebhookRejectsBadSignature() {
        properties.setWebhookSecret("whsec_test");
        StripeService service = new StripeService(properties);

        assertThatThrownBy(() -> service.parseWebhook("{\"id\":\"evt_1\"}", "t=1,v1=deadbeef"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không hợp lệ");
    }

    @Test
    @DisplayName("Thiếu STRIPE_WEBHOOK_SECRET thì không xác thực được webhook")
    void parseWebhookFailsWhenSecretMissing() {
        properties.setWebhookSecret("");
        StripeService service = new StripeService(properties);

        assertThatThrownBy(() -> service.parseWebhook("{}", "t=1,v1=x"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("STRIPE_WEBHOOK_SECRET");
    }

    @Test
    @DisplayName("Hoàn tiền với payment_intent rỗng chỉ ghi log, không ném lỗi")
    void refundWithoutPaymentIntentIsNoop() {
        StripeService service = new StripeService(properties);

        // Không có chỗ để ném ra: webhook vẫn phải trả 200 để Stripe ngừng gửi lại.
        service.refund(null, 7);
    }
}

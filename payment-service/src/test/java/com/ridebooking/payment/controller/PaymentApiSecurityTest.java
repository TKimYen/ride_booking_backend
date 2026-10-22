package com.ridebooking.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Bảo mật ở tầng HTTP.
 *
 * <p>Điều quan trọng: webhook Stripe KHÔNG được đòi JWT (Stripe không có token),
 * nhưng cũng KHÔNG được để lọt nếu thiếu chữ ký. Ngược lại checkout-session phải
 * đòi JWT.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "app.stripe.webhook-secret=whsec_test",
        "app.clients.booking-service.base-url=http://localhost:9"
})
@AutoConfigureMockMvc
@DisplayName("Bảo mật API payment-service")
class PaymentApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Tạo phiên thanh toán mà không có token -> 401")
    void checkoutSessionRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/payments/checkout-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Khách vãng lai gửi X-Guest-Token thì qua được tầng bảo mật (không 401)")
    void checkoutSessionAcceptsGuestTokenHeader() throws Exception {
        // booking-service ở cổng 9 không tồn tại nên request lỗi ở bước sau; điều cần
        // kiểm là Spring Security không chặn với 401 như khi thiếu cả JWT lẫn token.
        mockMvc.perform(post("/api/payments/checkout-session")
                        .header("X-Guest-Token", "some-guest-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    @Test
    @DisplayName("Webhook không cần JWT nhưng thiếu chữ ký -> 400, không phải 401")
    void webhookIsPublicButNeedsSignature() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Webhook với chữ ký sai -> 400")
    void webhookRejectsBadSignature() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                        .header("Stripe-Signature", "t=1,v1=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_test\"}"))
                .andExpect(status().isBadRequest());
    }
}

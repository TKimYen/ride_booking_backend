package com.ridebooking.payment.controller;

import com.ridebooking.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint Stripe gọi vào khi có sự kiện thanh toán.
 *
 * <p>Công khai (không JWT) nhưng bắt buộc có chữ ký {@code Stripe-Signature} hợp lệ,
 * xác thực bằng {@code STRIPE_WEBHOOK_SECRET}.
 */
@RestController
@RequestMapping("/api/payments")
@SecurityRequirements
@Tag(name = "2. Webhook (Stripe -> hệ thống)",
        description = "Stripe gọi vào, không cần JWT nhưng phải có chữ ký Stripe-Signature")
public class StripeWebhookController {

    private final PaymentService paymentService;

    public StripeWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Nhận sự kiện webhook từ Stripe",
            description = """
                    Xử lý `checkout.session.completed` / `checkout.session.async_payment_succeeded`
                    bằng cách gọi API nội bộ của booking-service để chuyển đơn sang PAID và ghế
                    sang BOOKED. Nếu ghế đã bị trả về do đơn hết hạn, tự động hoàn tiền.

                    Luôn trả 200 để Stripe không gửi lại (trừ khi chữ ký sai -> 400).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã nhận sự kiện"),
            @ApiResponse(responseCode = "400", description = "Thiếu hoặc sai chữ ký Stripe-Signature"),
            @ApiResponse(responseCode = "502", description = "Chưa cấu hình STRIPE_WEBHOOK_SECRET")})
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok(Map.of("received", true));
    }
}

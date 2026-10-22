package com.ridebooking.payment.controller;

import com.ridebooking.payment.dto.CheckoutSessionRequest;
import com.ridebooking.payment.dto.CheckoutSessionResponse;
import com.ridebooking.payment.security.AuthenticatedUser;
import com.ridebooking.payment.security.SecurityUtils;
import com.ridebooking.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "1. Thanh toán (Stripe)",
        description = "Tạo phiên thanh toán Stripe cho một đơn đã giữ ghế")
public class PaymentController {

    /** Token khách vãng lai, cùng tên header với booking-service. */
    public static final String GUEST_TOKEN_HEADER = "X-Guest-Token";

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Tạo phiên thanh toán Stripe",
            description = """
                    Trả về `checkoutUrl` để frontend chuyển hướng khách sang trang thanh toán
                    của Stripe. Số tiền lấy từ đơn ở booking-service, KHÔNG lấy từ request.

                    Điều kiện: đơn đang PENDING, chưa hết hạn giữ ghế, và thuộc về người gọi
                    (ADMIN được phép thanh toán hộ). Khách vãng lai không cần JWT, gửi header
                    `X-Guest-Token` nhận được lúc đặt vé.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo phiên, trả URL thanh toán"),
            @ApiResponse(responseCode = "400", description = "Thiếu bookingId"),
            @ApiResponse(responseCode = "401", description = "Không có JWT và không có X-Guest-Token"),
            @ApiResponse(responseCode = "403", description = "Đơn của người khác / token khách sai"),
            @ApiResponse(responseCode = "404", description = "Không có đơn"),
            @ApiResponse(responseCode = "409", description = "Đơn đã thanh toán / đã hết hạn giữ ghế"),
            @ApiResponse(responseCode = "502", description = "Stripe hoặc booking-service lỗi")})
    @PostMapping("/checkout-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @RequestHeader(value = GUEST_TOKEN_HEADER, required = false) String guestToken,
            @Valid @RequestBody CheckoutSessionRequest request) {
        AuthenticatedUser user = SecurityUtils.currentUser().orElse(null);
        if (user == null && (guestToken == null || guestToken.isBlank())) {
            throw new AccessDeniedException("Chưa xác thực");
        }
        CheckoutSessionResponse response = paymentService.createCheckoutSession(request.bookingId(), user, guestToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

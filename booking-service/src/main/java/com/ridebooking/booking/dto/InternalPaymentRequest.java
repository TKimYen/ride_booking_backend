package com.ridebooking.booking.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * Yêu cầu xác nhận đã thu tiền, do payment-service gửi qua API nội bộ.
 *
 * <p>Không có {@code userId}: quyền được kiểm bằng header {@code X-Internal-Key}
 * ở tầng controller, không phải theo người dùng cuối.
 */
public record InternalPaymentRequest(
        @NotBlank(message = "gateway không được để trống") String gateway,
        @NotBlank(message = "transactionNo không được để trống") String transactionNo,
        BigDecimal amount,
        String bankCode,
        String responseCode) {
}

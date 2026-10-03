package com.ridebooking.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Yêu cầu tạo phiên thanh toán.
 *
 * <p>Cố ý chỉ nhận {@code bookingId}: số tiền được booking-service trả về, KHÔNG
 * lấy từ client. Nếu nhận số tiền từ client thì kẻ xấu có thể trả 1.000đ cho đơn
 * 500.000đ.
 */
public record CheckoutSessionRequest(
        @Schema(description = "Mã đơn đặt vé cần thanh toán", example = "1")
        @NotNull(message = "bookingId không được để trống")
        Integer bookingId) {
}

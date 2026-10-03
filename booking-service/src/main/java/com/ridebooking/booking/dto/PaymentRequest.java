package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body cho {@code POST /api/bookings/{id}/pay}.
 *
 * <p>Giai đoạn 2 chạy cổng demo ngay trong booking-service. Khi chỉ gửi
 * {@code gateway} (không có {@code responseCode}), service coi như thành công và
 * sinh mã giao dịch. Khi có {@code responseCode} (dùng cho callback cổng thật),
 * chỉ {@code "00"} mới được tính thành công — các mã khác tạo giao dịch FAILED và
 * giữ đơn ở PENDING để khách thử lại.
 */
public record PaymentRequest(

        @NotBlank(message = "Cổng thanh toán không được bỏ trống")
        @Schema(description = "VNPAY | MOMO | ZALOPAY", example = "VNPAY", requiredMode = Schema.RequiredMode.REQUIRED)
        String gateway,

        @Size(max = 50, message = "Mã ngân hàng tối đa 50 ký tự")
        @Schema(description = "Mã ngân hàng, nếu có", example = "NCB")
        String bankCode,

        @Size(max = 100, message = "Mã giao dịch tối đa 100 ký tự")
        @Schema(description = "Mã giao dịch của cổng; bỏ trống thì service tự sinh", example = "VNP1701234567")
        String transactionNo,

        @Size(max = 20, message = "Mã phản hồi tối đa 20 ký tự")
        @Schema(description = "Mã phản hồi của cổng. Bỏ trống = demo thành công", example = "00")
        String responseCode) {
}

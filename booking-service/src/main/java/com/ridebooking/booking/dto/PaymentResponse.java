package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.Payment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một giao dịch thanh toán của đơn. */
@Schema(description = "Giao dịch thanh toán")
public record PaymentResponse(

        @Schema(example = "5") Integer id,
        @Schema(description = "VNPAY | MOMO | ZALOPAY", example = "VNPAY") String gateway,
        @Schema(example = "VNP1701234567") String transactionNo,
        @Schema(example = "500000.00") BigDecimal amount,
        @Schema(example = "NCB") String bankCode,
        @Schema(description = "Mã phản hồi của cổng. '00' = thành công", example = "00") String responseCode,
        @Schema(description = "PENDING | SUCCESS | FAILED", example = "SUCCESS") String status,
        @Schema(example = "2026-10-02T10:15:30") LocalDateTime createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentGateway().name(),
                payment.getTransactionNo(),
                payment.getAmount(),
                payment.getBankCode(),
                payment.getResponseCode(),
                payment.getStatus().name(),
                payment.getCreatedAt());
    }
}

package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả huỷ chuyến.
 *
 * <p>{@code refunds} tính từ dữ liệu đã lưu (đơn CANCELLED có giao dịch SUCCESS),
 * không phải từ lần gọi này. Admin gọi lại vẫn nhận đúng danh sách, nên mất phản
 * hồi giữa chừng không làm mất thông tin khách cần hoàn tiền.
 */
@Schema(description = "Kết quả huỷ chuyến, kèm danh sách đơn cần hoàn tiền")
public record TripCancellationResponse(

        TripResponse trip,

        @Schema(description = "Số đơn vừa bị huỷ ở lần gọi này", example = "5")
        int cancelledBookings,

        @Schema(description = "Số ghế vừa được trả lại ở lần gọi này", example = "9")
        int releasedSeats,

        @Schema(description = "Các đơn đã thu tiền, cần hoàn. Hệ thống CHƯA tự hoàn tiền")
        List<RefundItem> refunds,

        @Schema(description = "Tổng số tiền cần hoàn", example = "1500000.00")
        BigDecimal totalRefundAmount) {

    @Schema(description = "Một đơn cần hoàn tiền")
    public record RefundItem(
            @Schema(example = "42") Integer bookingId,
            @Schema(example = "XG7K2M9P") String bookingCode,
            @Schema(example = "Nguyễn Văn A") String contactName,
            @Schema(example = "0901234567") String contactPhone,
            @Schema(example = "khach@example.com") String contactEmail,
            @Schema(example = "500000.00") BigDecimal amount,
            @Schema(example = "STRIPE") String gateway,
            @Schema(description = "Mã giao dịch để hoàn trên cổng thanh toán", example = "pi_3Abc") String transactionNo) {
    }
}

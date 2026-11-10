package com.ridebooking.booking.dto;

import com.ridebooking.booking.dto.TripCancellationResponse.RefundItem;
import io.swagger.v3.oas.annotations.media.Schema;

/** Kết quả Admin huỷ một đơn [4.5]. */
@Schema(description = "Kết quả Admin huỷ đơn, kèm thông tin hoàn tiền nếu đơn đã thu tiền")
public record AdminBookingCancellationResponse(

        BookingResponse booking,

        @Schema(description = "Có khi đơn đã thanh toán thành công; null nếu chưa thu tiền. Hệ thống CHƯA tự hoàn tiền")
        RefundItem refund) {
}

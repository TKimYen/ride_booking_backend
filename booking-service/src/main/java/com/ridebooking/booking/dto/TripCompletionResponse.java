package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Kết quả Admin bấm "hoàn thành chuyến". */
@Schema(description = "Kết quả hoàn thành chuyến")
public record TripCompletionResponse(

        TripResponse trip,

        @Schema(description = "Số đơn vừa chuyển từ PAID sang COMPLETED; gọi lại lần nữa thì bằng 0", example = "12")
        int completedBookings) {
}

package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Chi tiết chuyến cho Admin: số ghế và số đơn theo từng trạng thái. */
@Schema(description = "Chi tiết chuyến cho Admin")
public record AdminTripDetailResponse(

        TripSearchItemResponse seats,

        @Schema(description = "Số đơn theo trạng thái; trạng thái không có đơn thì không xuất hiện",
                example = "{\"PAID\": 12, \"PENDING\": 1}")
        Map<String, Long> bookingsByStatus) {
}

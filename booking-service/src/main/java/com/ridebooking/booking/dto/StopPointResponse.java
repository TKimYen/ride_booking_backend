package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.StopPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** Một điểm dừng trên hành trình của một chuyến. */
@Schema(description = "Một điểm đón hoặc điểm trả của chuyến")
public record StopPointResponse(

        @Schema(example = "9") Integer id,
        @Schema(example = "17") Integer tripId,
        @Schema(example = "Bến xe Mỹ Đình") String name,
        @Schema(example = "Số 1 Lê Thanh Nghị, Hà Nội") String address,
        @Schema(description = "PICKUP | DROPOFF", example = "PICKUP") String pointType,
        @Schema(description = "Lần đón thứ mấy. Đánh số từ 1, không có chỗ trống.",
                example = "2") Integer stopOrder,
        @Schema(example = "2026-10-05T07:30:00") LocalDateTime estimatedTime) {

    public static StopPointResponse from(StopPoint point) {
        return new StopPointResponse(
                point.getId(),
                point.getTrip().getId(),
                point.getName(),
                point.getAddress(),
                point.getPointType().name(),
                point.getStopOrder(),
                point.getEstimatedTime());
    }
}

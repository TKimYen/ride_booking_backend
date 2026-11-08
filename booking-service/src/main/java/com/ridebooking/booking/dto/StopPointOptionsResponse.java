package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Tên điểm đón / trả khác nhau của các chuyến khớp bộ lọc, để dựng checkbox lọc. */
@Schema(description = "Các điểm đón và điểm trả có thể chọn để lọc")
public record StopPointOptionsResponse(
        @Schema(example = "[\"Bến xe Mỹ Đình\", \"Ngã tư Sở\"]") List<String> pickups,
        @Schema(example = "[\"Bến xe Sa Pa\"]") List<String> dropoffs) {
}

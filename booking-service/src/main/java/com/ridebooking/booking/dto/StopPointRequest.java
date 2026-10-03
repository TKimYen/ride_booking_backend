package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Body cho thêm/sửa điểm dừng. Chuyến lấy từ đường dẫn {@code /api/admin/trips/{tripId}/stop-points}. */
public record StopPointRequest(

        @NotBlank(message = "Tên điểm dừng không được bỏ trống")
        @Size(max = 200, message = "Tên điểm dừng không được dài quá 200 ký tự")
        @Schema(description = "VD: Bến xe Mỹ Đình", example = "Bến xe Mỹ Đình", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Size(max = 300, message = "Địa chỉ không được dài quá 300 ký tự")
        @Schema(description = "Địa chỉ đầy đủ, dùng để frontend hiện chú thích dưới tên",
                example = "Số 1 Đường Lê Thanh Nghị, Hà Đông, Hà Nội")
        String address,

        @NotBlank(message = "Loại điểm dừng không được bỏ trống")
        @Schema(description = "PICKUP = điểm đón, DROPOFF = điểm trả",
                example = "PICKUP", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"PICKUP", "DROPOFF"})
        String pointType,

        @Schema(description = "Giờ dự kiến tới điểm này. Bỏ trống nếu chưa chốt.",
                example = "2026-10-05T07:30:00")
        LocalDateTime estimatedTime) {
}

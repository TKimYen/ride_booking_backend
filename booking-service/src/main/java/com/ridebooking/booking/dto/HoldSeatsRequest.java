package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body cho {@code POST /api/bookings} — giữ ghế và tạo đơn.
 *
 * <p>Ghế được gửi theo id (lấy từ sơ đồ ghế), không theo tên: tên ghế có thể đổi
 * khi Admin đổi loại xe, còn id thì cố định.
 */
public record HoldSeatsRequest(

        @NotNull(message = "Chuyến không được bỏ trống")
        @Schema(description = "id của trips", example = "17", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer tripId,

        @NotEmpty(message = "Phải chọn ít nhất một ghế")
        @Size(max = 10, message = "Mỗi đơn tối đa 10 ghế")
        @Schema(description = "id các ghế, lấy từ GET /api/trips/{tripId}/seats",
                example = "[120, 121]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Integer> seatIds,

        @Schema(description = "id điểm đón (stop_points thuộc chuyến); có thể bỏ trống", example = "40")
        Integer pickupPointId,

        @Schema(description = "id điểm trả (stop_points thuộc chuyến); có thể bỏ trống", example = "41")
        Integer dropoffPointId,

        @NotBlank(message = "Tên người liên hệ không được bỏ trống")
        @Size(max = 255, message = "Tên người liên hệ tối đa 255 ký tự")
        @Schema(example = "Nguyễn Văn A", requiredMode = Schema.RequiredMode.REQUIRED)
        String contactName,

        @NotBlank(message = "Số điện thoại không được bỏ trống")
        @Pattern(regexp = "^[0-9+][0-9 .-]{7,19}$", message = "Số điện thoại không hợp lệ")
        @Schema(example = "0901234567", requiredMode = Schema.RequiredMode.REQUIRED)
        String contactPhone,

        @Email(message = "Email không hợp lệ")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        @Schema(description = "Bỏ trống thì lấy email từ token đăng nhập", example = "a@example.com")
        String contactEmail) {
}

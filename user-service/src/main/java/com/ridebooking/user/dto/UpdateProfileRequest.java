package com.ridebooking.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Dữ liệu cập nhật hồ sơ từ Trang Thông tin tài khoản.
 * Email không nằm trong đây vì đổi email phải qua luồng xác minh của auth-service.
 */
@Schema(description = "Body cho PUT /api/users/me")
public record UpdateProfileRequest(

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 255, message = "Họ tên không được vượt quá 255 ký tự")
        @Schema(description = "Họ và tên", example = "Nguyễn Anh Huy", requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,

        @Pattern(regexp = "^$|^\\+?[0-9]{9,15}$", message = "Số điện thoại không hợp lệ")
        @Schema(description = "Số điện thoại, để trống để xoá", example = "0912345678")
        String phone,

        @Schema(description = "Ngày sinh yyyy-MM-dd", example = "2002-05-20")
        LocalDate dob,

        @Pattern(regexp = "^$|Nam|Nữ|Khác", message = "Giới tính chỉ nhận Nam, Nữ hoặc Khác")
        @Schema(description = "Giới tính: Nam / Nữ / Khác", example = "Nam")
        String gender) {
}
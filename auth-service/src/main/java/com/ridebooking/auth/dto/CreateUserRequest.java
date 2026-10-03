package com.ridebooking.auth.dto;

import com.ridebooking.auth.enums.Role;
import com.ridebooking.auth.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Body cho POST /api/admin/users (Admin tạo tài khoản)")
public record CreateUserRequest(

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 255)
        @Schema(description = "Họ và tên", example = "Nguyễn Văn A", requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Schema(description = "Email đăng nhập", example = "nva@test.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @StrongPassword
        @Schema(description = "Mật khẩu tạo cho tài khoản, 8-72 ký tự, cần cả chữ và số",
                example = "Admin@2026", requiredMode = Schema.RequiredMode.REQUIRED)
        String password,

        @Schema(description = "Số điện thoại liên hệ", example = "0912345678")
        String phone,

        @Schema(description = "Quyền cấp cho tài khoản, mặc định USER", example = "USER")
        Role role) {
    /** Trim {@code email} trước khi Bean Validation chạy — xem {@link RegisterRequest}. */

    public CreateUserRequest {
        email = email == null ? null : email.trim();
        fullName = fullName == null ? null : fullName.trim();
    }
}

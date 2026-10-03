package com.ridebooking.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ridebooking.auth.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Body cho POST /api/auth/register")
public record RegisterRequest(

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 255, message = "Họ tên không được vượt quá 255 ký tự")
        @Schema(description = "Họ và tên", example = "Nguyễn Anh Huy", requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,

        @NotBlank(message = "Email không được để trống")
        @jakarta.validation.constraints.Email(message = "Email không hợp lệ")
        @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
        @Schema(description = "Email dùng để đăng nhập, duy nhất trong hệ thống",
                example = "huy@test.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @StrongPassword
        @Schema(description = "Mật khẩu (lưu BCrypt), 8-72 ký tự, cần cả chữ và số",
                example = "Huy@2002x", requiredMode = Schema.RequiredMode.REQUIRED)
        String password,

        @Schema(description = "Số điện thoại liên hệ (chuyển sang user-service)",
                example = "0912345678")
        String phone,

        @Schema(description = "Ngày sinh yyyy-MM-dd (chuyển sang user-service)", example = "2002-05-20")
        java.time.LocalDate dob,

        @Schema(description = "Giới tính: Nam / Nữ / Khác (chuyển sang user-service)", example = "Nam")
        @Size(max = 20, message = "Giới tính không được vượt quá 20 ký tự")
        String gender) {

    /**
     * Chuẩn hoá dữ liệu ngay khi dựng object, TRƯỚC khi Bean Validation chạy.
     *
     * <p>Không làm vậy thì {@code @Email} nhìn thấy chuỗi thô còn khoảng trắng
     * và báo "Email không hợp lệ", dù người dùng copy-paste dính space hai bên
     * vẫn dùng được. Còn {@code normalizeEmail()} ở service chỉ chạy sau khi
     * validate đã xong, nên không cứu được.
     *
     * <p>TUYỆT ĐỐI không trim {@code password}: khoảng trắng là một phần của bí
     * mật, tự ý cắt đi sẽ khiến mật khẩu gõ ra khác mật khẩu đăng ký.
     */
    public RegisterRequest {
        email = email == null ? null : email.trim();
        fullName = fullName == null ? null : fullName.trim();
    }
}
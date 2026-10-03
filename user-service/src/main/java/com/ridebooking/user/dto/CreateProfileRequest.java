package com.ridebooking.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body cho {@code POST /api/internal/users} — chỉ auth-service được gọi.
 * Dùng để tạo hồ sơ ngay khi có tài khoản mới, idempotent theo {@code userId}.
 */
@Schema(description = "Yêu cầu đồng bộ hồ sơ từ auth-service (internal)")
public record CreateProfileRequest(

        @NotNull(message = "userId không được để trống")
        @Schema(description = "users.id bên auth_db", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer userId,

        @Size(max = 255, message = "Họ tên không được vượt quá 255 ký tự")
        @Schema(description = "Họ và tên", example = "Nguyễn Anh Huy")
        String fullName,

        @Email(message = "Email không hợp lệ")
        @Size(max = 255)
        @Schema(description = "Email đăng nhập", example = "huy@test.com")
        String email,

        @Schema(description = "Số điện thoại", example = "0912345678")
        String phone,

        @Schema(description = "Ngày sinh yyyy-MM-dd", example = "2002-05-20")
        LocalDate dob,

        @Schema(description = "Giới tính: Nam / Nữ / Khác", example = "Nam")
        String gender,

        @Schema(description = "Quyền tài khoản lấy từ auth_db", example = "USER")
        String role) {

    /**
     * Chuẩn hoá ngay khi dựng object, trước khi Bean Validation chạy, để
     * {@code @Email} không bị báo sai khi giá trị có khoảng trắng thừa.
     * Khác với auth-service, ở đây chỉ trim chứ KHÔNG lowercase: dữ liệu
     * {@code role} và {@code email} ở đây đến từ auth-service đã chuẩn hoá rồi,
     * hạ cấp chữ thường lần nữa sẽ làm lệch với dữ liệu đã lưu.
     */
    public CreateProfileRequest {
        email = email == null ? null : email.trim();
    }
}

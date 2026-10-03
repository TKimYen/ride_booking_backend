package com.ridebooking.auth.dto;

import com.ridebooking.auth.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Thông tin người dùng trả về sau khi đăng nhập / đăng ký")
public record UserResponse(

        @Schema(description = "Khoá chính, dùng làm userId trong các service khác", example = "1")
        Integer id,

        @Schema(description = "Họ và tên", example = "Nguyễn Anh Huy")
        String fullName,

        @Schema(description = "Số điện thoại liên hệ", example = "0912345678")
        String phone,

        @Schema(description = "Email đăng nhập", example = "huy@test.com")
        String email,

        @Schema(description = "Ngày sinh định dạng yyyy-MM-dd", example = "2002-05-20")
        LocalDate dob,

        @Schema(description = "Giới tính: Nam / Nữ / Khác", example = "Nam")
        String gender,

        @Schema(description = "Quyền tài khoản", example = "USER")
        Role role,

        @Schema(description = "Thời điểm tạo tài khoản")
        String createdAt) {
}
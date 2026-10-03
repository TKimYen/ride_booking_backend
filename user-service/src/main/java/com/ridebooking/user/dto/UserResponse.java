package com.ridebooking.user.dto;

import com.ridebooking.user.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Hồ sơ người dùng (nguồn: user_db.user_profiles + role từ JWT)")
public record UserResponse(

        @Schema(description = "userId, sinh ra bởi auth-service", example = "1")
        Integer id,

        @Schema(description = "Họ và tên", example = "Nguyễn Anh Huy")
        String fullName,

        @Schema(description = "Số điện thoại liên hệ", example = "0912345678")
        String phone,

        @Schema(description = "Email đăng nhập (đồng bộ từ auth-service)", example = "huy@test.com")
        String email,

        @Schema(description = "Ngày sinh yyyy-MM-dd", example = "2002-05-20")
        LocalDate dob,

        @Schema(description = "Giới tính: Nam / Nữ / Khác", example = "Nam")
        String gender,

        @Schema(description = "Quyền tài khoản, đọc từ claim role trong JWT", example = "USER")
        Role role,

        @Schema(description = "Thời điểm tạo hồ sơ")
        String createdAt,

        @Schema(description = "true nghĩa là dữ liệu đã bị che vì người gọi không có quyền xem chi tiết")
        boolean masked,

        @Schema(description = "Số ký tự đã bị che khỏi email, hữu ích cho client hiển thị",
                example = "5")
        Integer hiddenEmailChars) {

    public static UserResponse of(Integer id, String fullName, String phone, String email,
                                  LocalDate dob, String gender, Role role, String createdAt) {
        return new UserResponse(id, fullName, phone, email, dob, gender, role, createdAt, false, null);
    }

    /**
     * Bản dành cho người dùng thường xem hồ sơ người khác: chỉ giữ họ tên và
     * che email / SĐT / ngày sinh / giới tính. Thiếu dữ liệu liên hệ thì không
     * nên gợi ý cho tài xế thấy SĐT hay ngày sinh của hành khách.
     */
    public UserResponse maskedView() {
        if (masked) {
            return this;
        }
        String maskedEmail = maskEmail(email);
        int hidden = email == null ? 0 : email.indexOf('@') - 1;
        return new UserResponse(id, fullName, null, maskedEmail, null, null, role, createdAt, true,
                hidden > 0 ? hidden : null);
    }

    /** {@code huy.nguyen@gmail.com} -> {@code h*******@gmail.com} */
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() == 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "*".repeat(local.length() - 1) + domain;
    }
}
package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Phần hồi của user-service mà booking cần.
 *
 * <p>Không dùng chung DTO {@code UserResponse} của user-service: đặt service khác
 * vào classpath là để bị kéo theo thay đổi DTO của nó. User-service thêm một
 * trường hoặc đổi kiểu là booking-service không compile, dù hai service không liên
 * quan gì về nghiệp vụ.
 */
@Schema(description = "Hồ sơ rút gọn từ user-service")
public record InternalUserProfile(
        @Schema(example = "1") Integer id,
        @Schema(example = "Nguyễn Anh Huy") String fullName) {
}

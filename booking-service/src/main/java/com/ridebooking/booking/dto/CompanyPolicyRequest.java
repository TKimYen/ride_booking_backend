package com.ridebooking.booking.dto;

import com.ridebooking.booking.enums.PolicyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body thêm/sửa chính sách của nhà xe. */
public record CompanyPolicyRequest(

        @NotNull(message = "Loại chính sách không được bỏ trống")
        @Schema(example = "LUGGAGE", requiredMode = Schema.RequiredMode.REQUIRED)
        PolicyType policyType,

        @NotBlank(message = "Tiêu đề không được bỏ trống")
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
        @Schema(example = "Hành lý", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @NotBlank(message = "Nội dung không được bỏ trống")
        @Size(max = 10000, message = "Nội dung tối đa 10000 ký tự")
        @Schema(example = "Mỗi khách được mang 20kg hành lý.", requiredMode = Schema.RequiredMode.REQUIRED)
        String content,

        @Min(value = 1, message = "Thứ tự hiển thị bắt đầu từ 1")
        @Schema(description = "Bỏ trống thì là 1", example = "1")
        Integer sortOrder) {
}

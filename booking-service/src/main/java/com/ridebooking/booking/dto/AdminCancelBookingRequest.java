package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin huỷ đơn: bắt buộc nhập lý do để lưu vết và báo cho khách. */
@Schema(description = "Yêu cầu Admin huỷ đơn")
public record AdminCancelBookingRequest(
        @NotBlank(message = "Vui lòng nhập lý do huỷ đơn")
        @Size(max = 255, message = "Lý do huỷ tối đa 255 ký tự")
        @Schema(example = "Khách gọi tổng đài xin huỷ", requiredMode = Schema.RequiredMode.REQUIRED)
        String reason) {
}

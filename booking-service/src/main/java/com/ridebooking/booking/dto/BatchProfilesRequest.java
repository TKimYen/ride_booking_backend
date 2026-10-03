package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Body gửi sang {@code POST /api/internal/users/batch} của user-service. */
@Schema(description = "Body lấy hồ sơ hàng loạt")
public record BatchProfilesRequest(List<Integer> userIds) {
}

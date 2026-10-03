package com.ridebooking.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body cho {@code POST /api/internal/users/batch}.
 *
 * <p>Giới hạn 200 id: booking-service gom id của các đánh giá trong MỘT trang
 * (tối đa 100 theo mặc định). Cho phép vài nghìn id trong một lời gọi thì
 * user-service phải gọi lại database hàng nghìn lần trong một request, dễ làm
 * nó chết vì đúng cái request đó.
 */
@Schema(description = "Danh sách id cần lấy hồ sơ")
public record BatchProfilesRequest(

        @NotEmpty(message = "userIds không được rỗng")
        @Size(max = 200, message = "Một lần chỉ lấy tối đa 200 hồ sơ")
        @Schema(example = "[1, 2, 3]")
        List<Integer> userIds) {
}

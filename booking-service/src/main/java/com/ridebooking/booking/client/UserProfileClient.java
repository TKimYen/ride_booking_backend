package com.ridebooking.booking.client;

import java.util.List;
import java.util.Map;

/**
 * Lấy tên người đánh giá từ user-service.
 *
 * <p>{@code booking_db} không có khoá ngoại sang {@code user_db}, nên không join
 * được. Gọi qua HTTP là cách duy nhất — xem db.md mục C.
 */
public interface UserProfileClient {

    /**
     * Lấy tên hiển thị cho nhiều user id trong một lần gọi.
     *
     * @param userIds các id cần tên
     * @return map userId -> tên hiển thị. Id không có hồ sơ vắng mặt khỏi map.
     */
    Map<Integer, String> fetchDisplayNames(List<Integer> userIds);
}

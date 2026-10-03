package com.ridebooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Một dòng trong danh sách kết quả tìm kiếm: {@link TripResponse} cộng thêm số
 * ghế còn trống.
 *
 * <p>Tách riêng khỏi {@code TripResponse} vì số ghế còn trống chỉ có ý nghĩa ở
 * danh sách và trang chi tiết, còn {@code TripResponse} dùng chung cho các nơi
 * không cần đếm ghế.
 */
@Schema(description = "Chuyến xe kèm số ghế còn trống, dùng cho Trang Danh sách kết quả")
public record TripSearchItemResponse(

        TripResponse trip,

        @Schema(description = "Số ghế còn trống để đặt", example = "12")
        Integer availableSeats,

        @Schema(description = "Số ghế đã có người giữ hoặc đã bán", example = "17")
        Integer unavailableSeats) {

    public static TripSearchItemResponse of(TripResponse trip, Integer available, Integer unavailable) {
        return new TripSearchItemResponse(trip, available, unavailable);
    }
}

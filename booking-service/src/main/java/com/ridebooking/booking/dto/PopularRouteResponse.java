package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.Route;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Một thẻ trong block "Tuyến đường phổ biến" ở Trang chủ. */
@Schema(description = "Tuyến phổ biến")
public record PopularRouteResponse(

        @Schema(example = "4") Integer id,
        @Schema(example = "1") Integer originId,
        @Schema(example = "Hà Nội") String originName,
        @Schema(example = "8") Integer destinationId,
        @Schema(example = "Sa Pa") String destinationName,
        @Schema(example = "Hà Nội - Sa Pa") String name,
        @Schema(example = "465") BigDecimal distanceKm,
        @Schema(example = "510") Integer durationMinutes,
        @Schema(example = "https://picsum.photos/seed/sapa/800/500") String imageUrl,
        @Schema(description = "Admin đánh dấu nổi bật", example = "true") boolean featured,
        @Schema(description = "Số vé đã bán (đơn PAID hoặc COMPLETED)", example = "128") long ticketsSold,
        @Schema(description = "Giá cơ bản thấp nhất trong các chuyến sắp chạy; null khi chưa có chuyến",
                example = "250000.00") BigDecimal minPrice) {

    public static PopularRouteResponse of(Route route, long ticketsSold, BigDecimal minPrice) {
        return new PopularRouteResponse(
                route.getId(),
                route.getOrigin().getId(), route.getOrigin().getName(),
                route.getDestination().getId(), route.getDestination().getName(),
                route.getName(),
                route.getDistanceKm(),
                route.getDurationMinutes(),
                route.getImageUrl(),
                route.isFeatured(),
                ticketsSold,
                minPrice);
    }
}

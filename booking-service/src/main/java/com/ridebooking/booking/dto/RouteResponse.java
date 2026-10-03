package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.Route;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Một tuyến đường")
public record RouteResponse(

        @Schema(example = "4") Integer id,
        @Schema(description = "id địa điểm xuất phát") Integer originId,
        @Schema(description = "Tên địa điểm xuất phát", example = "Hà Nội") String originName,
        @Schema(description = "id địa điểm đến") Integer destinationId,
        @Schema(description = "Tên địa điểm đến", example = "Sa Pa") String destinationName,
        @Schema(example = "Hà Nội - Sa Pa") String name,
        @Schema(example = "155.5") BigDecimal distanceKm,
        @Schema(example = "300") Integer durationMinutes) {

    public static RouteResponse from(Route route) {
        return new RouteResponse(
                route.getId(),
                route.getOrigin().getId(), route.getOrigin().getName(),
                route.getDestination().getId(), route.getDestination().getName(),
                route.getName(),
                route.getDistanceKm(),
                route.getDurationMinutes());
    }
}

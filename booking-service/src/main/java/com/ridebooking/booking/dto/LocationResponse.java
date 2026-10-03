package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.Location;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một tỉnh/thành phố")
public record LocationResponse(

        @Schema(example = "12") Integer id,
        @Schema(example = "Hà Giang") String name,
        @Schema(example = "ha-giang") String slug) {

    public static LocationResponse from(Location location) {
        return new LocationResponse(location.getId(), location.getName(), location.getSlug());
    }
}

package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.Amenity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một tiện ích")
public record AmenityResponse(
        @Schema(example = "1") Integer id,
        @Schema(example = "WIFI") String code,
        @Schema(example = "Wi-Fi miễn phí") String name,
        @Schema(example = "wifi") String icon) {

    public static AmenityResponse from(Amenity amenity) {
        return new AmenityResponse(amenity.getId(), amenity.getCode(), amenity.getName(), amenity.getIcon());
    }
}

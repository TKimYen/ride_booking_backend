package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.BusCompany;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một nhà xe")
public record BusCompanyResponse(

        @Schema(example = "3") Integer id,
        @Schema(example = "G8 SAPA OPEN TOUR") String name,
        @Schema(description = "Mô tả nhà xe") String description,
        @Schema(description = "URL logo") String logoUrl) {

    public static BusCompanyResponse from(BusCompany company) {
        return new BusCompanyResponse(
                company.getId(), company.getName(),
                company.getDescription(), company.getLogoUrl());
    }
}

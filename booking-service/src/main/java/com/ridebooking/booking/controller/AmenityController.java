package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.AmenityResponse;
import com.ridebooking.booking.service.BusCompanyContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/amenities")
@Tag(name = "2. Tiện ích nhà xe")
public class AmenityController {

    private final BusCompanyContentService contentService;

    public AmenityController(BusCompanyContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping
    @Operation(summary = "Danh sách tiện ích công khai")
    public List<AmenityResponse> list() {
        return contentService.listAmenities();
    }
}

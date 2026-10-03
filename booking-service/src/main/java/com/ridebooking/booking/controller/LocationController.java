package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.LocationResponse;
import com.ridebooking.booking.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nhóm Guest — API đọc địa điểm.
 *
 * <p>Không yêu cầu đăng nhập: khách tìm chuyến trước, đăng nhập sau. Thêm/sửa/xoá
 * nằm ở {@code AdminLocationController}.
 */
@RestController
@RequestMapping("/api/locations")
@Tag(name = "1. Catalog - Địa điểm", description = "Tỉnh/thành phố: nguồn cho dropdown Nơi đi, Nơi đến")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @Operation(summary = "Gợi ý địa điểm cho dropdown",
            description = """
                    Bỏ trống `keyword` thì trả toàn bộ danh sách để dropdown dùng được ngay.
                    Có `keyword` thì chỉ khớp ở đầu tên: "Hà" ra "Hà Nội", "Hà Giang".
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")})
    @GetMapping
    public List<LocationResponse> suggest(
            @Parameter(description = "Từ khoá, phân biệt hoa thường không quan trọng", example = "Hà")
            @RequestParam(required = false) String keyword) {
        return locationService.suggest(keyword);
    }

    @Operation(summary = "Tất cả địa điểm")
    @GetMapping("/all")
    public List<LocationResponse> listAll() {
        return locationService.listAll();
    }

    @Operation(summary = "Chi tiết một địa điểm")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy địa điểm")})
    @GetMapping("/{id}")
    public LocationResponse getById(@PathVariable Integer id) {
        return locationService.getById(id);
    }
}
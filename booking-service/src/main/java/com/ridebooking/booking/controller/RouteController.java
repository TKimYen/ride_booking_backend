package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.RouteResponse;
import com.ridebooking.booking.service.RouteService;
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

/** Nhóm Guest — API đọc tuyến đường. Thêm/sửa/xoá nằm ở {@code AdminRouteController}. */
@RestController
@RequestMapping("/api/routes")
@Tag(name = "1. Catalog - Tuyến đường", description = "Tuyến đường giữa hai địa điểm")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @Operation(summary = "Danh sách tuyến",
            description = "Bỏ trống thì trả tất cả. Truyền `locationId` để lấy các tuyến có liên quan tới địa điểm đó (cả chiều đi và chiều đến).")
    @GetMapping
    public List<RouteResponse> list(
            @Parameter(description = "id địa điểm", example = "1")
            @RequestParam(required = false) Integer locationId) {
        if (locationId == null) {
            return routeService.listAll();
        }
        return routeService.touchingLocation(locationId);
    }

    @Operation(summary = "Tuyến đi ra từ một địa điểm",
            description = "Nguồn cho block \"Tuyến đường phổ biến\" ở Trang chủ.")
    @GetMapping("/from/{locationId}")
    public List<RouteResponse> popularFrom(@PathVariable Integer locationId) {
        return routeService.popularFrom(locationId);
    }

    @Operation(summary = "Chi tiết một tuyến")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy tuyến")})
    @GetMapping("/{id}")
    public RouteResponse getById(@PathVariable Integer id) {
        return routeService.getById(id);
    }
}

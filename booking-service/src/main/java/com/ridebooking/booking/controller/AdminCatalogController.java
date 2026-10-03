package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.BusCompanyRequest;
import com.ridebooking.booking.dto.BusCompanyResponse;
import com.ridebooking.booking.dto.RouteRequest;
import com.ridebooking.booking.dto.RouteResponse;
import com.ridebooking.booking.service.BusCompanyService;
import com.ridebooking.booking.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản trị nhà xe và tuyến đường.
 *
 * <p>Gộp hai nhóm vào một controller vì cùng quyền, cùng đối tượng dữ liệu
 * catalog. Tách thành hai file chỉ đổi tên class, không thêm gì.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "4. Admin - Nhà xe & Tuyến", description = "Thêm, sửa, xoá nhà xe và tuyến đường")
public class AdminCatalogController {

    private final BusCompanyService busCompanyService;
    private final RouteService routeService;

    public AdminCatalogController(BusCompanyService busCompanyService, RouteService routeService) {
        this.busCompanyService = busCompanyService;
        this.routeService = routeService;
    }


    @Operation(summary = "Danh sách nhà xe")
    @GetMapping("/bus-companies")
    public List<BusCompanyResponse> listCompanies() {
        return busCompanyService.listAll();
    }

    @Operation(summary = "Thêm nhà xe")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã thêm"),
            @ApiResponse(responseCode = "409", description = "Tên nhà xe đã tồn tại")})
    @PostMapping("/bus-companies")
    public ResponseEntity<BusCompanyResponse> createCompany(
            @Valid @RequestBody BusCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(busCompanyService.create(request));
    }

    @Operation(summary = "Sửa nhà xe")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã sửa"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy"),
            @ApiResponse(responseCode = "409", description = "Tên nhà xe đã tồn tại")})
    @PutMapping("/bus-companies/{id}")
    public BusCompanyResponse updateCompany(@PathVariable Integer id,
                                            @Valid @RequestBody BusCompanyRequest request) {
        return busCompanyService.update(id, request);
    }

    @Operation(summary = "Xoá nhà xe")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy")})
    @DeleteMapping("/bus-companies/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable Integer id) {
        busCompanyService.delete(id);
        return ResponseEntity.noContent().build();
    }


    @Operation(summary = "Danh sách tuyến")
    @GetMapping("/routes")
    public List<RouteResponse> listRoutes() {
        return routeService.listAll();
    }

    @Operation(summary = "Thêm tuyến")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã thêm"),
            @ApiResponse(responseCode = "400", description = "Điểm đi và đến bằng nhau"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy địa điểm"),
            @ApiResponse(responseCode = "409", description = "Tên tuyến đã tồn tại")})
    @PostMapping("/routes")
    public ResponseEntity<RouteResponse> createRoute(@Valid @RequestBody RouteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routeService.create(request));
    }

    @Operation(summary = "Sửa tuyến")
    @PutMapping("/routes/{id}")
    public RouteResponse updateRoute(@PathVariable Integer id, @Valid @RequestBody RouteRequest request) {
        return routeService.update(id, request);
    }

    @Operation(summary = "Xoá tuyến")
    @DeleteMapping("/routes/{id}")
    public ResponseEntity<Void> deleteRoute(@PathVariable Integer id) {
        routeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.LocationRequest;
import com.ridebooking.booking.dto.LocationResponse;
import com.ridebooking.booking.service.LocationService;
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
 * Quản trị địa điểm.
 *
 * <p>Yêu cầu {@code ADMIN} được khai ở {@code SecurityConfig} (rule
 * {@code /api/admin/**}) và nhắc lại bằng {@code @PreAuthorize} — không phải thừa:
 * đổi đường dẫn sau này mà quên rule trong SecurityConfig thì vẫn còn lớp bảo vệ
 * thứ hai.
 */
@RestController
@RequestMapping("/api/admin/locations")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "3. Admin - Địa điểm", description = "Thêm, sửa, xoá tỉnh/thành phố")
public class AdminLocationController {

    private final LocationService locationService;

    public AdminLocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @Operation(summary = "Danh sách địa điểm")
    @GetMapping
    public List<LocationResponse> list() {
        return locationService.listAll();
    }

    @Operation(summary = "Thêm địa điểm")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã thêm"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không phải admin")})
    @PostMapping
    public ResponseEntity<LocationResponse> create(@Valid @RequestBody LocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(locationService.create(request));
    }

    @Operation(summary = "Sửa địa điểm")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã sửa"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy"),
            @ApiResponse(responseCode = "409", description = "Slug đã dùng")})
    @PutMapping("/{id}")
    public LocationResponse update(@PathVariable Integer id, @Valid @RequestBody LocationRequest request) {
        return locationService.update(id, request);
    }

    @Operation(summary = "Xoá địa điểm")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        locationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
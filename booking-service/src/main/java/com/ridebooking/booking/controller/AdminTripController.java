package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.TripRequest;
import com.ridebooking.booking.dto.TripResponse;
import com.ridebooking.booking.service.TripAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Trang Quản lý Chuyến xe của Admin (requirement.md). */
@RestController
@RequestMapping("/api/admin/trips")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "5. Admin - Chuyến xe", description = "Thêm, sửa, xoá chuyến xe")
public class AdminTripController {

    private final TripAdminService tripAdminService;

    public AdminTripController(TripAdminService tripAdminService) {
        this.tripAdminService = tripAdminService;
    }

    @Operation(summary = "Thêm chuyến xe",
            description = """
                    Ghế được SINH TỰ ĐỘNG theo loại xe, trong cùng transaction với
                    việc tạo chuyến. Admin không nhập tay danh sách ghế.

                    Sửa `busType` chỉ được khi chưa có ghế nào bán hoặc đang giữ.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã thêm, kèm số ghế đã sinh"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không phải admin"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy tuyến hoặc nhà xe")})
    @PostMapping
    public ResponseEntity<TripResponse> create(@Valid @RequestBody TripRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripAdminService.create(request));
    }

    @Operation(summary = "Sửa chuyến xe")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã sửa"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy"),
            @ApiResponse(responseCode = "409", description = "Chuyến đã có ghế bán hoặc đang giữ")})
    @PutMapping("/{id}")
    public TripResponse update(@PathVariable Integer id, @Valid @RequestBody TripRequest request) {
        return tripAdminService.update(id, request);
    }

    @Operation(summary = "Xoá chuyến xe",
            description = "Từ chối nếu chuyến đã bán ghế. Hãy đặt trạng thái CANCELLED thay vì xoá.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy"),
            @ApiResponse(responseCode = "409", description = "Chuyến đã bán ghế")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        tripAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

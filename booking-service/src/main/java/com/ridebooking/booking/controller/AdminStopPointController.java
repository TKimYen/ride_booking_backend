package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.StopPointRequest;
import com.ridebooking.booking.dto.StopPointResponse;
import com.ridebooking.booking.service.StopPointService;
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
 * Quản trị điểm dừng (Trang Quản lý Chuyến xe của Admin).
 *
 * <p>Chuyến nằm trong đường dẫn chứ không trong body: một điểm dừng luôn thuộc
 * đúng một chuyến, đặt trong body là mở đường cho việc gán nhầm chuyến rồi phải
 * truy vết để sửa.
 */
@RestController
@RequestMapping("/api/admin/trips/{tripId}/stop-points")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "5. Admin - Chuyến xe", description = "Điểm dừng của một chuyến")
public class AdminStopPointController {

    private final StopPointService stopPointService;

    public AdminStopPointController(StopPointService stopPointService) {
        this.stopPointService = stopPointService;
    }

    @Operation(summary = "Danh sách điểm dừng của chuyến")
    @GetMapping
    public List<StopPointResponse> list(@PathVariable Integer tripId) {
        return stopPointService.listByTrip(tripId);
    }

    @Operation(summary = "Thêm điểm dừng",
            description = """
                    Không nhận `stopOrder`: hệ thống tự xếp thứ tự, thêm vào cuối danh
                    sách cùng loại. Nhận tay thì dễ có hai điểm cùng số thứ tự.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã thêm"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chuyến"),
            @ApiResponse(responseCode = "409", description = "Chuyến không còn ACTIVE")})
    @PostMapping
    public ResponseEntity<StopPointResponse> create(@PathVariable Integer tripId,
                                                    @Valid @RequestBody StopPointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stopPointService.create(tripId, request));
    }

    @Operation(summary = "Sửa điểm dừng")
    @PutMapping("/{stopPointId}")
    public StopPointResponse update(@PathVariable Integer tripId,
                                    @PathVariable Integer stopPointId,
                                    @Valid @RequestBody StopPointRequest request) {
        // tripId trong đường dẫn phải khớp điểm dừng, nếu không Admin có thể sửa nhầm
        // sang một chuyến khác chỉ vì nhét id đúng vào URL.
        stopPointService.requireBelongsToTrip(stopPointId, tripId);
        return stopPointService.update(stopPointId, request);
    }

    @Operation(summary = "Xoá điểm dừng", description = "Các điểm còn lại được đánh lại số thứ tự từ 1.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy điểm dừng")})
    @DeleteMapping("/{stopPointId}")
    public ResponseEntity<Void> delete(@PathVariable Integer tripId, @PathVariable Integer stopPointId) {
        stopPointService.requireBelongsToTrip(stopPointId, tripId);
        stopPointService.delete(stopPointId);
        return ResponseEntity.noContent().build();
    }
}

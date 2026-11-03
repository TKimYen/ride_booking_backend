package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.AdminTripDetailResponse;
import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.TripCancellationResponse;
import com.ridebooking.booking.dto.TripCompletionResponse;
import com.ridebooking.booking.dto.TripRequest;
import com.ridebooking.booking.dto.TripResponse;
import com.ridebooking.booking.dto.TripSearchItemResponse;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.service.TripAdminService;
import com.ridebooking.booking.service.TripLifecycleService;
import com.ridebooking.booking.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trang Quản lý Chuyến xe của Admin (requirement.md). */
@RestController
@RequestMapping("/api/admin/trips")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "5. Admin - Chuyến xe", description = "Xem, thêm, sửa, xoá, huỷ và hoàn thành chuyến xe")
public class AdminTripController {

    private final TripAdminService tripAdminService;
    private final TripService tripService;
    private final TripLifecycleService tripLifecycleService;

    public AdminTripController(TripAdminService tripAdminService,
                               TripService tripService,
                               TripLifecycleService tripLifecycleService) {
        this.tripAdminService = tripAdminService;
        this.tripService = tripService;
        this.tripLifecycleService = tripLifecycleService;
    }

    @Operation(summary = "Danh sách chuyến xe",
            description = "Thấy chuyến ở mọi trạng thái, khởi hành muộn nhất lên trước. Bộ lọc nào bỏ trống thì không áp.")
    @GetMapping
    public PageResponse<TripSearchItemResponse> list(
            @Parameter(description = "ACTIVE | CANCELLED | COMPLETED") @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) Integer originId,
            @RequestParam(required = false) Integer destinationId,
            @RequestParam(required = false) Integer companyId,
            @Parameter(description = "Khởi hành từ ngày", example = "2026-11-01") @RequestParam(required = false) LocalDate from,
            @Parameter(description = "Khởi hành đến ngày, tính cả ngày đó", example = "2026-11-30") @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tối đa 100") @RequestParam(defaultValue = "10") int size) {
        return tripService.listForAdmin(status, originId, destinationId, companyId, from, to, page, size);
    }

    @Operation(summary = "Chi tiết chuyến xe", description = "Kèm số ghế và số đơn theo trạng thái.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy")})
    @GetMapping("/{id}")
    public AdminTripDetailResponse getById(@PathVariable Integer id) {
        return tripService.getForAdmin(id);
    }

    @Operation(summary = "Hoàn thành chuyến thủ công",
            description = """
                    Chuyến thành COMPLETED, đơn PAID thành COMPLETED. Job cũng tự làm việc này
                    khi chuyến qua giờ đến; API này dành cho lúc Admin không muốn chờ.
                    Gọi lại trên chuyến đã hoàn thành không lỗi.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã hoàn thành"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy"),
            @ApiResponse(responseCode = "409", description = "Chuyến đã huỷ hoặc chưa khởi hành")})
    @PostMapping("/{id}/complete")
    public TripCompletionResponse complete(@PathVariable Integer id) {
        return tripLifecycleService.complete(id);
    }

    @Operation(summary = "Huỷ chuyến",
            description = """
                    Trả mọi ghế về AVAILABLE, huỷ đơn PENDING và PAID, trả về danh sách đơn
                    cần hoàn tiền. Hệ thống CHƯA tự hoàn tiền: Admin hoàn trên cổng thanh toán.
                    Gọi lại trên chuyến đã huỷ không lỗi và vẫn trả đủ danh sách.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã huỷ"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy"),
            @ApiResponse(responseCode = "409", description = "Chuyến đã hoàn thành")})
    @PostMapping("/{id}/cancel")
    public TripCancellationResponse cancel(@PathVariable Integer id) {
        return tripLifecycleService.cancel(id);
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

    @Operation(summary = "Sửa chuyến xe",
            description = "Không đổi được trạng thái ở đây; dùng `/cancel` hoặc `/complete`. Bỏ trống `status` thì giữ nguyên.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã sửa"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy"),
            @ApiResponse(responseCode = "409", description = "Chuyến đã có ghế bán hoặc đang giữ, hoặc request đòi đổi trạng thái")})
    @PutMapping("/{id}")
    public TripResponse update(@PathVariable Integer id, @Valid @RequestBody TripRequest request) {
        return tripAdminService.update(id, request);
    }

    @Operation(summary = "Xoá chuyến xe",
            description = "Từ chối nếu chuyến đã có đơn. Hãy dùng `/cancel` thay vì xoá.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy"),
            @ApiResponse(responseCode = "409", description = "Chuyến đã có đơn hoặc ghế đang được giữ")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        tripAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

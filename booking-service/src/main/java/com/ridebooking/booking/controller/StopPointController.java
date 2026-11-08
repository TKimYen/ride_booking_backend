package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.StopPointResponse;
import com.ridebooking.booking.service.StopPointService;
import io.swagger.v3.oas.annotations.Operation;
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
 * Điểm dừng công khai — Trang chi tiết chuyến.
 *
 * <p>Điểm đón xếp trước điểm trả, mỗi nhóm theo thứ tự dọc tuyến, nên frontend vẽ
 * thẳng theo mảng trả về.
 */
@RestController
@RequestMapping("/api/stop-points")
@Tag(name = "2. Ghế", description = "Điểm đón và điểm trả của một chuyến")
public class StopPointController {

    private final StopPointService stopPointService;

    public StopPointController(StopPointService stopPointService) {
        this.stopPointService = stopPointService;
    }

    @Operation(summary = "Tên điểm đón / trả để lọc (Trang Danh sách kết quả)",
            description = """
                    Tên khác nhau, sắp theo ABC, của các chuyến đang bán khớp tuyến và ngày.
                    Không có `travelDate` thì lấy mọi chuyến chưa khởi hành.
                    """)
    @GetMapping("/options")
    public com.ridebooking.booking.dto.StopPointOptionsResponse options(
            @RequestParam(required = false) Integer originId,
            @RequestParam(required = false) Integer destinationId,
            @RequestParam(required = false) java.time.LocalDate travelDate) {
        return stopPointService.options(originId, destinationId, travelDate);
    }

    @Operation(summary = "Điểm dừng của một chuyến",
            description = """
                    Danh sách điểm đón và điểm trả theo thứ tự dọc tuyến.
                    `stopOrder` đánh số từ 1 và không có chỗ trống giữa hai điểm liền kề.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chuyến")})
    @GetMapping("/trip/{tripId}")
    public List<StopPointResponse> listByTrip(@PathVariable Integer tripId) {
        return stopPointService.listByTrip(tripId);
    }
}

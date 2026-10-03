package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.SeatMapResponse;
import com.ridebooking.booking.dto.TripSeatResponse;
import com.ridebooking.booking.service.TripSeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sơ đồ ghế — cho Trang Chọn chỗ & Thanh toán (Thành viên 3).
 *
 * <p>Giai đoạn 1 mới chỉ ĐỌC. Endpoint giữ ghế (POST) và thanh toán thuộc Giai đoạn 2;
 * xem db.md mục C.
 */
@RestController
@RequestMapping("/api/seats")
@Tag(name = "2. Ghế", description = "Sơ đồ ghế của một chuyến")
public class SeatController {

    private final TripSeatService tripSeatService;

    public SeatController(TripSeatService tripSeatService) {
        this.tripSeatService = tripSeatService;
    }

    @Operation(summary = "Sơ đồ ghế của một chuyến",
            description = """
                    Trả về ghế theo thứ tự tầng, rồi hàng, rồi cột — frontend vẽ
                    thẳng theo thứ tự mảng, không cần sắp xếp lại.

                    `totalPrice` của mỗi ghế đã cộng sẵn `extraPrice` vào giá chuyến,
                    nên frontend không tự tính lại (tính sai là ra hoá đơn sai).

                    `holdSecondsRemaining` là số giây còn lại của ghế sắp hết giữ;
                    dùng cho đồng hồ đếm ngược 10 phút trên header.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chuyến")})
    @GetMapping("/trip/{tripId}")
    public SeatMapResponse seatMap(@PathVariable Integer tripId) {
        return tripSeatService.seatMap(tripId);
    }

    @Operation(summary = "Danh sách ghế, không kèm thống kê",
            description = "Dùng cho màn hình quản trị, nơi không cần số liệu tổng.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chuyến")})
    @GetMapping("/trip/{tripId}/list")
    public List<TripSeatResponse> list(@PathVariable Integer tripId) {
        return tripSeatService.listSeats(tripId);
    }
}

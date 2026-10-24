package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.ReviewResponse;
import com.ridebooking.booking.dto.ReviewSummaryResponse;
import com.ridebooking.booking.security.SecurityUtils;
import com.ridebooking.booking.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đánh giá nhà xe — phần đánh giá trên Trang chi tiết chuyến.
 *
 * <p>Endpoint này PUBLIC: khách chưa đăng nhập vẫn xem được đánh giá, và đó là
 * lý do người dùng tìm được nhà xe nào tốt. Nếu đã đăng nhập thì mỗi dòng có
 * {@code mine = true} cho chính đánh giá của họ.
 */
@RestController
@RequestMapping("/api/reviews")
@Tag(name = "6. Đánh giá", description = "Đánh giá nhà xe sau khi đi")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Đánh giá của một chuyến",
            description = """
                    Mới nhất trước. Xem được không cần đăng nhập.

                    Tên người đánh giá lấy từ user-service theo lô, tối đa một lần gọi cho
                    cả trang. Nếu user-service không trả lời, đánh giá vẫn hiện với tên
                    "Người dùng" — không mất dữ liệu vì một service khác chậm.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chuyến")})
    @GetMapping("/trip/{tripId}")
    public PageResponse<ReviewResponse> listByTrip(
            @PathVariable Integer tripId,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số dòng mỗi trang, tối đa 100")
            @RequestParam(defaultValue = "10") int size) {
        return reviewService.listByTrip(tripId, page, size, SecurityUtils.currentUserIdOrNull());
    }

    @Operation(summary = "Điểm trung bình và phân bố sao của chuyến",
            description = """
                    Chuyến chưa có đánh giá thì `averageRating` là null và `totalReviews`
                    bằng 0, không phải lỗi — frontend hiện "Chưa có đánh giá nào".

                    `ratingBreakdown` luôn có đủ 5 mức, mức chưa có lượt đánh giá thì
                    bằng 0, nên thanh sao luôn cùng độ dài.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chuyến")})
    @GetMapping("/trip/{tripId}/summary")
    public ReviewSummaryResponse summary(@PathVariable Integer tripId) {
        return reviewService.summary(tripId);
    }
}
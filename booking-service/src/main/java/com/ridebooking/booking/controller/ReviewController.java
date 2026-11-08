package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.ReviewRequest;
import com.ridebooking.booking.dto.ReviewResponse;
import com.ridebooking.booking.dto.ReviewSummaryResponse;
import com.ridebooking.booking.dto.ReviewUpdateRequest;
import com.ridebooking.booking.security.SecurityUtils;
import com.ridebooking.booking.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đánh giá nhà xe [3.4].
 *
 * <p>Các API GET (trừ {@code /me}) PUBLIC: khách chưa đăng nhập vẫn xem được
 * đánh giá, và đó là lý do người dùng tìm được nhà xe nào tốt. Nếu đã đăng nhập
 * thì mỗi dòng có {@code mine = true} cho chính đánh giá của họ.
 *
 * <p>Viết / sửa / xoá và {@code /me} bắt buộc đăng nhập. Admin ẩn đánh giá ở
 * {@link AdminReviewController}.
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

    @Operation(summary = "Đánh giá của một nhà xe (tab Đánh giá trên Trang chi tiết)",
            description = """
                    Gộp đánh giá của mọi chuyến thuộc nhà xe, mới nhất trước, không gồm
                    đánh giá Admin đã ẩn. `rating` lọc theo một mức sao (1..5); giá trị
                    khác coi như không lọc.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhà xe")})
    @GetMapping("/company/{companyId}")
    public PageResponse<ReviewResponse> listByCompany(
            @PathVariable Integer companyId,
            @Parameter(description = "Chỉ lấy đánh giá có số sao này") @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số dòng mỗi trang, tối đa 100")
            @RequestParam(defaultValue = "10") int size) {
        return reviewService.listByCompany(companyId, rating, page, size, SecurityUtils.currentUserIdOrNull());
    }

    @Operation(summary = "Điểm trung bình và phân bố sao của nhà xe",
            description = "Giống `/trip/{tripId}/summary` nhưng tính trên mọi chuyến của nhà xe.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhà xe")})
    @GetMapping("/company/{companyId}/summary")
    public ReviewSummaryResponse companySummary(@PathVariable Integer companyId) {
        return reviewService.companySummary(companyId);
    }

    @Operation(summary = "Đánh giá của tôi",
            description = "Gồm cả đánh giá đã bị Admin ẩn (`hidden = true`) để người viết biết vì sao không thấy nó trên trang.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")})
    @GetMapping("/me")
    public PageResponse<ReviewResponse> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviewService.listByUser(SecurityUtils.currentUserIdOrThrow(), page, size);
    }

    @Operation(summary = "Viết đánh giá",
            description = """
                    Chỉ cho đơn của chính mình và đơn đã đi (`COMPLETED`). Mỗi đơn một đánh
                    giá; một người chỉ đánh giá một chuyến một lần.
                    """)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo"),
            @ApiResponse(responseCode = "400", description = "Số sao ngoài 1..5 hoặc bình luận quá 1000 ký tự"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn của bạn"),
            @ApiResponse(responseCode = "409", description = "Đơn chưa đi, hoặc đã đánh giá rồi")})
    @PostMapping
    public ResponseEntity<ReviewResponse> create(@Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(SecurityUtils.currentUserIdOrThrow(), request));
    }

    @Operation(summary = "Sửa đánh giá của mình")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã cập nhật"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không phải đánh giá của bạn"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đánh giá")})
    @PutMapping("/{id}")
    public ReviewResponse update(@PathVariable Integer id, @Valid @RequestBody ReviewUpdateRequest request) {
        return reviewService.update(SecurityUtils.currentUserIdOrThrow(), id, request);
    }

    @Operation(summary = "Xoá đánh giá", description = "Chủ đánh giá hoặc Admin.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã xoá"),
            @ApiResponse(responseCode = "403", description = "Không phải đánh giá của bạn"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đánh giá")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        reviewService.delete(SecurityUtils.currentUserIdOrThrow(), SecurityUtils.isAdmin(), id);
        return ResponseEntity.noContent().build();
    }
}
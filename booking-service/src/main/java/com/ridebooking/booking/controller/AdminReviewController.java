package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.ReviewResponse;
import com.ridebooking.booking.dto.ReviewVisibilityRequest;
import com.ridebooking.booking.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin kiểm duyệt đánh giá: xem cả đánh giá đã ẩn, ẩn / hiện lại. Xoá dùng {@code DELETE /api/reviews/{id}}. */
@RestController
@RequestMapping("/api/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "4. Admin - Đánh giá", description = "Kiểm duyệt đánh giá của khách")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Danh sách đánh giá (gồm cả đã ẩn)")
    @GetMapping
    public PageResponse<ReviewResponse> list(
            @Parameter(description = "id nhà xe") @RequestParam(required = false) Integer companyId,
            @Parameter(description = "Số sao 1..5") @RequestParam(required = false) Integer rating,
            @Parameter(description = "true: chỉ đã ẩn; false: chỉ đang hiện; bỏ trống: tất cả")
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviewService.listForAdmin(companyId, rating, hidden, page, size);
    }

    @Operation(summary = "Ẩn / hiện lại đánh giá",
            description = "Đánh giá ẩn không hiện trên trang và không tính vào điểm trung bình của nhà xe.")
    @PatchMapping("/{id}/hidden")
    public ReviewResponse setHidden(@PathVariable Integer id, @Valid @RequestBody ReviewVisibilityRequest request) {
        return reviewService.setHidden(id, request.hidden());
    }
}

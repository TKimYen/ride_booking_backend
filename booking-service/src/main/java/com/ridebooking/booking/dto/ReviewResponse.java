package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.Review;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** Một đánh giá của khách. */
@Schema(description = "Một đánh giá của khách")
public record ReviewResponse(

        @Schema(example = "5") Integer id,
        @Schema(example = "17") Integer tripId,
        @Schema(description = "id người đánh giá, lấy từ JWT", example = "1") Integer userId,
        @Schema(description = """
                Tên hiển thị. Lấy từ user-service; nếu hồ sơ không còn thì là
                "Người dùng" — đánh giá vẫn hiện, không mất vì user-service chết.
                """, example = "Nguyễn Anh Huy")
        String userDisplayName,
        @Schema(description = "Điểm 1..5", example = "5") Integer rating,
        @Schema(description = "Nội dung đánh giá", example = "Xe chạy đúng giờ, tài xế dễ thương") String comment,
        @Schema(example = "2026-10-05T20:15:00") LocalDateTime createdAt,
        @Schema(description = "true nếu người đang xem là chính người viết đánh giá này")
        boolean mine,
        @Schema(description = "Đơn đã đi mà đánh giá gắn vào; null với đánh giá seed cũ", example = "42")
        Integer bookingId,
        @Schema(example = "3") Integer companyId,
        @Schema(example = "G8 SAPA OPEN TOUR") String companyName,
        @Schema(example = "Hà Nội - Sa Pa") String routeName,
        @Schema(description = "Admin đã ẩn. Chỉ trang của tôi và trang admin mới thấy đánh giá ẩn")
        boolean hidden,
        @Schema(example = "2026-10-06T08:00:00") LocalDateTime updatedAt) {

    /** Cần {@code trip}, {@code trip.company}, {@code trip.route} đã nạp hoặc còn trong phiên. */
    public static ReviewResponse from(Review review, String displayName, Integer viewerId) {
        var trip = review.getTrip();
        return new ReviewResponse(
                review.getId(),
                trip.getId(),
                review.getUserId(),
                displayName,
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                viewerId != null && viewerId.equals(review.getUserId()),
                review.getBooking() == null ? null : review.getBooking().getId(),
                trip.getCompany().getId(),
                trip.getCompany().getName(),
                trip.getRoute().getName(),
                review.isHidden(),
                review.getUpdatedAt());
    }
}

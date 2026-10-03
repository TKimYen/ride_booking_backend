package com.ridebooking.booking.service;

import com.ridebooking.booking.client.UserProfileClient;
import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.ReviewResponse;
import com.ridebooking.booking.dto.ReviewSummaryResponse;
import com.ridebooking.booking.entity.Review;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.ReviewRepository;
import com.ridebooking.booking.repository.TripRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh sách và thống kê đánh giá — phần đánh giá nhà xe trên Trang chi tiết
 * (Thành viên 2).
 *
 * <p>Giai đoạn 1 chỉ ĐỌC. Viết đánh giá cần xác nhận khách đã đi chuyến đó, mà
 * điều đó lấy từ {@code bookings} — thuộc Giai đoạn 2.
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewRepository reviewRepository;
    private final TripRepository tripRepository;
    private final UserProfileClient userProfileClient;

    public ReviewService(ReviewRepository reviewRepository,
                         TripRepository tripRepository,
                         UserProfileClient userProfileClient) {
        this.reviewRepository = reviewRepository;
        this.tripRepository = tripRepository;
        this.userProfileClient = userProfileClient;
    }

    /**
     * Đánh giá của một chuyến, mới nhất trước.
     *
     * @param viewerId id người đang xem, để đánh dấu đánh giá của chính họ; null nếu khách vô danh
     */
    public PageResponse<ReviewResponse> listByTrip(Integer tripId, int page, int size, Integer viewerId) {
        requireTrip(tripId);
        Page<Review> result = reviewRepository.findByTripId(tripId,
                PageRequest.of(Math.max(page, 0), clampSize(size)));
        return PageResponse.from(result, toResponses(result.getContent(), viewerId));
    }

    /** Đánh giá của một người dùng, dùng cho trang "đánh giá của tôi". */
    public PageResponse<ReviewResponse> listByUser(Integer userId, int page, int size) {
        Page<Review> result = reviewRepository.findByUserIdWithTrip(userId,
                PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(result, toResponses(result.getContent(), userId));
    }

    /** Điểm trung bình và phân bố sao. Chuyến chưa có đánh giá thì trả 0 chứ không lỗi. */
    public ReviewSummaryResponse summary(Integer tripId) {
        requireTrip(tripId);

        // AVG của tập rỗng là NULL, đã COALESCE về 0 trong query.
        BigDecimal average = BigDecimal.ZERO;
        long total = 0L;
        List<Object[]> summary = reviewRepository.ratingSummary(tripId);
        if (!summary.isEmpty()) {
            // Không ép thẳng sang BigDecimal: Hibernate trả AVG trên cột Integer
            // là Double, ép trực tiếp sẽ ném ClassCastException và API thành 500.
            Object rawAverage = summary.get(0)[0];
            if (rawAverage instanceof Number number) {
                average = BigDecimal.valueOf(number.doubleValue());
            }
            total = ((Number) summary.get(0)[1]).longValue();
        }

        Map<Integer, Long> breakdown = new LinkedHashMap<>();
        for (Object[] row : reviewRepository.ratingBreakdown(tripId)) {
            breakdown.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return ReviewSummaryResponse.from(average, total,
                ReviewSummaryResponse.withAllLevels(breakdown));
    }

    /**
     * Gắn tên người đánh giá cho cả trang bằng MỘT lần gọi user-service.
     *
     * <p>Gọi từng người thì một trang 10 đánh giá là 10 lần gọi HTTP và 10 lần đọc
     * database ở service bên kia — trang danh sách đánh giá sẽ chậm hơn cả trang
     * chi tiết chuyến.
     */
    private List<ReviewResponse> toResponses(List<Review> reviews, Integer viewerId) {
        if (reviews.isEmpty()) {
            return List.of();
        }
        Map<Integer, String> names = userProfileClient.fetchDisplayNames(
                reviews.stream().map(Review::getUserId).toList());
        return reviews.stream()
                .map(review -> ReviewResponse.from(review,
                        names.getOrDefault(review.getUserId(), "Người dùng"), viewerId))
                .toList();
    }

    private Trip requireTrip(Integer tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến xe với id " + tripId));
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
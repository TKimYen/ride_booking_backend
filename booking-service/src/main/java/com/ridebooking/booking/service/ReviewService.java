package com.ridebooking.booking.service;

import com.ridebooking.booking.client.UserProfileClient;
import com.ridebooking.booking.dto.CompanyRatingResponse;
import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.ReviewRequest;
import com.ridebooking.booking.dto.ReviewResponse;
import com.ridebooking.booking.dto.ReviewSummaryResponse;
import com.ridebooking.booking.dto.ReviewUpdateRequest;
import com.ridebooking.booking.entity.Booking;
import com.ridebooking.booking.entity.Review;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.BookingRepository;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.ReviewRepository;
import com.ridebooking.booking.repository.TripRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đánh giá nhà xe [3.4].
 *
 * <p>Quy tắc viết đánh giá:
 * <ul>
 *   <li>chỉ viết cho đơn của chính mình và đơn đã {@code COMPLETED} ("Đã đi");</li>
 *   <li>mỗi đơn một đánh giá, mỗi người một đánh giá cho một chuyến (UNIQUE ở DB);</li>
 *   <li>chỉ chủ đánh giá được sửa; chủ đánh giá hoặc Admin được xoá;</li>
 *   <li>Admin ẩn đánh giá vi phạm thay vì xoá — đánh giá ẩn không hiện và không
 *       tính vào điểm trung bình, nhưng chủ đánh giá vẫn thấy nó ở trang của mình.</li>
 * </ul>
 *
 * <p>Điểm nhà xe là trung bình mọi đánh giá không ẩn của các chuyến thuộc nhà xe đó.
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String FALLBACK_NAME = "Người dùng";

    private final ReviewRepository reviewRepository;
    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final BusCompanyRepository busCompanyRepository;
    private final UserProfileClient userProfileClient;

    public ReviewService(ReviewRepository reviewRepository,
                         TripRepository tripRepository,
                         BookingRepository bookingRepository,
                         BusCompanyRepository busCompanyRepository,
                         UserProfileClient userProfileClient) {
        this.reviewRepository = reviewRepository;
        this.tripRepository = tripRepository;
        this.bookingRepository = bookingRepository;
        this.busCompanyRepository = busCompanyRepository;
        this.userProfileClient = userProfileClient;
    }

    // ------------------------------------------------------------------ đọc

    /**
     * Đánh giá của một chuyến, mới nhất trước.
     *
     * @param viewerId id người đang xem, để đánh dấu đánh giá của chính họ; null nếu khách vô danh
     */
    public PageResponse<ReviewResponse> listByTrip(Integer tripId, int page, int size, Integer viewerId) {
        requireTrip(tripId);
        Page<Review> result = reviewRepository.findByTripId(tripId, pageOf(page, size));
        return PageResponse.from(result, toResponses(result.getContent(), viewerId));
    }

    /** Đánh giá không ẩn của một nhà xe; {@code rating} null là mọi mức sao. */
    public PageResponse<ReviewResponse> listByCompany(Integer companyId, Integer rating,
                                                      int page, int size, Integer viewerId) {
        requireCompany(companyId);
        Page<Review> result = reviewRepository.findVisibleByCompany(companyId, validRatingOrNull(rating),
                pageOf(page, size));
        return PageResponse.from(result, toResponses(result.getContent(), viewerId));
    }

    /** Đánh giá của một người dùng, gồm cả đánh giá đã bị ẩn. */
    public PageResponse<ReviewResponse> listByUser(Integer userId, int page, int size) {
        Page<Review> result = reviewRepository.findByUserIdWithTrip(userId, pageOf(page, size));
        return PageResponse.from(result, toResponses(result.getContent(), userId));
    }

    /** Admin: mọi đánh giá, kể cả đã ẩn. */
    public PageResponse<ReviewResponse> listForAdmin(Integer companyId, Integer rating, Boolean hidden,
                                                     int page, int size) {
        Page<Review> result = reviewRepository.findForAdmin(companyId, validRatingOrNull(rating), hidden,
                pageOf(page, size));
        return PageResponse.from(result, toResponses(result.getContent(), null));
    }

    /** Điểm trung bình và phân bố sao của chuyến. Chuyến chưa có đánh giá thì trả 0 chứ không lỗi. */
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
        return ReviewSummaryResponse.from(average, total,
                ReviewSummaryResponse.withAllLevels(toBreakdown(reviewRepository.ratingBreakdown(tripId))));
    }

    /** Điểm trung bình và phân bố sao của nhà xe — phần đầu tab "Đánh giá" trên Trang chi tiết. */
    public ReviewSummaryResponse companySummary(Integer companyId) {
        requireCompany(companyId);
        Map<Integer, Long> breakdown = toBreakdown(reviewRepository.ratingBreakdownByCompany(companyId));
        long total = breakdown.values().stream().mapToLong(Long::longValue).sum();
        long weighted = breakdown.entrySet().stream()
                .mapToLong(e -> e.getKey() * e.getValue()).sum();
        BigDecimal average = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(weighted).divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP);
        return ReviewSummaryResponse.from(average, total, ReviewSummaryResponse.withAllLevels(breakdown));
    }

    /**
     * Điểm của nhiều nhà xe trong một câu truy vấn — dùng cho kết quả tìm kiếm và
     * hồ sơ nhà xe. Nhà xe chưa có đánh giá thì có mặt trong map với {@link CompanyRatingResponse#NONE}.
     */
    public Map<Integer, CompanyRatingResponse> ratingsOf(Collection<Integer> companyIds) {
        Map<Integer, CompanyRatingResponse> ratings = new HashMap<>();
        if (companyIds.isEmpty()) {
            return ratings;
        }
        for (Object[] row : reviewRepository.ratingByCompanyIds(companyIds)) {
            ratings.put((Integer) row[0], CompanyRatingResponse.of((Number) row[1], ((Number) row[2]).longValue()));
        }
        companyIds.forEach(id -> ratings.putIfAbsent(id, CompanyRatingResponse.NONE));
        return ratings;
    }

    // ------------------------------------------------------------------ ghi

    /**
     * Viết đánh giá cho một đơn đã đi.
     *
     * <p>Đơn của người khác trả 404 chứ không 403: không để lộ id đơn nào đang tồn tại.
     */
    @Transactional
    public ReviewResponse create(Integer userId, ReviewRequest request) {
        Booking booking = bookingRepository.findByIdAndUserId(request.bookingId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy đơn với id " + request.bookingId()));
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ConflictException("Bạn chỉ đánh giá được chuyến mình đã đi");
        }
        Integer tripId = booking.getTrip().getId();
        if (reviewRepository.existsByBookingId(booking.getId())
                || reviewRepository.existsByUserIdAndTripId(userId, tripId)) {
            throw new ConflictException("Bạn đã đánh giá chuyến này, hãy sửa đánh giá cũ");
        }

        Review review = new Review();
        review.setUserId(userId);
        review.setBooking(booking);
        review.setTrip(booking.getTrip());
        review.setCompany(booking.getTrip().getCompany());
        review.setRating(request.rating());
        review.setComment(normalizeComment(request.comment()));
        try {
            review = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            // Hai tab bấm "Gửi" cùng lúc: kiểm tra ở trên đều qua, UNIQUE ở DB chặn lần hai.
            throw new ConflictException("Bạn đã đánh giá chuyến này, hãy sửa đánh giá cũ");
        }
        return toResponse(review, userId);
    }

    /** Chủ đánh giá sửa điểm và bình luận. Admin không sửa nội dung của khách, chỉ ẩn. */
    @Transactional
    public ReviewResponse update(Integer userId, Integer reviewId, ReviewUpdateRequest request) {
        Review review = requireReview(reviewId);
        if (!review.getUserId().equals(userId)) {
            throw new AccessDeniedException("Bạn không thể sửa đánh giá của người khác");
        }
        review.setRating(request.rating());
        review.setComment(normalizeComment(request.comment()));
        return toResponse(reviewRepository.saveAndFlush(review), userId);
    }

    /** Chủ đánh giá hoặc Admin xoá. Xoá xong thì đơn đó lại được viết đánh giá mới. */
    @Transactional
    public void delete(Integer userId, boolean isAdmin, Integer reviewId) {
        Review review = requireReview(reviewId);
        if (!isAdmin && !review.getUserId().equals(userId)) {
            throw new AccessDeniedException("Bạn không thể xoá đánh giá của người khác");
        }
        reviewRepository.delete(review);
    }

    /** Admin ẩn / hiện lại một đánh giá. Gọi lại với cùng giá trị không lỗi. */
    @Transactional
    public ReviewResponse setHidden(Integer reviewId, boolean hidden) {
        Review review = requireReview(reviewId);
        review.setHidden(hidden);
        return toResponse(reviewRepository.saveAndFlush(review), null);
    }

    /** id các đơn trong danh sách đã có đánh giá. */
    public java.util.Set<Integer> reviewedBookingIds(Collection<Integer> bookingIds) {
        if (bookingIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(reviewRepository.findReviewedBookingIds(bookingIds));
    }

    // ------------------------------------------------------------------ nội bộ

    private ReviewResponse toResponse(Review review, Integer viewerId) {
        return toResponses(List.of(review), viewerId).getFirst();
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
                reviews.stream().map(Review::getUserId).distinct().toList());
        return reviews.stream()
                .map(review -> ReviewResponse.from(review,
                        names.getOrDefault(review.getUserId(), FALLBACK_NAME), viewerId))
                .toList();
    }

    private static Map<Integer, Long> toBreakdown(List<Object[]> rows) {
        Map<Integer, Long> breakdown = new LinkedHashMap<>();
        for (Object[] row : rows) {
            breakdown.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return breakdown;
    }

    /** Bình luận toàn khoảng trắng lưu là null, để trang hiện "Không có bình luận". */
    private static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Mức sao ngoài 1..5 coi như không lọc — đây là tham số tiện ích của trang. */
    private static Integer validRatingOrNull(Integer rating) {
        return rating != null && rating >= 1 && rating <= 5 ? rating : null;
    }

    private Review requireReview(Integer reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá với id " + reviewId));
    }

    private void requireTrip(Integer tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new ResourceNotFoundException("Không tìm thấy chuyến xe với id " + tripId);
        }
    }

    private void requireCompany(Integer companyId) {
        if (!busCompanyRepository.existsById(companyId)) {
            throw new ResourceNotFoundException("Không tìm thấy nhà xe với id " + companyId);
        }
    }

    private static PageRequest pageOf(int page, int size) {
        int safeSize = size < 1 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize);
    }
}

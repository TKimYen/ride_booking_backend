package com.ridebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ridebooking.booking.dto.CompanyRatingResponse;
import com.ridebooking.booking.dto.HoldSeatsRequest;
import com.ridebooking.booking.dto.PaymentRequest;
import com.ridebooking.booking.dto.ReviewRequest;
import com.ridebooking.booking.dto.ReviewResponse;
import com.ridebooking.booking.dto.ReviewSummaryResponse;
import com.ridebooking.booking.dto.ReviewUpdateRequest;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/** API đánh giá [3.4], chạy thật trên H2. */
@SpringBootTest
@Transactional
class ReviewFlowTest {

    private static final int USER = 201;
    private static final int OTHER_USER = 202;

    @Autowired private ReviewService reviewService;
    @Autowired private BookingService bookingService;
    @Autowired private TripLifecycleService tripLifecycleService;
    @Autowired private SeatGenerator seatGenerator;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripSeatRepository tripSeatRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private BusCompanyRepository busCompanyRepository;
    @Autowired private LocationRepository locationRepository;

    private Route route;
    private BusCompany company;

    @BeforeEach
    void setUp() {
        Location from = locationRepository.save(location("Điểm đi review", "diem-di-review"));
        Location to = locationRepository.save(location("Điểm đến review", "diem-den-review"));
        BusCompany newCompany = new BusCompany();
        newCompany.setName("Nhà xe review");
        company = busCompanyRepository.save(newCompany);

        Route newRoute = new Route();
        newRoute.setName("Tuyến review");
        newRoute.setOrigin(from);
        newRoute.setDestination(to);
        route = routeRepository.save(newRoute);
    }

    @Test
    @DisplayName("Viết đánh giá cho đơn đã đi: có trong danh sách nhà xe và điểm trung bình")
    void createOnCompletedBooking() {
        Integer booking = completedBooking(USER);

        ReviewResponse review = reviewService.create(USER, new ReviewRequest(booking, 4, "  Xe sạch  "));

        assertThat(review.bookingId()).isEqualTo(booking);
        assertThat(review.companyId()).isEqualTo(company.getId());
        assertThat(review.comment()).isEqualTo("Xe sạch");
        assertThat(review.mine()).isTrue();

        assertThat(reviewService.listByCompany(company.getId(), null, 0, 10, null).content())
                .extracting(ReviewResponse::id).containsExactly(review.id());
        ReviewSummaryResponse summary = reviewService.companySummary(company.getId());
        assertThat(summary.totalReviews()).isEqualTo(1);
        assertThat(summary.averageRating()).isEqualByComparingTo("4.0");
        assertThat(summary.ratingBreakdown()).containsEntry(4, 1L).containsEntry(5, 0L);
        assertThat(reviewService.reviewedBookingIds(List.of(booking))).containsExactly(booking);
    }

    @Test
    @DisplayName("Đơn đã thanh toán nhưng chưa đi thì không đánh giá được (409)")
    void rejectNotCompletedBooking() {
        Integer paid = paidBooking(USER, futureTrip());

        assertThatThrownBy(() -> reviewService.create(USER, new ReviewRequest(paid, 5, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Đơn của người khác trả 404, không lộ đơn có tồn tại")
    void rejectOthersBooking() {
        Integer booking = completedBooking(USER);

        assertThatThrownBy(() -> reviewService.create(OTHER_USER, new ReviewRequest(booking, 5, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Mỗi đơn chỉ một đánh giá")
    void rejectDuplicate() {
        Integer booking = completedBooking(USER);
        reviewService.create(USER, new ReviewRequest(booking, 5, null));

        assertThatThrownBy(() -> reviewService.create(USER, new ReviewRequest(booking, 3, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Chỉ chủ đánh giá được sửa")
    void onlyOwnerCanUpdate() {
        Integer review = reviewService.create(USER, new ReviewRequest(completedBooking(USER), 5, "Tốt")).id();

        assertThatThrownBy(() -> reviewService.update(OTHER_USER, review, new ReviewUpdateRequest(1, "x")))
                .isInstanceOf(AccessDeniedException.class);

        ReviewResponse updated = reviewService.update(USER, review, new ReviewUpdateRequest(2, "Trễ giờ"));
        assertThat(updated.rating()).isEqualTo(2);
        assertThat(updated.comment()).isEqualTo("Trễ giờ");
    }

    @Test
    @DisplayName("Người khác không xoá được; Admin xoá được, xoá xong đơn lại được đánh giá")
    void deletePermissions() {
        Integer booking = completedBooking(USER);
        Integer review = reviewService.create(USER, new ReviewRequest(booking, 5, null)).id();

        assertThatThrownBy(() -> reviewService.delete(OTHER_USER, false, review))
                .isInstanceOf(AccessDeniedException.class);

        reviewService.delete(OTHER_USER, true, review);
        assertThat(reviewService.listByUser(USER, 0, 10).content()).isEmpty();
        assertThat(reviewService.create(USER, new ReviewRequest(booking, 3, null)).rating()).isEqualTo(3);
    }

    @Test
    @DisplayName("Đánh giá bị ẩn không hiện, không tính điểm, nhưng chủ đánh giá vẫn thấy")
    void hiddenReviewExcludedFromPublicAndAverage() {
        Integer good = reviewService.create(USER, new ReviewRequest(completedBooking(USER), 5, null)).id();
        Integer spam = reviewService.create(OTHER_USER, new ReviewRequest(completedBooking(OTHER_USER), 1, "spam")).id();

        reviewService.setHidden(spam, true);

        assertThat(reviewService.listByCompany(company.getId(), null, 0, 10, null).content())
                .extracting(ReviewResponse::id).containsExactly(good);
        CompanyRatingResponse rating = reviewService.ratingsOf(List.of(company.getId())).get(company.getId());
        assertThat(rating.totalReviews()).isEqualTo(1);
        assertThat(rating.averageRating()).isEqualByComparingTo("5.0");

        assertThat(reviewService.listByUser(OTHER_USER, 0, 10).content())
                .singleElement().satisfies(r -> assertThat(r.hidden()).isTrue());
        assertThat(reviewService.listForAdmin(company.getId(), null, true, 0, 10).content())
                .extracting(ReviewResponse::id).containsExactly(spam);

        reviewService.setHidden(spam, false);
        assertThat(reviewService.companySummary(company.getId()).totalReviews()).isEqualTo(2);
    }

    @Test
    @DisplayName("Lọc đánh giá nhà xe theo số sao; nhà xe chưa có đánh giá có điểm null")
    void filterByRatingAndEmptyCompany() {
        reviewService.create(USER, new ReviewRequest(completedBooking(USER), 5, null));
        reviewService.create(OTHER_USER, new ReviewRequest(completedBooking(OTHER_USER), 3, null));

        assertThat(reviewService.listByCompany(company.getId(), 3, 0, 10, null).content())
                .extracting(ReviewResponse::rating).containsExactly(3);

        BusCompany empty = new BusCompany();
        empty.setName("Nhà xe chưa có đánh giá");
        empty = busCompanyRepository.save(empty);
        assertThat(reviewService.ratingsOf(List.of(empty.getId())).get(empty.getId()))
                .isEqualTo(CompanyRatingResponse.NONE);
        assertThat(reviewService.companySummary(empty.getId()).averageRating()).isNull();
    }

    // ------------------------------------------------------------ helpers

    /** Một đơn đã thanh toán của {@code userId} trên một chuyến mới, rồi cho chuyến chạy xong. */
    private Integer completedBooking(int userId) {
        Trip trip = futureTrip();
        Integer booking = paidBooking(userId, trip);
        trip.setDepartureTime(LocalDateTime.now().minusHours(10));
        trip.setArrivalTime(LocalDateTime.now().minusHours(2));
        tripRepository.saveAndFlush(trip);
        tripLifecycleService.completeArrivedTrips(LocalDateTime.now());
        return booking;
    }

    private Integer paidBooking(int userId, Trip trip) {
        Integer seatId = tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId()).getFirst().getId();
        Integer id = bookingService.holdSeats(userId, new HoldSeatsRequest(trip.getId(), List.of(seatId), null, null,
                "Khách đánh giá", "0901234567", "review@example.com")).id();
        bookingService.pay(userId, id, new PaymentRequest("VNPAY", null, null, null));
        return id;
    }

    private Trip futureTrip() {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setCompany(company);
        trip.setBusType(BusType.LIMOUSINE_29);
        trip.setDepartureTime(LocalDateTime.now().plusDays(7));
        trip.setArrivalTime(LocalDateTime.now().plusDays(7).plusHours(8));
        trip.setBasePrice(new BigDecimal("500000"));
        trip.setStatus(TripStatus.ACTIVE);
        trip = tripRepository.saveAndFlush(trip);
        tripSeatRepository.saveAll(seatGenerator.generate(trip));
        return trip;
    }

    private Location location(String name, String slug) {
        Location location = new Location();
        location.setName(name);
        location.setSlug(slug);
        return location;
    }
}

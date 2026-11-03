package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.TripCancellationResponse;
import com.ridebooking.booking.dto.TripCancellationResponse.RefundItem;
import com.ridebooking.booking.dto.TripCompletionResponse;
import com.ridebooking.booking.dto.TripResponse;
import com.ridebooking.booking.entity.Booking;
import com.ridebooking.booking.entity.Payment;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.enums.PaymentStatus;
import com.ridebooking.booking.enums.SeatStatus;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.BookingRepository;
import com.ridebooking.booking.repository.PaymentRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kết thúc vòng đời chuyến xe: hoàn thành hoặc huỷ.
 *
 * <p>Trạng thái chuyến đổi bằng câu UPDATE có điều kiện {@code status = ACTIVE},
 * nên hai thao tác chạy cùng lúc (Admin huỷ và job hoàn thành) chỉ có một bên thắng.
 * Cố ý không khoá dòng {@code trips}: giữ ghế khoá ghế rồi mới chèn đơn có khoá
 * ngoại tới chuyến, khoá theo thứ tự ngược lại sẽ dễ deadlock.
 */
@Service
@Transactional
public class TripLifecycleService {

    private final TripRepository tripRepository;
    private final TripSeatRepository tripSeatRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    public TripLifecycleService(TripRepository tripRepository,
                                TripSeatRepository tripSeatRepository,
                                BookingRepository bookingRepository,
                                PaymentRepository paymentRepository) {
        this.tripRepository = tripRepository;
        this.tripSeatRepository = tripSeatRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Việc của job định kỳ: chuyến đã đến nơi thành COMPLETED, đơn PAID của mọi
     * chuyến COMPLETED thành COMPLETED.
     *
     * <p>Câu thứ hai không giới hạn ở các chuyến vừa đổi: đơn giữ ghế trước giờ chạy
     * vẫn có thể thanh toán sau khi Admin bấm hoàn thành, và cần được quét ở lần sau.
     */
    public CompletionSweep completeArrivedTrips(LocalDateTime now) {
        int trips = tripRepository.completeArrivedTrips(TripStatus.COMPLETED, TripStatus.ACTIVE, now);
        int bookings = bookingRepository.completePaidBookingsOfCompletedTrips(
                BookingStatus.COMPLETED, BookingStatus.PAID, TripStatus.COMPLETED);
        return new CompletionSweep(trips, bookings);
    }

    /**
     * Admin bấm hoàn thành chuyến. Gọi lại trên chuyến đã hoàn thành không lỗi.
     *
     * <p>Chặn chuyến chưa khởi hành: đơn COMPLETED mở quyền đánh giá, và không có
     * luồng nào đưa đơn từ COMPLETED về lại PAID nếu Admin bấm nhầm.
     */
    public TripCompletionResponse complete(Integer id) {
        LocalDateTime now = LocalDateTime.now();
        Trip trip = requireTrip(id);
        if (trip.getStatus() == TripStatus.CANCELLED) {
            throw new ConflictException("Chuyến đã huỷ, không thể hoàn thành");
        }
        if (trip.getDepartureTime().isAfter(now)) {
            throw new ConflictException("Chuyến chưa khởi hành, không thể hoàn thành");
        }
        if (trip.getStatus() == TripStatus.ACTIVE) {
            transition(id, TripStatus.COMPLETED, now);
        }
        int completed = bookingRepository.completePaidBookingsOfTrip(id, BookingStatus.COMPLETED, BookingStatus.PAID);
        return new TripCompletionResponse(TripResponse.from(requireTrip(id)), completed);
    }

    /**
     * Admin huỷ chuyến: trả mọi ghế, huỷ mọi đơn còn hiệu lực, liệt kê đơn cần hoàn
     * tiền. Gọi lại trên chuyến đã huỷ không lỗi và vẫn trả đủ danh sách hoàn tiền.
     *
     * <p>Thứ tự bước quyết định an toàn khi có người đang đặt vé cùng lúc:
     * <ol>
     *   <li>Đổi trạng thái chuyến trước. Luồng giữ ghế kiểm tra lại trạng thái sau khi
     *       đã khoá ghế, nên request đến sau bị từ chối.</li>
     *   <li>Khoá toàn bộ ghế, chờ các giao dịch giữ ghế và thanh toán đang chạy.</li>
     *   <li>Đọc đơn SAU khi có khoá, để thấy đơn vừa được các giao dịch đó ghi. Thanh
     *       toán đến sau thấy ghế không còn HOLDING và bị từ chối để tự hoàn tiền.</li>
     * </ol>
     */
    public TripCancellationResponse cancel(Integer id) {
        LocalDateTime now = LocalDateTime.now();
        Trip trip = requireTrip(id);
        if (trip.getStatus() == TripStatus.COMPLETED) {
            throw new ConflictException("Chuyến đã hoàn thành, không thể huỷ");
        }
        int cancelled = 0;
        int released = 0;
        if (trip.getStatus() == TripStatus.ACTIVE) {
            // Job chưa kịp quét thì chuyến vẫn ACTIVE dù xe đã tới nơi.
            if (trip.getArrivalTime().isBefore(now)) {
                throw new ConflictException("Chuyến đã hoàn thành, không thể huỷ");
            }
            transition(id, TripStatus.CANCELLED, now);

            List<TripSeat> seats = tripSeatRepository.findByTripIdForUpdate(id);
            for (TripSeat seat : seats) {
                if (seat.getStatus() != SeatStatus.AVAILABLE) {
                    seat.setStatus(SeatStatus.AVAILABLE);
                    seat.setHoldingUntil(null);
                    released++;
                }
            }
            List<Booking> active = bookingRepository.findByTripIdAndStatusIn(
                    id, List.of(BookingStatus.PENDING, BookingStatus.PAID));
            for (Booking booking : active) {
                booking.setStatus(BookingStatus.CANCELLED);
                booking.setCancelledAt(now);
            }
            cancelled = active.size();
            // Ép ghi ngay để truy vấn danh sách hoàn tiền bên dưới thấy trạng thái mới.
            bookingRepository.flush();
        }

        List<RefundItem> refunds = refundsOf(id);
        BigDecimal total = refunds.stream().map(RefundItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TripCancellationResponse(TripResponse.from(requireTrip(id)), cancelled, released, refunds, total);
    }

    /**
     * Đơn đã huỷ mà còn giao dịch SUCCESS, tức đã thu tiền và chưa hoàn. Khách không
     * tự huỷ được đơn PAID nên các đơn này chỉ có thể đến từ huỷ chuyến.
     */
    private List<RefundItem> refundsOf(Integer tripId) {
        Map<Integer, Booking> bookings = new LinkedHashMap<>();
        for (Booking booking : bookingRepository.findByTripIdAndStatusIn(tripId, List.of(BookingStatus.CANCELLED))) {
            bookings.put(booking.getId(), booking);
        }
        if (bookings.isEmpty()) {
            return List.of();
        }
        List<Payment> payments = paymentRepository.findByBookingIdInAndStatusOrderByCreatedAtDesc(
                bookings.keySet(), PaymentStatus.SUCCESS);
        return payments.stream()
                .map(payment -> {
                    Booking booking = bookings.get(payment.getBooking().getId());
                    return new RefundItem(booking.getId(), booking.getBookingCode(), booking.getContactName(),
                            booking.getContactPhone(), booking.getContactEmail(), payment.getAmount(),
                            payment.getPaymentGateway().name(), payment.getTransactionNo());
                })
                .toList();
    }

    private void transition(Integer id, TripStatus target, LocalDateTime now) {
        if (tripRepository.transitionStatus(id, TripStatus.ACTIVE, target, now) == 0) {
            throw new ConflictException("Trạng thái chuyến vừa thay đổi, vui lòng tải lại");
        }
    }

    private Trip requireTrip(Integer id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến xe với id " + id));
    }

    /** Số chuyến và số đơn job vừa chuyển sang COMPLETED. */
    public record CompletionSweep(int trips, int bookings) {
    }
}

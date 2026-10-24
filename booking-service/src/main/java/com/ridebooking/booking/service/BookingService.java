package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.BookingLookupRequest;
import com.ridebooking.booking.dto.BookingResponse;
import com.ridebooking.booking.dto.HoldSeatsRequest;
import com.ridebooking.booking.dto.InternalBookingResponse;
import com.ridebooking.booking.dto.InternalPaymentRequest;
import com.ridebooking.booking.dto.PageResponse;
import com.ridebooking.booking.dto.PaymentRequest;
import com.ridebooking.booking.entity.Booking;
import com.ridebooking.booking.entity.BookingDetail;
import com.ridebooking.booking.entity.Payment;
import com.ridebooking.booking.entity.StopPoint;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.BookingStatus;
import com.ridebooking.booking.enums.PaymentGateway;
import com.ridebooking.booking.enums.PaymentStatus;
import com.ridebooking.booking.enums.PointType;
import com.ridebooking.booking.enums.SeatStatus;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.exception.BadRequestException;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.BookingDetailRepository;
import com.ridebooking.booking.repository.BookingRepository;
import com.ridebooking.booking.repository.PaymentRepository;
import com.ridebooking.booking.repository.StopPointRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import com.ridebooking.booking.security.AuthenticatedUser;
import com.ridebooking.booking.security.SecurityUtils;
import com.ridebooking.booking.util.GuestTokens;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đặt vé, giữ ghế, thanh toán và huỷ đơn — Giai đoạn 2.
 *
 * <h2>Chống bán trùng ghế</h2>
 * Ràng buộc "một ghế chỉ thuộc một đơn đang hiệu lực" trải qua hai bảng
 * ({@code bookings} + {@code booking_details}) nên không thể biểu diễn bằng
 * UNIQUE. Chốt chặn duy nhất là khoá dòng {@code trip_seats} bằng
 * {@code SELECT ... FOR UPDATE} TRƯỚC khi đọc trạng thái và ghi.
 *
 * <p>Mọi luồng đổi trạng thái ghế — giữ, thanh toán, huỷ, hết hạn — đều đi qua
 * {@link TripSeatRepository#findAllByIdForUpdate}. Danh sách id được sắp tăng dần
 * trước khi khoá để hai giao dịch giành cùng bộ ghế theo cùng thứ tự, tránh
 * deadlock.
 *
 * <h2>Ghế HOLDING quá hạn</h2>
 * Một ghế đang HOLDING nhưng {@code holdingUntil} đã qua được coi là còn trống
 * ngay tại đây, không chờ job quét. Job chỉ là lưới an toàn dọn dẹp định kỳ, giúp
 * khách không phải chờ tới 1 phút để giành lại ghế vừa hết hạn.
 */
@Service
@Transactional(readOnly = true)
public class BookingService {

    private static final int MAX_SEATS_PER_BOOKING = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SUCCESS_RESPONSE_CODE = "00";

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final PaymentRepository paymentRepository;
    private final TripRepository tripRepository;
    private final TripSeatRepository tripSeatRepository;
    private final StopPointRepository stopPointRepository;
    private final long holdMinutes;

    public BookingService(BookingRepository bookingRepository,
                          BookingDetailRepository bookingDetailRepository,
                          PaymentRepository paymentRepository,
                          TripRepository tripRepository,
                          TripSeatRepository tripSeatRepository,
                          StopPointRepository stopPointRepository,
                          @Value("${app.booking.seat-hold-minutes:10}") long holdMinutes) {
        this.bookingRepository = bookingRepository;
        this.bookingDetailRepository = bookingDetailRepository;
        this.paymentRepository = paymentRepository;
        this.tripRepository = tripRepository;
        this.tripSeatRepository = tripSeatRepository;
        this.stopPointRepository = stopPointRepository;
        this.holdMinutes = holdMinutes;
    }


    /** Giữ ghế và tạo đơn PENDING của tài khoản trong CÙNG một transaction. */
    @Transactional
    public BookingResponse holdSeats(Integer userId, HoldSeatsRequest request) {
        return createBooking(userId, null, request);
    }

    /**
     * Giữ ghế cho khách vãng lai (không JWT). Đơn có {@code userId = NULL}; quyền
     * với đơn chứng minh bằng token trả về đúng MỘT lần trong {@code guestToken}.
     * Email bắt buộc vì đó là kênh duy nhất để gửi vé cho khách.
     */
    @Transactional
    public BookingResponse holdSeatsAsGuest(HoldSeatsRequest request) {
        if (request.contactEmail() == null || request.contactEmail().isBlank()) {
            throw new BadRequestException("Khách chưa đăng nhập phải nhập email để nhận vé");
        }
        String token = GuestTokens.newToken();
        return createBooking(null, GuestTokens.hash(token), request).withGuestToken(token);
    }

    private BookingResponse createBooking(Integer userId, String guestTokenHash, HoldSeatsRequest request) {
        LocalDateTime now = LocalDateTime.now();

        Trip trip = tripRepository.findById(request.tripId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chuyến xe với id " + request.tripId()));
        if (trip.getStatus() != TripStatus.ACTIVE) {
            throw new ConflictException("Chuyến xe này không còn nhận đặt vé");
        }
        // Chuyến đã chạy vẫn ACTIVE cho tới khi job hoàn tất quét tới, nên phải
        // chặn theo giờ đi chứ không chỉ theo trạng thái.
        if (!trip.getDepartureTime().isAfter(now)) {
            throw new ConflictException("Chuyến xe đã khởi hành, không thể đặt vé");
        }

        // distinct + sort: chống gửi trùng ghế và bảo đảm thứ tự khoá ổn định.
        List<Integer> seatIds = request.seatIds().stream()
                .distinct()
                .sorted()
                .toList();
        if (seatIds.size() > MAX_SEATS_PER_BOOKING) {
            throw new BadRequestException("Mỗi đơn chỉ được đặt tối đa " + MAX_SEATS_PER_BOOKING + " ghế");
        }

        List<TripSeat> seats = lockSeats(seatIds);
        for (TripSeat seat : seats) {
            if (!seat.getTrip().getId().equals(trip.getId())) {
                throw new BadRequestException("Ghế " + seat.getSeatName() + " không thuộc chuyến này");
            }
        }

        List<String> taken = seats.stream()
                .filter(seat -> isBlocked(seat, now))
                .map(TripSeat::getSeatName)
                .toList();
        if (!taken.isEmpty()) {
            throw new ConflictException("Ghế đã có người giữ hoặc đã bán: " + String.join(", ", taken));
        }

        StopPoint pickup = resolveStopPoint(request.pickupPointId(), trip.getId(), PointType.PICKUP, "điểm đón");
        StopPoint dropoff = resolveStopPoint(request.dropoffPointId(), trip.getId(), PointType.DROPOFF, "điểm trả");

        LocalDateTime expiresAt = now.plusMinutes(holdMinutes);

        // Đặt ghế HOLDING trước khi tạo đơn. Nếu bước sau lỗi, transaction cuộn
        // lại và ghế trở về AVAILABLE — không có khoảng thời gian ghế bị treo.
        for (TripSeat seat : seats) {
            seat.setStatus(SeatStatus.HOLDING);
            seat.setHoldingUntil(expiresAt);
        }
        tripSeatRepository.saveAll(seats);

        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setGuestTokenHash(guestTokenHash);
        booking.setTrip(trip);
        booking.setPickupPoint(pickup);
        booking.setDropoffPoint(dropoff);
        booking.setContactName(request.contactName().trim());
        booking.setContactPhone(request.contactPhone().trim());
        booking.setContactEmail(resolveContactEmail(request.contactEmail()));
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(now);
        booking.setExpiresAt(expiresAt);

        BigDecimal total = BigDecimal.ZERO;
        List<BookingDetail> details = new ArrayList<>();
        for (TripSeat seat : seats) {
            // Giá chốt tại đây và lưu vào booking_details.price.
            BigDecimal price = seat.effectivePrice(trip.getBasePrice());
            total = total.add(price);
            details.add(new BookingDetail(booking, seat, price));
        }
        booking.setSubtotalPrice(total);
        booking.setDiscountAmount(BigDecimal.ZERO);
        booking.setTotalPrice(total);
        bookingRepository.save(booking);
        bookingDetailRepository.saveAll(details);

        return BookingResponse.from(booking, details, null, now);
    }


    /**
     * Xác nhận thanh toán: ghế HOLDING -> BOOKED, đơn -> PAID.
     *
     * <p>Idempotent: gọi lại trên đơn đã PAID trả về kết quả cũ thay vì lỗi, để
     * nút "Thử lại" của client hoặc callback cổng gửi lặp không tạo giao dịch thứ
     * hai.
     */
    @Transactional
    public BookingResponse pay(Integer userId, Integer bookingId, PaymentRequest request) {
        return pay(userId, null, bookingId, request);
    }

    /** Như trên, cho cả khách vãng lai: {@code guestToken} là header {@code X-Guest-Token}. */
    @Transactional
    public BookingResponse pay(Integer userId, String guestToken, Integer bookingId, PaymentRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Booking booking = requireAccessible(bookingId, userId, guestToken, true);
        List<BookingDetail> details = bookingDetailRepository.findByBookingIdWithSeat(bookingId);

        if (booking.getStatus() == BookingStatus.PAID) {
            return BookingResponse.from(booking, details, latestSuccessfulPayment(bookingId), now);
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException("Đơn không ở trạng thái chờ thanh toán");
        }
        if (isExpired(booking, now)) {
            throw new ConflictException("Đơn đã hết hạn thanh toán, vui lòng đặt lại");
        }

        PaymentGateway gateway = parseGateway(request.gateway());
        boolean success = request.responseCode() == null
                || SUCCESS_RESPONSE_CODE.equals(request.responseCode().trim());

        if (!success) {
            paymentRepository.save(buildPayment(booking, gateway, request.transactionNo(),
                    request.bankCode(), request.responseCode(), PaymentStatus.FAILED, now));
            throw new ConflictException(
                    "Thanh toán không thành công (mã " + request.responseCode() + "), vui lòng thử lại");
        }

        String transactionNo = request.transactionNo() != null && !request.transactionNo().isBlank()
                ? request.transactionNo().trim()
                : generateTransactionNo(gateway);
        return settleSuccess(booking, details, gateway, transactionNo,
                request.bankCode(), SUCCESS_RESPONSE_CODE, now);
    }

    /**
     * Xác nhận thanh toán do payment-service gọi (webhook Stripe), KHÔNG qua JWT.
     *
     * <p>Khác {@link #pay(Integer, Integer, PaymentRequest)} ở chỗ không kiểm tra
     * quyền sở hữu — quyền nằm ở header {@code X-Internal-Key} do controller nội bộ
     * giữ. Idempotent: webhook Stripe có thể gửi lại nhiều lần cho cùng một
     * {@code payment_intent}, lần thứ hai trả về kết quả cũ thay vì tạo giao dịch
     * hoặc trả ghế lần nữa.
     */
    @Transactional
    public BookingResponse confirmPayment(Integer bookingId, InternalPaymentRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy đơn đặt vé với id " + bookingId));
        List<BookingDetail> details = bookingDetailRepository.findByBookingIdWithSeat(bookingId);

        if (booking.getStatus() == BookingStatus.PAID) {
            return BookingResponse.from(booking, details, latestSuccessfulPayment(bookingId), now);
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException("Đơn không ở trạng thái chờ thanh toán");
        }
        if (isExpired(booking, now)) {
            throw new ConflictException("Đơn đã hết hạn thanh toán, vui lòng đặt lại");
        }

        PaymentGateway gateway = parseGateway(request.gateway());
        String transactionNo = request.transactionNo() != null && !request.transactionNo().isBlank()
                ? request.transactionNo().trim()
                : generateTransactionNo(gateway);
        String responseCode = request.responseCode() == null || request.responseCode().isBlank()
                ? SUCCESS_RESPONSE_CODE
                : request.responseCode().trim();
        return settleSuccess(booking, details, gateway, transactionNo,
                request.bankCode(), responseCode, now);
    }

    /**
     * Phần dùng chung của mọi luồng "đã thu được tiền": ghế HOLDING -> BOOKED,
     * đơn -> PAID, ghi/cập nhật dòng {@code payments} SUCCESS.
     *
     * <p>Người gọi phải bảo đảm đơn đang PENDING, chưa hết hạn và tiền đã thu.
     * Ghế vẫn được kiểm tra lại dưới khoá dòng: nếu job đã trả ghế về AVAILABLE
     * thì ném xung đột để tầng trên hoàn tiền, không âm thầm bán ghế đã mất.
     */
    private BookingResponse settleSuccess(Booking booking, List<BookingDetail> details,
                                          PaymentGateway gateway, String transactionNo, String bankCode,
                                          String responseCode, LocalDateTime now) {
        Integer bookingId = booking.getId();
        List<Integer> seatIds = details.stream()
                .map(detail -> detail.getSeat().getId())
                .distinct()
                .sorted()
                .toList();
        List<TripSeat> seats = lockSeats(seatIds);
        for (TripSeat seat : seats) {
            if (seat.getStatus() != SeatStatus.HOLDING) {
                // Ghế đã bị job trả về hoặc người khác giành mất.
                throw new ConflictException(
                        "Ghế " + seat.getSeatName() + " không còn được giữ, vui lòng đặt lại");
            }
        }
        for (TripSeat seat : seats) {
            seat.setStatus(SeatStatus.BOOKED);
            seat.setHoldingUntil(null);
        }
        tripSeatRepository.saveAll(seats);

        booking.setStatus(BookingStatus.PAID);
        booking.setPaidAt(now);

        // (payment_gateway, transaction_no) là UNIQUE. Nếu mã đã tồn tại: nếu thuộc
        // đơn khác thì từ chối, nếu cùng đơn (ví dụ lần trước FAILED) thì cập nhật
        // thành SUCCESS thay vì chèn dòng thứ hai gây lỗi ràng buộc.
        Optional<Payment> existing = paymentRepository.findByPaymentGatewayAndTransactionNo(gateway, transactionNo);
        if (existing.isPresent() && !existing.get().getBooking().getId().equals(bookingId)) {
            throw new ConflictException("Mã giao dịch đã được dùng cho đơn khác");
        }
        Payment payment = existing.orElseGet(() -> buildPayment(booking, gateway, transactionNo,
                bankCode, responseCode, PaymentStatus.SUCCESS, now));
        payment.setAmount(booking.getTotalPrice());
        payment.setBankCode(bankCode);
        payment.setResponseCode(responseCode);
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);

        return BookingResponse.from(booking, details, payment, now);
    }

    /**
     * Dữ liệu tối thiểu payment-service cần để tạo phiên Stripe: mã đơn, số tiền,
     * trạng thái và hạn giữ ghế. Không lộ thông tin liên hệ hay ghế.
     */
    public InternalBookingResponse getForPayment(Integer bookingId, String guestToken) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy đơn đặt vé với id " + bookingId));
        return InternalBookingResponse.from(booking, guestCanAccess(booking, guestToken));
    }


    /**
     * Khách huỷ đơn chưa thanh toán: ghế HOLDING -> AVAILABLE, đơn -> CANCELLED.
     *
     * <p>Đơn đã PAID không huỷ ở đây được — hoàn tiền là luồng riêng. Đơn đã quá
     * hạn được coi là EXPIRED; không trả ghế ở đây vì ghế có thể đã được người
     * khác giữ lại, và job sẽ dọn đơn.
     */
    @Transactional
    public BookingResponse cancel(Integer userId, Integer bookingId) {
        return cancel(userId, null, bookingId);
    }

    /** Như trên, cho cả khách vãng lai: {@code guestToken} là header {@code X-Guest-Token}. */
    @Transactional
    public BookingResponse cancel(Integer userId, String guestToken, Integer bookingId) {
        LocalDateTime now = LocalDateTime.now();
        Booking booking = requireAccessible(bookingId, userId, guestToken, false);
        List<BookingDetail> details = bookingDetailRepository.findByBookingIdWithSeat(bookingId);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return BookingResponse.from(booking, details, latestSuccessfulPayment(bookingId), now);
        }
        if (booking.getStatus() == BookingStatus.PAID) {
            throw new ConflictException(
                    "Đơn đã thanh toán, không thể huỷ trực tuyến. Vui lòng liên hệ nhà xe để được hoàn tiền.");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException("Đơn không thể huỷ ở trạng thái hiện tại");
        }
        if (isExpired(booking, now)) {
            throw new ConflictException("Đơn đã hết hạn thanh toán");
        }

        List<Integer> seatIds = details.stream()
                .map(detail -> detail.getSeat().getId())
                .distinct()
                .sorted()
                .toList();
        List<TripSeat> seats = lockSeats(seatIds);
        for (TripSeat seat : seats) {
            // Chỉ trả ghế còn đang giữ. Nếu ghế đã BOOKED (không thể với đơn PENDING)
            // thì giữ nguyên, tránh phá đơn đã thanh toán.
            if (seat.getStatus() == SeatStatus.HOLDING) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHoldingUntil(null);
            }
        }
        tripSeatRepository.saveAll(seats);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        return BookingResponse.from(booking, details, null, now);
    }


    /** "Đơn hàng của tôi". {@code statuses} rỗng/null = tất cả trạng thái. */
    public PageResponse<BookingResponse> listMine(Integer userId, List<BookingStatus> statuses,
                                                  int page, int size) {
        Page<Booking> result = bookingRepository.findByUserIdAndStatusIn(
                userId,
                normalizeStatuses(statuses),
                PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        return toPage(result);
    }

    /** Chi tiết một đơn; chỉ chủ đơn hoặc Admin. */
    public BookingResponse getOne(Integer userId, Integer bookingId) {
        return getOne(userId, null, bookingId);
    }

    /** Chi tiết một đơn; chủ đơn, Admin, hoặc khách vãng lai có token đúng. */
    public BookingResponse getOne(Integer userId, String guestToken, Integer bookingId) {
        Booking booking = requireAccessible(bookingId, userId, guestToken, true);
        List<BookingDetail> details = bookingDetailRepository.findByBookingIdWithSeat(bookingId);
        return BookingResponse.from(booking, details, latestSuccessfulPayment(bookingId), LocalDateTime.now());
    }

    /**
     * Trang Tra cứu vé: mã vé + SĐT liên hệ.
     *
     * <p>Đơn của khách vãng lai được cấp token MỚI (token cũ hết hiệu lực) để khách
     * thanh toán/huỷ tiếp từ thiết bị khác. Đơn của tài khoản chỉ trả thông tin, muốn
     * thao tác thì đăng nhập. Sai mã hoặc sai SĐT đều trả cùng một thông báo, không
     * cho biết mã có tồn tại hay không.
     */
    @Transactional
    public BookingResponse lookup(BookingLookupRequest request) {
        Booking booking = findByCodeAndPhone(request);
        List<BookingDetail> details = bookingDetailRepository.findByBookingIdWithSeat(booking.getId());
        BookingResponse response = BookingResponse.from(
                booking, details, latestSuccessfulPayment(booking.getId()), LocalDateTime.now());
        if (!booking.isGuest()) {
            return response;
        }
        String token = GuestTokens.newToken();
        booking.setGuestTokenHash(GuestTokens.hash(token));
        return response.withGuestToken(token);
    }

    /**
     * Gắn đơn đặt lúc chưa đăng nhập vào tài khoản đang đăng nhập. Chứng minh quyền
     * bằng mã vé + SĐT, giống tra cứu. Sau khi gắn, token khách bị xoá (CHECK
     * {@code ck_bookings_owner}: đơn chỉ có một kiểu chủ) và đơn hiện ở "Đơn hàng của tôi".
     */
    @Transactional
    public BookingResponse claim(Integer userId, BookingLookupRequest request) {
        Booking booking = findByCodeAndPhone(request);
        if (!booking.isGuest() && !userId.equals(booking.getUserId())) {
            throw new ConflictException("Vé này đã thuộc một tài khoản khác");
        }
        booking.setUserId(userId);
        booking.setGuestTokenHash(null);
        List<BookingDetail> details = bookingDetailRepository.findByBookingIdWithSeat(booking.getId());
        return BookingResponse.from(booking, details, latestSuccessfulPayment(booking.getId()), LocalDateTime.now());
    }

    private Booking findByCodeAndPhone(BookingLookupRequest request) {
        return bookingRepository.findByBookingCodeAndContactPhone(
                        request.bookingCode().trim().toUpperCase(), request.phone().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé với mã và số điện thoại này"));
    }

    /** Trang Quản lý Đơn hàng của Admin; {@code tripId} null = mọi chuyến. */
    public PageResponse<BookingResponse> listForAdmin(Integer tripId, List<BookingStatus> statuses,
                                                      int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> result = tripId == null
                ? bookingRepository.findByStatusIn(normalizeStatuses(statuses), pageable)
                : bookingRepository.findByTripIdAndStatusIn(tripId, normalizeStatuses(statuses), pageable);
        return toPage(result);
    }


    /**
     * Nạp nội dung trang kèm ghế và giao dịch trong hai truy vấn gom, không N+1.
     */
    private PageResponse<BookingResponse> toPage(Page<Booking> page) {
        List<Integer> bookingIds = page.getContent().stream().map(Booking::getId).toList();
        LocalDateTime now = LocalDateTime.now();

        Map<Integer, List<BookingDetail>> seatsByBooking = bookingIds.isEmpty()
                ? Map.of()
                : bookingDetailRepository.findByBookingIdsWithSeat(bookingIds).stream()
                        .collect(Collectors.groupingBy(detail -> detail.getBooking().getId(),
                                LinkedHashMap::new, Collectors.toList()));

        Map<Integer, Payment> paymentByBooking = bookingIds.isEmpty()
                ? Map.of()
                : paymentRepository.findByBookingIdInAndStatusOrderByCreatedAtDesc(bookingIds, PaymentStatus.SUCCESS)
                        .stream()
                        .collect(Collectors.toMap(
                                payment -> payment.getBooking().getId(),
                                payment -> payment,
                                (first, second) -> first));

        List<BookingResponse> content = page.getContent().stream()
                .map(booking -> BookingResponse.from(
                        booking,
                        seatsByBooking.getOrDefault(booking.getId(), List.of()),
                        paymentByBooking.get(booking.getId()),
                        now))
                .toList();
        return PageResponse.from(page, content);
    }

    /**
     * Khoá và nạp các ghế theo id, trả về đã sắp theo id.
     *
     * <p>Ép buộc đủ số lượng: thiếu id nào tức ghế không tồn tại, ném 404 thay vì
     * âm thầm bỏ qua rồi tạo đơn thiếu ghế.
     */
    private List<TripSeat> lockSeats(List<Integer> seatIds) {
        if (seatIds.isEmpty()) {
            return List.of();
        }
        List<TripSeat> seats = tripSeatRepository.findAllByIdForUpdate(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new ResourceNotFoundException("Một hoặc nhiều ghế không tồn tại");
        }
        seats.sort(Comparator.comparing(TripSeat::getId));
        return seats;
    }

    /**
     * Ghế có bị chiếm tại thời điểm {@code now} không.
     *
     * <p>BOOKED luôn bị chiếm. HOLDING chỉ bị chiếm khi còn hạn — hết hạn coi như
     * trống và được giành lại ngay, không chờ job.
     */
    private boolean isBlocked(TripSeat seat, LocalDateTime now) {
        if (seat.getStatus() == SeatStatus.BOOKED) {
            return true;
        }
        if (seat.getStatus() == SeatStatus.HOLDING) {
            return seat.getHoldingUntil() == null || seat.getHoldingUntil().isAfter(now);
        }
        return false;
    }

    private boolean isExpired(Booking booking, LocalDateTime now) {
        return booking.getExpiresAt() != null && !booking.getExpiresAt().isAfter(now);
    }

    private Booking requireAccessible(Integer bookingId, Integer userId, String guestToken, boolean allowAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé với id " + bookingId));
        boolean owner = userId != null && userId.equals(booking.getUserId());
        if (!owner && !guestCanAccess(booking, guestToken) && !(allowAdmin && SecurityUtils.isAdmin())) {
            throw new AccessDeniedException("Bạn không có quyền truy cập đơn đặt vé này");
        }
        return booking;
    }

    /** Token khách chỉ mở được đơn của khách vãng lai; đơn đã gắn tài khoản thì không. */
    private boolean guestCanAccess(Booking booking, String guestToken) {
        return booking.isGuest() && GuestTokens.matches(guestToken, booking.getGuestTokenHash());
    }

    private StopPoint resolveStopPoint(Integer pointId, Integer tripId, PointType type, String label) {
        if (pointId == null) {
            return null;
        }
        StopPoint point = stopPointRepository.findById(pointId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy " + label + " với id " + pointId));
        if (!point.getTrip().getId().equals(tripId)) {
            throw new BadRequestException("Điểm dừng id " + pointId + " không thuộc chuyến này");
        }
        if (point.getPointType() != type) {
            throw new BadRequestException("Điểm dừng id " + pointId + " không phải " + label);
        }
        return point;
    }

    private String resolveContactEmail(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        return SecurityUtils.currentUser().map(AuthenticatedUser::email).orElse(null);
    }

    private PaymentGateway parseGateway(String raw) {
        try {
            return PaymentGateway.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Cổng thanh toán không hợp lệ: " + raw);
        }
    }

    private Payment buildPayment(Booking booking, PaymentGateway gateway, String transactionNo,
                                 String bankCode, String responseCode, PaymentStatus status,
                                 LocalDateTime now) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentGateway(gateway);
        payment.setTransactionNo(transactionNo);
        payment.setAmount(booking.getTotalPrice());
        payment.setBankCode(bankCode);
        payment.setResponseCode(responseCode);
        payment.setStatus(status);
        payment.setCreatedAt(now);
        return payment;
    }

    private Payment latestSuccessfulPayment(Integer bookingId) {
        return paymentRepository
                .findFirstByBookingIdAndStatusOrderByCreatedAtDesc(bookingId, PaymentStatus.SUCCESS)
                .orElse(null);
    }

    private String generateTransactionNo(PaymentGateway gateway) {
        return gateway.name() + System.currentTimeMillis()
                + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000)).toUpperCase();
    }

    private List<BookingStatus> normalizeStatuses(List<BookingStatus> statuses) {
        return statuses == null || statuses.isEmpty()
                ? Arrays.asList(BookingStatus.values())
                : statuses;
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** Cho test biết thời gian giữ ghế đang cấu hình. */
    public long holdMinutes() {
        return holdMinutes;
    }
}

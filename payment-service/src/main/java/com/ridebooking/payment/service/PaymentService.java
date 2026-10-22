package com.ridebooking.payment.service;

import com.ridebooking.payment.client.BookingClient;
import com.ridebooking.payment.client.BookingPaymentView;
import com.ridebooking.payment.client.ConfirmPaymentCommand;
import com.ridebooking.payment.client.ConfirmPaymentResult;
import com.ridebooking.payment.dto.CheckoutSessionResponse;
import com.ridebooking.payment.exception.ConflictException;
import com.ridebooking.payment.security.AuthenticatedUser;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Nghiệp vụ thanh toán: mở phiên Stripe và chốt đơn khi Stripe báo đã thu tiền. */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String GATEWAY_STRIPE = "STRIPE";

    private final BookingClient bookingClient;
    private final StripeService stripeService;

    public PaymentService(BookingClient bookingClient, StripeService stripeService) {
        this.bookingClient = bookingClient;
        this.stripeService = stripeService;
    }

    public CheckoutSessionResponse createCheckoutSession(Integer bookingId, AuthenticatedUser user) {
        return createCheckoutSession(bookingId, user, null);
    }

    /**
     * {@code user} null = khách vãng lai, khi đó bắt buộc {@code guestToken} khớp đơn.
     * Quyền: chủ đơn, Admin, hoặc token khách đúng (booking-service xác nhận).
     */
    public CheckoutSessionResponse createCheckoutSession(Integer bookingId, AuthenticatedUser user,
                                                         String guestToken) {
        BookingPaymentView booking = bookingClient.getForPayment(bookingId, guestToken);

        boolean allowed = booking.hasGuestAccess()
                || (user != null && (user.isAdmin() || Objects.equals(booking.userId(), user.id())));
        if (!allowed) {
            throw new AccessDeniedException("Bạn không có quyền thanh toán đơn này");
        }
        if (!booking.payable()) {
            if ("PAID".equalsIgnoreCase(booking.status())) {
                throw new ConflictException("Đơn #" + bookingId + " đã được thanh toán");
            }
            throw new ConflictException(
                    "Đơn #" + bookingId + " không còn ở trạng thái chờ thanh toán hoặc đã hết hạn giữ ghế");
        }

        Session session = stripeService.createCheckoutSession(
                booking.id(), user == null ? null : user.id(), booking.totalPrice().longValue());

        return new CheckoutSessionResponse(
                booking.id(),
                session.getId(),
                session.getUrl(),
                booking.totalPrice(),
                booking.currency(),
                booking.expiresAt());
    }

    /** Xử lý webhook Stripe. Không ném lỗi nghiệp vụ ra ngoài trừ chữ ký sai. */
    public void handleWebhook(String payload, String signatureHeader) {
        Event event = stripeService.parseWebhook(payload, signatureHeader);
        switch (event.getType()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" ->
                    onCheckoutSessionPaid(event);
            case "checkout.session.expired" ->
                    log.info("Phiên thanh toán Stripe {} đã hết hạn", event.getId());
            case "payment_intent.payment_failed" ->
                    log.warn("Stripe báo thanh toán thất bại cho sự kiện {}", event.getId());
            default ->
                    log.debug("Bỏ qua sự kiện Stripe không quan tâm: {}", event.getType());
        }
    }

    private void onCheckoutSessionPaid(Event event) {
        Session session = extractSession(event).orElse(null);
        if (session == null) {
            log.warn("Sự kiện {} không có dữ liệu Checkout Session đọc được", event.getId());
            return;
        }

        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || metadata.get("bookingId") == null) {
            log.warn("Phiên Stripe {} thiếu metadata bookingId", session.getId());
            return;
        }

        if (!"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            log.info("Phiên Stripe {} hoàn tất nhưng chưa thu được tiền (payment_status={}, method có thể bất đồng bộ)",
                    session.getId(), session.getPaymentStatus());
            return;
        }

        int bookingId;
        try {
            bookingId = Integer.parseInt(metadata.get("bookingId"));
        } catch (NumberFormatException ex) {
            log.warn("metadata bookingId không hợp lệ ở phiên {}: {}", session.getId(), metadata.get("bookingId"));
            return;
        }

        BigDecimal amount = Optional.ofNullable(session.getAmountTotal())
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.ZERO);

        settle(bookingId, session.getPaymentIntent(), amount);
    }

    private void settle(int bookingId, String paymentIntentId, BigDecimal amount) {
        try {
            ConfirmPaymentResult result = bookingClient.confirmPayment(bookingId, new ConfirmPaymentCommand(
                    GATEWAY_STRIPE, paymentIntentId, amount, "stripe_card", "00"));
            log.info("Đã xác nhận thanh toán đơn #{} qua Stripe, trạng thái đơn hiện tại: {}",
                    bookingId, result.status());
        } catch (ConflictException ex) {
            // Ghế đã bị job trả về AVAILABLE (đơn hết hạn) nên không thể chốt: phải hoàn tiền.
            log.warn("Đơn #{} không chốt được ({}), tiến hành hoàn tiền {}", bookingId, ex.getMessage(), paymentIntentId);
            stripeService.refund(paymentIntentId, bookingId);
        }
    }

    private Optional<Session> extractSession(Event event) {
        return event.getDataObjectDeserializer().getObject()
                .filter(Session.class::isInstance)
                .map(Session.class::cast);
    }
}

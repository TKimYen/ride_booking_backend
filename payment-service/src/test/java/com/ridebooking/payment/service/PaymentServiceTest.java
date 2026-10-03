package com.ridebooking.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ridebooking.payment.client.BookingClient;
import com.ridebooking.payment.client.BookingPaymentView;
import com.ridebooking.payment.client.ConfirmPaymentCommand;
import com.ridebooking.payment.client.ConfirmPaymentResult;
import com.ridebooking.payment.dto.CheckoutSessionResponse;
import com.ridebooking.payment.enums.Role;
import com.ridebooking.payment.exception.ConflictException;
import com.ridebooking.payment.security.AuthenticatedUser;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

/** Nghiệp vụ thanh toán, mock cả Stripe lẫn booking-service. */
class PaymentServiceTest {

    private BookingClient bookingClient;
    private StripeService stripeService;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        bookingClient = mock(BookingClient.class);
        stripeService = mock(StripeService.class);
        paymentService = new PaymentService(bookingClient, stripeService);
    }

    @Test
    @DisplayName("Không cho thanh toán đơn của người khác")
    void rejectsBookingOfAnotherUser() {
        when(bookingClient.getForPayment(1)).thenReturn(booking(1, 999, "PENDING", true));

        assertThatThrownBy(() -> paymentService.createCheckoutSession(1, user(1)))
                .isInstanceOf(AccessDeniedException.class);
        verify(stripeService, never()).createCheckoutSession(any(), any(), anyLong());
    }

    @Test
    @DisplayName("Đơn hết hạn giữ ghế thì không mở phiên thanh toán")
    void rejectsNotPayableBooking() {
        when(bookingClient.getForPayment(1)).thenReturn(booking(1, 1, "PENDING", false));

        assertThatThrownBy(() -> paymentService.createCheckoutSession(1, user(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("không còn ở trạng thái chờ thanh toán");
    }

    @Test
    @DisplayName("Đơn đã PAID thì báo đã thanh toán, không tạo phiên mới")
    void rejectsAlreadyPaidBooking() {
        when(bookingClient.getForPayment(1)).thenReturn(booking(1, 1, "PAID", false));

        assertThatThrownBy(() -> paymentService.createCheckoutSession(1, user(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("đã được thanh toán");
    }

    @Test
    @DisplayName("Đơn hợp lệ trả URL Stripe, số tiền lấy từ booking-service")
    void createsCheckoutSessionForOwnBooking() {
        when(bookingClient.getForPayment(1)).thenReturn(booking(1, 1, "PENDING", true));
        Session session = new Session();
        session.setId("cs_test_1");
        session.setUrl("https://checkout.stripe.com/c/pay/cs_test_1");
        when(stripeService.createCheckoutSession(eq(1), eq(1), eq(500_000L))).thenReturn(session);

        CheckoutSessionResponse response = paymentService.createCheckoutSession(1, user(1));

        assertThat(response.sessionId()).isEqualTo("cs_test_1");
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_1");
        assertThat(response.amount()).isEqualByComparingTo("500000");
        assertThat(response.currency()).isEqualTo("VND");
    }

    @Test
    @DisplayName("Webhook thanh toán thành công chốt đơn qua booking-service")
    void settlesPaidCheckoutSession() {
        Event event = paidEvent("pi_test_1", 500_000L);
        when(stripeService.parseWebhook("payload", "sig")).thenReturn(event);
        when(bookingClient.confirmPayment(eq(10), any())).thenReturn(new ConfirmPaymentResult(10, "PAID"));

        paymentService.handleWebhook("payload", "sig");

        ArgumentCaptor<ConfirmPaymentCommand> captor = ArgumentCaptor.forClass(ConfirmPaymentCommand.class);
        verify(bookingClient).confirmPayment(eq(10), captor.capture());
        assertThat(captor.getValue().gateway()).isEqualTo("STRIPE");
        assertThat(captor.getValue().transactionNo()).isEqualTo("pi_test_1");
        assertThat(captor.getValue().amount()).isEqualByComparingTo("500000");
        verify(stripeService, never()).refund(any(), any());
    }

    @Test
    @DisplayName("Ghế đã bị trả về (đơn hết hạn) thì hoàn tiền thay vì chốt")
    void refundsWhenBookingCannotBeSettled() {
        Event event = paidEvent("pi_test_2", 500_000L);
        when(stripeService.parseWebhook("payload", "sig")).thenReturn(event);
        when(bookingClient.confirmPayment(eq(10), any()))
                .thenThrow(new ConflictException("Đơn đã hết hạn thanh toán"));

        paymentService.handleWebhook("payload", "sig");

        verify(stripeService).refund("pi_test_2", 10);
    }

    @Test
    @DisplayName("Phiên chưa thu được tiền thì chưa chốt đơn")
    void doesNotSettleUnpaidSession() {
        Session session = session("pi_test_3", 500_000L);
        session.setPaymentStatus("unpaid");
        EventDataObjectDeserializer objectDeserializer = deserializer(session);
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(objectDeserializer);
        when(stripeService.parseWebhook("payload", "sig")).thenReturn(event);

        paymentService.handleWebhook("payload", "sig");

        verify(bookingClient, never()).confirmPayment(any(), any());
        verify(stripeService, never()).refund(any(), any());
    }

    private Event paidEvent(String paymentIntentId, long amount) {
        Session session = session(paymentIntentId, amount);
        EventDataObjectDeserializer objectDeserializer = deserializer(session);
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(objectDeserializer);
        return event;
    }

    private Session session(String paymentIntentId, long amount) {
        Session session = new Session();
        session.setId("cs_test");
        session.setPaymentStatus("paid");
        session.setPaymentIntent(paymentIntentId);
        session.setAmountTotal(amount);
        session.setMetadata(Map.of("bookingId", "10", "userId", "1"));
        return session;
    }

    private EventDataObjectDeserializer deserializer(Session session) {
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(session));
        return deserializer;
    }

    private BookingPaymentView booking(int id, int userId, String status, boolean payable) {
        return new BookingPaymentView(id, userId, new BigDecimal("500000"), "VND", status,
                LocalDateTime.now().plusMinutes(5), payable);
    }

    private AuthenticatedUser user(int id) {
        return new AuthenticatedUser(id, "user" + id + "@test.local", "User " + id, Role.USER);
    }
}

package com.ridebooking.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ridebooking.payment.exception.ConflictException;
import com.ridebooking.payment.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Client gọi API nội bộ booking-service: đúng header, và dịch 404/409 đúng loại lỗi. */
class BookingClientTest {

    private MockRestServiceServer server;
    private BookingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://booking-service.test")
                .defaultHeader("X-Internal-Key", "test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        client = new BookingClient(builder.build(), objectMapper);
    }

    @Test
    @DisplayName("Lấy đơn kèm header X-Internal-Key và đọc được số tiền")
    void getsBookingForPayment() {
        server.expect(requestTo("http://booking-service.test/api/internal/bookings/5"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Key", "test-key"))
                .andRespond(withSuccess("""
                        {"id":5,"userId":9,"totalPrice":500000,"currency":"VND",
                         "status":"PENDING","expiresAt":"2026-10-02T10:10:00","payable":true}
                        """, MediaType.APPLICATION_JSON));

        BookingPaymentView view = client.getForPayment(5);

        assertThat(view.id()).isEqualTo(5);
        assertThat(view.userId()).isEqualTo(9);
        assertThat(view.totalPrice()).isEqualByComparingTo("500000");
        assertThat(view.payable()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("booking-service trả 404 thì thành ResourceNotFoundException")
    void mapsNotFound() {
        server.expect(requestTo("http://booking-service.test/api/internal/bookings/404"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Không tìm thấy đơn\"}"));

        assertThatThrownBy(() -> client.getForPayment(404))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy đơn");
    }

    @Test
    @DisplayName("booking-service trả 409 thì thành ConflictException kèm thông báo gốc")
    void mapsConflict() {
        server.expect(requestTo("http://booking-service.test/api/internal/bookings/5/confirm-payment"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Đơn đã hết hạn thanh toán\"}"));

        ConfirmPaymentCommand command = new ConfirmPaymentCommand(
                "STRIPE", "pi_1", new BigDecimal("500000"), "stripe_card", "00");

        assertThatThrownBy(() -> client.confirmPayment(5, command))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Đơn đã hết hạn thanh toán");
    }
}

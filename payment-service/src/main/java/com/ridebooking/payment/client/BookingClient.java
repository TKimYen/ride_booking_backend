package com.ridebooking.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridebooking.payment.exception.ConflictException;
import com.ridebooking.payment.exception.PaymentGatewayException;
import com.ridebooking.payment.exception.ResourceNotFoundException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gọi API nội bộ của booking-service.
 *
 * <p>Đây là cầu nối duy nhất tới bảng {@code payments} và tới luồng chốt ghế:
 * payment-service không có database riêng nên mọi thay đổi trạng thái tiền/ghế
 * đều đi qua booking-service để giữ đúng một nguồn sự thật.
 */
@Component
public class BookingClient {

    private final RestClient bookingRestClient;
    private final ObjectMapper objectMapper;

    public BookingClient(RestClient bookingRestClient, ObjectMapper objectMapper) {
        this.bookingRestClient = bookingRestClient;
        this.objectMapper = objectMapper;
    }

    public BookingPaymentView getForPayment(Integer bookingId) {
        return getForPayment(bookingId, null);
    }

    /**
     * {@code guestToken} (header {@code X-Guest-Token} của khách vãng lai) được chuyển
     * tiếp nguyên vẹn; booking-service so với hash và trả kết quả ở {@code guestAccess}.
     */
    public BookingPaymentView getForPayment(Integer bookingId, String guestToken) {
        try {
            BookingPaymentView view = bookingRestClient.get()
                    .uri("/api/internal/bookings/{bookingId}", bookingId)
                    .headers(headers -> {
                        if (guestToken != null && !guestToken.isBlank()) {
                            headers.set("X-Guest-Token", guestToken);
                        }
                    })
                    .retrieve()
                    .body(BookingPaymentView.class);
            if (view == null) {
                throw new PaymentGatewayException("booking-service trả về dữ liệu rỗng cho đơn #" + bookingId);
            }
            return view;
        } catch (RestClientResponseException ex) {
            throw translate(ex, bookingId);
        }
    }

    public ConfirmPaymentResult confirmPayment(Integer bookingId, ConfirmPaymentCommand command) {
        try {
            ConfirmPaymentResult result = bookingRestClient.post()
                    .uri("/api/internal/bookings/{bookingId}/confirm-payment", bookingId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(command)
                    .retrieve()
                    .body(ConfirmPaymentResult.class);
            if (result == null) {
                throw new PaymentGatewayException("booking-service trả về dữ liệu rỗng cho đơn #" + bookingId);
            }
            return result;
        } catch (RestClientResponseException ex) {
            throw translate(ex, bookingId);
        }
    }

    private RuntimeException translate(RestClientResponseException ex, Integer bookingId) {
        int status = ex.getStatusCode().value();
        String message = extractMessage(ex);
        return switch (status) {
            case 404 -> new ResourceNotFoundException(
                    message != null ? message : "Không tìm thấy đơn đặt vé #" + bookingId);
            case 409 -> new ConflictException(
                    message != null ? message : "Đơn #" + bookingId + " không còn ở trạng thái chờ thanh toán");
            case 403 -> new PaymentGatewayException(
                    "Không xác thực được với booking-service (X-Internal-Key sai)");
            default -> new PaymentGatewayException("booking-service trả lỗi " + status, ex);
        };
    }

    /** Đọc field {@code message} trong format lỗi thống nhất của hệ thống, nếu có. */
    private String extractMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode message = node.get("message");
            return message != null && !message.isNull() ? message.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}

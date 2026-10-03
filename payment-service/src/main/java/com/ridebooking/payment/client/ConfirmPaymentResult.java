package com.ridebooking.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Phần dùng của kết quả booking-service trả về sau khi xác nhận thanh toán. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmPaymentResult(Integer id, String status) {
}

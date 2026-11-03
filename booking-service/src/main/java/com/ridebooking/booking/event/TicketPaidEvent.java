package com.ridebooking.booking.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TicketPaidEvent(
        String recipient,
        String bookingCode,
        String routeName,
        LocalDateTime departureTime,
        List<String> seatNames,
        BigDecimal totalPrice) {

    public TicketPaidEvent {
        seatNames = List.copyOf(seatNames);
    }
}

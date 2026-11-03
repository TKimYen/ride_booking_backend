package com.ridebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ridebooking.booking.event.TicketPaidEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class TicketEmailListenerTest {

    @Test
    void sendsTicketDetailsAndLookupLink() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        TicketEmailListener listener =
                new TicketEmailListener(mailSender, "tickets@example.com", "https://xego.example");
        TicketPaidEvent event = new TicketPaidEvent(
                "customer@example.com",
                "A1B2C3D4",
                "Hà Nội - Sa Pa",
                LocalDateTime.of(2026, 11, 1, 8, 30),
                List.of("A1", "A2"),
                new BigDecimal("450000.00"));

        listener.sendTicket(event);

        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("tickets@example.com");
        assertThat(message.getValue().getTo()).containsExactly("customer@example.com");
        assertThat(message.getValue().getSubject()).contains("A1B2C3D4");
        assertThat(message.getValue().getText())
                .contains("A1B2C3D4", "Hà Nội - Sa Pa", "A1, A2", "450000.00")
                .contains("https://xego.example/tra-cuu-ve?bookingCode=A1B2C3D4");
    }
}

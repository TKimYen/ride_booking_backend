package com.ridebooking.booking.service;

import com.ridebooking.booking.event.TicketPaidEvent;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class TicketEmailListener {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailListener.class);
    private static final DateTimeFormatter DEPARTURE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final JavaMailSender mailSender;
    private final String sender;
    private final String frontendUrl;

    public TicketEmailListener(
            JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@ridebooking.local}") String sender,
            @Value("${app.mail.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.mailSender = mailSender;
        this.sender = sender;
        this.frontendUrl = frontendUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendTicket(TicketPaidEvent event) {
        String lookupUrl = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/tra-cuu-ve")
                .queryParam("bookingCode", event.bookingCode())
                .build()
                .toUriString();
        String text = """
                Cảm ơn bạn đã đặt vé XeGo.

                Mã vé: %s
                Tuyến: %s
                Khởi hành: %s
                Ghế: %s
                Tổng thanh toán: %s VND

                Tra cứu vé và xem mã QR tại:
                %s

                Vui lòng nhập số điện thoại đã dùng khi đặt vé để tra cứu.
                """.formatted(
                event.bookingCode(),
                event.routeName(),
                event.departureTime().format(DEPARTURE_FORMAT),
                String.join(", ", event.seatNames()),
                event.totalPrice().toPlainString(),
                lookupUrl);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(event.recipient());
        message.setSubject("Vé xe của bạn - " + event.bookingCode());
        message.setText(text);
        try {
            mailSender.send(message);
            log.info("Đã gửi email vé cho đơn {}", event.bookingCode());
        } catch (MailException exception) {
            log.error("Gửi email vé cho đơn {} tới {} thất bại",
                    event.bookingCode(), event.recipient(), exception);
        }
    }
}

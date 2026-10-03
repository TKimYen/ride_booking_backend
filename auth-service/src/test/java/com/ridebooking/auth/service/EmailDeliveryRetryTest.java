package com.ridebooking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ridebooking.auth.config.EmailProperties;
import com.ridebooking.auth.entity.EmailSendLog;
import com.ridebooking.auth.repository.EmailSendLogRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Chứng minh cái bẫy: SMTP hỏng tạm thời thì email mất vĩnh viễn, và người dùng
 * còn bị khoá hạn mức một cách vô lý.
 *
 * <p>Kịch bản thật (SMTP/Gmail sập 5 phút lúc 3 giờ sáng): chủ tài khoản bấm "quên
 * mật khẩu", HTTP trả 200 kèm câu "chúng tôi đã gửi email" — nhưng không có email
 * nào đi ra. Họ thử lại: hệ thống trả "bạn đã yêu cầu quá nhiều lần, thử sau 60
 * phút". Vậy là khách bị kẹt: không có email, không có đường đi tiếp, và không có
 * gì tự động gửi lại khi SMTP hồi phục. Token đặt lại mặc định chỉ sống 30 phút.
 */
@SpringBootTest
@DisplayName("Không mất email khi SMTP hỏng tạm thời")
class EmailDeliveryRetryTest {

        /**
     * Thay {@code JavaMailSender} thật bằng mock, nhưng PHẢI cho đủ phụ thuộc mà
     * bean thật có. Actuator dựng {@code mailHealthContributor} từ danh sách
     * transporter; nếu thiếu thì danh sách rỗng và cả ApplicationContext không
     * dựng nổi (lỗi đã gặp: {@code 'beans' must not be empty}).
     */
    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private JavaMailSenderImpl mailSenderImpl;

    @Autowired
    private EmailSendLogRepository logRepository;

    @Autowired
    private EmailProperties properties;

    private MailService mailService;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        Session session = Session.getInstance(new Properties());
        org.mockito.Mockito.lenient()
                .when(mailSender.createMimeMessage())
                .thenAnswer(call -> new MimeMessage(session));
        mailService = new MailService(mailSender, logRepository, properties,
                new EmailSendLogWriter(logRepository));
    }

    /** Bật/tắt lỗi SMTP để mô phỏng sự cố tạm thời. */
    private void smtpIsBroken(boolean broken) {
        if (broken) {
            org.mockito.Mockito.doThrow(new RuntimeException("Connection refused"))
                    .when(mailSender).send(any(MimeMessage.class));
        } else {
            org.mockito.Mockito.doNothing().when(mailSender).send(any(MimeMessage.class));
        }
    }

    @Test
    @DisplayName("Gửi lỗi thì email vẫn nằm trong hàng đợi chờ retry, KHÔNG bị bỏ")
    void failedSendIsKeptForRetry() {
        smtpIsBroken(true);

        mailService.send("khach1@test.com", "Đặt lại mật khẩu",
                "<p>xin chào</p>", "xin chào", "PASSWORD_RESET", "10.0.0.1");

        assertThat(logRepository.findAll())
                .as("email gửi lỗi phải được giữ lại để thử lại, không được xoá")
                .hasSize(1);

        EmailSendLog row = logRepository.findAll().get(0);
        assertThat(row.getStatus())
                .as("phải ở trạng thái chờ gửi lại, không phải trạng thái chết")
                .isEqualTo("PENDING");
        assertThat(row.getHtmlBody())
                .as("phải lưu nội dung để gửi lại được sau này")
                .isEqualTo("<p>xin chào</p>");
        assertThat(row.getNextAttemptAt())
                .as("phải có mốc thời gian để worker biết khi nào thử lại")
                .isNotNull();
    }

    @Test
    @DisplayName("Email thất bại vẫn bị tính vào hạn mức gửi")
    void failedSendStillCountsTowardRateLimit() {
        smtpIsBroken(true);
        String recipient = "khach2@test.com";

        for (int i = 0; i < properties.getMaxPerEmailPerWindow(); i++) {
            assertThat(mailService.checkRateLimit(recipient, "10.0.0.2"))
                    .as("lần %d phải còn được gửi", i + 1)
                    .isNull();
            mailService.send(recipient, "Đặt lại mật khẩu",
                    "<p>xin chào</p>", "xin chào", "PASSWORD_RESET", "10.0.0.2");
        }

        assertThat(mailService.checkRateLimit(recipient, "10.0.0.2"))
                .as("sau khi đã hết hạn mức thì phải bị chặn — đây là hành vi CỐ Ý, "
                        + "xem javadoc MailService.checkRateLimit: nếu chỉ đếm email "
                        + "gửi thành công thì kẻ spam chỉ cần làm hỏng request là lách được")
                .isNotNull();
    }

    @Test
    @DisplayName("Khi SMTP hồi phục, worker gửi được email đang chờ")
    void pendingEmailIsDeliveredWhenSmtpRecovers() {
        smtpIsBroken(true);
        mailService.send("khach3@test.com", "Đặt lại mật khẩu",
                "<p>xin chào</p>", "xin chào", "PASSWORD_RESET", "10.0.0.3");

        // SMTP hồi phục, worker quét tới dòng đang chờ.
        smtpIsBroken(false);
        int sent = mailService.retryPending();

        assertThat(sent).isEqualTo(1);
        assertThat(logRepository.findAll().get(0).getStatus())
                .isEqualTo(EmailSendLog.STATUS_SENT);
    }

    @Test
    @DisplayName("Không gửi lại link đã hết hạn — token chỉ sống 30 phút")
    void expiredTokenEmailIsNotResent() {
        smtpIsBroken(true);
        mailService.send("khach4@test.com", "Đặt lại mật khẩu",
                "<p>xin chào</p>", "xin chào", "PASSWORD_RESET", "10.0.0.4");

        // Đẩy mốc chờ về quá khứ nhưng vẫn còn trong hạn token.
        // Phải dùng ĐÚNG danh sách đã sửa: gọi findAll() hai lần sẽ ra hai tập
        // entity khác nhau, nên saveAll() lúc sau lưu bản CHƯA sửa.
        List<EmailSendLog> rows = logRepository.findAll();
        rows.forEach(row -> row.setNextAttemptAt(LocalDateTime.now().minusMinutes(1)));
        logRepository.saveAll(rows);

        smtpIsBroken(false);
        assertThat(mailService.retryPending()).isEqualTo(1);

        // Token chỉ sống 30 phút: đẩy hạn retry về trước thời điểm hiện tại,
        // lúc này link trong email đã chết nên phải bị bỏ.
        List<EmailSendLog> reset = logRepository.findAll();
        reset.forEach(row -> {
            row.setStatus(EmailSendLog.STATUS_PENDING);
            row.setRetryUntil(LocalDateTime.now().minusSeconds(1));
            row.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        });
        logRepository.saveAll(reset);

        assertThat(mailService.retryPending())
                .as("gửi lại link đã hết hạn chỉ khiến khách bấm vào thấy 'mã không hợp lệ'")
                .isZero();
        assertThat(logRepository.findAll().get(0).getStatus())
                .isEqualTo(EmailSendLog.STATUS_DEAD);
    }

    @Test
    @DisplayName("Không gửi vô hạn: hết số lần thử thì bỏ qua, dù SMTP đã hồi phục")
    void retriesStopAfterMaxAttempts() {
        smtpIsBroken(true);
        mailService.send("khach5@test.com", "Đặt lại mật khẩu",
                "<p>xin chào</p>", "xin chào", "PASSWORD_RESET", "10.0.0.5");

        // Dòng này đã thử hết số lần cho phép (mặc định 5) nhưng vẫn tới hạn.
        List<EmailSendLog> rows = logRepository.findAll();
        rows.forEach(row -> {
            row.setAttempts(properties.getMaxDeliveryAttempts());
            row.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        });
        logRepository.saveAll(rows);

        // SMTP đã hồi phục hoàn toàn.
        smtpIsBroken(false);

        assertThat(mailService.retryPending())
                .as("gửi cho khách mãi mỗi phút một lần là tự tấn công chính mình")
                .isZero();
        assertThat(logRepository.findAll().get(0).getStatus())
                .isEqualTo(EmailSendLog.STATUS_DEAD);
    }
}

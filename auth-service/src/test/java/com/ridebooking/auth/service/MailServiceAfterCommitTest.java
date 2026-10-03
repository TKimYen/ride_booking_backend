package com.ridebooking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ridebooking.auth.entity.EmailSendLog;
import com.ridebooking.auth.repository.EmailSendLogRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Mail chỉ được gửi khi transaction đã commit.
 *
 * <p>Các luồng email vừa tạo token vừa gửi mail. Nếu gửi ngay rồi transaction
 * mới rollback, khách nhận được link chứa token không tồn tại trong DB và bấm vào
 * thấy "mã không hợp lệ" — lỗi của hệ thống nhưng người dùng nghĩ là do họ.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Gửi email sau commit")
class MailServiceAfterCommitTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailSendLogRepository logRepository;

    private MailService mailService;
    private long nextId = 1L;
    private final java.util.Map<Long, EmailSendLog> savedRows =
            new java.util.LinkedHashMap<>();
    private final List<String> statusAtSaveTime = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        mailService = new MailService(mailSender, logRepository,
                new com.ridebooking.auth.config.EmailProperties(),
                new EmailSendLogWriter(logRepository));
        // Mock mặc định trả null cho createMimeMessage(), khiến MimeMessageHelper
        // ném lỗi trước khi kịp gửi. Phải trả message thật.
        Session session = Session.getInstance(new Properties());
        org.mockito.Mockito.lenient()
                .when(mailSender.createMimeMessage())
                .thenAnswer(call -> new MimeMessage(session));

        // Repository là mock nên save() trả về đối tượng có id = null, khiến
        // markSent() không biết cần cập nhật dòng nào và bỏ qua im lặng — dòng
        // PENDING không bao giờ chuyển thành SENT. Gán id và cho findById trả lại
        // đúng dòng vừa lưu để mô phỏng hành vi repository thật.
        // Lưu ý: phải ghi lại trạng thái TẠI LÚC save(), chứ không so sánh entity
        // sau này. Cùng một object được sửa ở lần save thứ hai nên captor sẽ thấy
        // cả hai lần đều là SENT — đúng trạng thái cuối nhưng sai thứ tự.
        org.mockito.Mockito.lenient()
                .when(logRepository.save(any(EmailSendLog.class)))
                .thenAnswer(call -> {
                    EmailSendLog row = call.getArgument(0);
                    row.setId(nextId);
                    savedRows.put(nextId, row);
                    statusAtSaveTime.add(row.getStatus());
                    nextId++;
                    return row;
                });
        org.mockito.Mockito.lenient()
                .when(logRepository.findById(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(call -> java.util.Optional.ofNullable(
                        savedRows.get(call.<Long>getArgument(0))));
    }

    @Test
    @DisplayName("Trong transaction: hoãn lại, chưa gửi ngay")
    void send_insideTransaction_defersUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            mailService.send("huy@test.com", "Chủ đề", "<p>xin chào</p>", "xin chào",
                    "EMAIL_VERIFICATION", "1.2.3.4");

            verify(mailSender, never()).send(any(MimeMessage.class));
            assertThat(TransactionSynchronizationManager.getSynchronizations())
                    .as("phải đăng ký 1 callback chờ commit")
                    .hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Sau commit thì mail mới thực sự đi")
    void send_afterCommit_actuallySends() {
        TransactionSynchronizationManager.initSynchronization();
        mailService.send("huy@test.com", "Chủ đề", "<p>xin chào</p>", "xin chào",
                "EMAIL_VERIFICATION", "1.2.3.4");

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(callback -> callback.afterCommit());
        TransactionSynchronizationManager.clearSynchronization();

        verify(mailSender, times(1)).send(any(MimeMessage.class));

        // Dòng hàng đợi được ghi TRƯỚC khi gửi (xem MailService.sendNow): nếu JVM
        // chết giữa lúc gửi, email phải còn trong hàng đợi để gửi lại. Nên có
        // 2 lần save — lần đầu tạo dòng PENDING, lần sau đánh dấu đã gửi.
        verify(logRepository, times(2)).save(any(EmailSendLog.class));

        assertThat(statusAtSaveTime)
                .as("ghi PENDING trước khi gửi, chuyển SENT sau khi gửi thành công")
                .containsExactly(EmailSendLog.STATUS_PENDING, EmailSendLog.STATUS_SENT);
        assertThat(savedRows.values())
                .allSatisfy(row -> assertThat(row.getRecipient()).isEqualTo("huy@test.com"));
    }

    @Test
    @DisplayName("Không có transaction thì gửi ngay, không cần chờ")
    void send_withoutTransaction_sendsImmediately() {
        mailService.send("huy@test.com", "Chủ đề", "<p>xin chào</p>", "xin chào",
                "EMAIL_VERIFICATION", "1.2.3.4");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Gửi nhiều địa chỉ: bỏ qua địa chỉ rỗng")
    void sendToEach_skipsBlankRecipients() {
        mailService.sendToEach(java.util.Arrays.asList("a@test.com", null, "  ", "b@test.com"),
                "Chủ đề", new String[]{"<p>x</p>", "x"}, "SECURITY_ALERT", "1.2.3.4");

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("SMTP chết thì nuốt lỗi, đưa vào hàng đợi chờ gửi lại, không ném ra ngoài")
    void send_smtpFails_doesNotThrow() {
        // MailSender khai báo MailException (runtime), không phải checked exception
        org.mockito.Mockito.doThrow(
                        new org.springframework.mail.MailSendException("SMTP chết"))
                .when(mailSender).send(any(MimeMessage.class));

        // MailService phải nuốt lỗi: luồng chính không được sập vì SMTP hỏng
        mailService.send("huy@test.com", "Chủ đề", "<p>x</p>", "x",
                "EMAIL_VERIFICATION", "1.2.3.4");

        // Vẫn phải ghi lại dòng (kể cả lần lỗi): nếu không kẻ spam vô hiệu hoá hạn
        // mức bằng cách làm hỏng SMTP. Nay dòng này mang trạng thái PENDING kèm
        // nội dung email để worker gửi lại, thay vì chỉ ghi log rồi bỏ.
        // 2 lần save: tạo dòng PENDING, rồi đẩy mốc thử lại.
        verify(logRepository, times(2)).save(any(EmailSendLog.class));
        assertThat(statusAtSaveTime.get(0))
                .as("lần ghi đầu phải là PENDING, kèm nội dung để gửi lại")
                .isEqualTo(EmailSendLog.STATUS_PENDING);
        assertThat(savedRows.values())
                .allSatisfy(row -> assertThat(row.getHtmlBody()).isEqualTo("<p>x</p>"));
        assertThat(statusAtSaveTime.get(1))
                .as("sau khi gửi lỗi thì vẫn PENDING để thử lại, KHÔNG chuyển DEAD")
                .isEqualTo(EmailSendLog.STATUS_PENDING);
    }
}

package com.ridebooking.auth.service;

import com.ridebooking.auth.config.EmailProperties;
import com.ridebooking.auth.entity.EmailSendLog;
import com.ridebooking.auth.repository.EmailSendLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Gửi email và chống spam.
 *
 * <p>Nguyên tắc quan trọng: <b>hàm gửi không bao giờ ném exception ra ngoài</b>.
 * Nếu gửi thất bại mà làm request lỗi, người dùng sẽ biết địa chỉ có tồn tại hay
 * không (đăng ký email của người khác → báo lỗi tương phản với email không có
 * trong hệ thống), và một lần hỏng SMTP sẽ chặn cả đăng ký lẫn đăng nhập.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final EmailSendLogRepository logRepository;
    private final EmailSendLogWriter logWriter;
    private final EmailProperties properties;

    public MailService(JavaMailSender mailSender,
                       EmailSendLogRepository logRepository,
                       EmailProperties properties,
                       EmailSendLogWriter logWriter) {
        this.mailSender = mailSender;
        this.logRepository = logRepository;
        this.properties = properties;
        this.logWriter = logWriter;
    }

    /**
     * Kiểm tra hạn mức gửi. Trả lý do bị chặn, hoặc {@code null} nếu được phép.
     */
    public String checkRateLimit(String recipient, String ipAddress) {
        LocalDateTime since = LocalDateTime.now()
                .minusMinutes(properties.getResendWindowMinutes());

        if (recipient != null
                && logRepository.countByRecipientAndCreatedAtAfter(recipient, since)
                >= properties.getMaxPerEmailPerWindow()) {
            return "Bạn đã yêu cầu quá nhiều lần. Vui lòng thử lại sau "
                    + properties.getResendWindowMinutes() + " phút.";
        }
        if (ipAddress != null && !ipAddress.isBlank()
                && logRepository.countByIpAddressAndCreatedAtAfter(ipAddress, since)
                >= properties.getMaxPerIpPerWindow()) {
            return "Quá nhiều yêu cầu gửi email từ thiết bị này. Vui lòng thử lại sau "
                    + properties.getResendWindowMinutes() + " phút.";
        }
        return null;
    }

    /**
     * Gửi email. Luôn trả về {@code true}/{@code false}, không ném exception.
     * Ghi lịch sử kể cả khi thất bại để còn đối chiếu và vẫn áp dụng hạn mức
     * (nếu không, kẻ spam sẽ bị vô hiệu hoá bằng cách làm hỏng).
     *
     * <p>Nếu đang có transaction mở, thao tác gửi thật được hoãn tới <b>sau khi
     * commit</b>. Lý do: các luồng này vừa tạo token vừa gửi mail trong cùng một
     * transaction. Nếu gửi ngay, sau đó transaction rollback vì lý do khác thì
     * khách đã nhận một link chứa token không tồn tại trong database — bấm vào
     * báo "mã không hợp lệ" mà không hiểu vì sao. Hoãn lại thì mail chỉ đi khi
     * token thật sự đã nằm trong DB.
     */
    public boolean send(String to, String subject, String htmlBody, String textBody,
                        String kind, String ipAddress) {
        return send(to, subject, new String[]{htmlBody, textBody}, kind, ipAddress);
    }

    /** Gửi email với nội dung dựng sẵn từ {@link EmailTemplates#split}. */
    public boolean send(String to, String subject, String[] htmlAndText,
                        String kind, String ipAddress) {
        return afterCommit(() -> sendNow(to, subject, htmlAndText, kind, ipAddress));
    }

    /**
     * Chạy {@code action} ngay nếu không có transaction, nếu không thì hoãn tới
     * sau khi commit. Luôn trả về {@code true}; giá trị trả về thật của action
     * không dùng được vì sau commit thì chưa xảy ra.
     */
    private boolean afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return true;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
        return true;
    }

    private boolean sendNow(String to, String subject, String[] htmlAndText,
                            String kind, String ipAddress) {
        // Ghi vào hàng đợi TRƯỚC khi gửi. Nếu gửi trước rồi ghi sau, thì khoảnh
        // khắc JVM bị kill giữa hai việc thì email đã đi nhưng không có dấu vết,
        // và không có gì thử lại được. Ghi trước thì tệ nhất là gửi trùng, còn gửi
        // trùng thì khách chỉ thấy hai email giống nhau — chấp nhận được.
        Long id = logWriter.enqueue(to, subject, kind, ipAddress,
                htmlAndText[0], htmlAndText[1], retryDeadline(kind));

        try {
            buildAndSend(to, subject, htmlAndText);
            logWriter.markSent(id);
            log.info("Đã gửi email [{}] tới {}", kind, mask(to));
            return true;
        } catch (Exception e) {
            // Không ném ra ngoài — xem javadoc của lớp.
            log.warn("Gửi email [{}] tới {} thất bại, sẽ thử lại: {}",
                    kind, mask(to), e.getMessage());
            logWriter.markRetryLater(id, shortReason(e), LocalDateTime.now());
            return false;
        }
    }

    /**
     * Hạn chót để thử gửi lại, đặt bằng thời hạn token mà email mang theo.
     *
     * <p>Gửi lại link đã hết hạn chỉ khiến khách bấm vào thấy "mã không hợp lệ" và
     * tưởng hệ thống hỏng — tệ hơn hẳn là không gửi. Vì vậy mốc retry bị chặn
     * bởi hạn token, ví dụ link đặt lại mật khẩu chỉ sống 30 phút.
     */
    private LocalDateTime retryDeadline(String kind) {
        Duration ttl = switch (kind == null ? "" : kind) {
            case "PASSWORD_RESET" -> properties.getPasswordResetTtl();
            case "EMAIL_CHANGE" -> properties.getEmailChangeTtl();
            case "EMAIL_VERIFICATION" -> properties.getVerifyTtl();
            default -> Duration.ofHours(1);
        };
        return LocalDateTime.now().plus(ttl);
    }

    /**
     * Quét hàng đợi và gửi lại những email tới hạn. Trả về số email gửi thành công.
     *
     * <p>Đây là phần giải quyết việc email mất vĩnh viễn khi SMTP hỏng: trước đây
     * gửi lỗi thì ghi log rồi bỏ, HTTP vẫn trả 200 kèm "đã gửi email cho bạn", khách
     * chờ mãi không thấy gì, thử lại thì bị chặn vì hết hạn mức. Nay email nằm
     * trong hàng đợi và được gửi lại khi SMTP hồi phục.
     */
    public int retryPending() {
        List<EmailSendLog> due = logRepository.findDueForRetry(
                LocalDateTime.now(), PageRequest.of(0, properties.getRetryBatchSize()));

        int sent = 0;
        for (EmailSendLog row : due) {
            if (row.getRetryUntil() != null && row.getRetryUntil().isBefore(LocalDateTime.now())) {
                // Link đã hết hạn: dừng thay vì gửi cho khách một link chết.
                logWriter.markDead(row.getId(), "hết hạn token trước khi gửi lại được");
                continue;
            }
            if (row.getAttempts() >= properties.getMaxDeliveryAttempts()) {
                logWriter.markDead(row.getId(), "hết số lần thử: " + row.getAttempts());
                continue;
            }
            if (tryDeliver(row)) {
                sent++;
            }
        }
        if (sent > 0) {
            log.info("Gửi lại {} email sau khi SMTP hồi phục", sent);
        }
        return sent;
    }

    private boolean tryDeliver(EmailSendLog row) {
        try {
            buildAndSend(row.getRecipient(), row.getSubject(),
                    new String[]{row.getHtmlBody(), row.getTextBody()});
            logWriter.markSent(row.getId());
            log.info("Đã gửi lại email [{}] tới {}", row.getKind(), mask(row.getRecipient()));
            return true;
        } catch (Exception e) {
            long delay = Math.min(
                    properties.getRetryBaseDelaySeconds() * (1L << Math.min(row.getAttempts(), 10)),
                    properties.getRetryMaxDelaySeconds());
            logWriter.markRetryLater(row.getId(), shortReason(e),
                    LocalDateTime.now().plusSeconds(delay));
            log.warn("Gửi lại email [{}] tới {} lỗi lần {}, thử lại sau {}s: {}",
                    row.getKind(), mask(row.getRecipient()), row.getAttempts() + 1, delay,
                    e.getMessage());
            return false;
        }
    }

    private void buildAndSend(String to, String subject, String[] htmlAndText)
            throws Exception {
        MimeMessage message = mailSender.createMimeMessage();

        // Chỉ dùng helper cho phần header. Spring 7 đã bỏ setHtmlMessage(), còn
        // setText(content, contentType) ghi đè lên CÙNG một part, nên gọi hai lần
        // sẽ chỉ còn lại bản cuối — mail HTML bị mất, hoặc tệ hơn là bản "text
        // thuần" lại chứa HTML. Vì vậy thân email dựng tay theo chuẩn
        // multipart/alternative: client đọc được bản nào cũng được.
        MimeMessageHelper helper =
                new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.getFrom(), properties.getFromName());
        helper.setTo(to);
        helper.setSubject(subject);

        // Thứ tự BẮT BUỘC: text/plain trước, text/html sau. Client ưu tiên bản
        // sau (đặc biệt là HTML).
        MimeMultipart alternative = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(htmlAndText[1], StandardCharsets.UTF_8.name());
        alternative.addBodyPart(textPart);

        MimeBodyPart htmlPart = new MimeBodyPart();
        // KHÔNG dùng setContent(html, "text/html..."): Angus Mail ép nội dung về
        // text/plain dù đã truyền content type (đã kiểm chứng). Cách chắc chắn
        // hoạt động là setText rồi ghi đè header Content-Type.
        htmlPart.setText(htmlAndText[0], StandardCharsets.UTF_8.name());
        htmlPart.setHeader("Content-Type", "text/html; charset=UTF-8");
        alternative.addBodyPart(htmlPart);

        message.setContent(alternative);
        mailSender.send(message);
    }

    /** Gửi tới nhiều địa chỉ (cảnh báo bảo mật cho cả email cũ và email mới). */
    public boolean sendToEach(java.util.List<String> recipients, String subject,
                              String[] htmlAndText, String kind, String ipAddress) {
        return afterCommit(() -> {
            for (String to : recipients) {
                if (to == null || to.isBlank()) {
                    continue;
                }
                sendNow(to, subject, htmlAndText, kind, ipAddress);
            }
        });
    }

    /**
     * Dọn lịch sử gửi cũ — chỉ dòng đã KẾT THÚC.
     *
     * <p>Dòng PENDING phải còn lại dù đã cũ: xoá nó nghĩa là xoá email chưa gửi
     * được. Xem {@link EmailSendLog} và {@code EmailSendLogRepository#deleteOlderThan}.
     */
    public int purgeHistory() {
        int removed = logRepository.deleteOlderThan(
                LocalDateTime.now().minusDays(properties.getHistoryRetentionDays()));
        if (removed > 0) {
            log.info("Đã dọn {} bản ghi lịch sử gửi email", removed);
        }
        return removed;
    }

    private String mask(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String shortReason(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > 490 ? message.substring(0, 490) : message;
    }
}

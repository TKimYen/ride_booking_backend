package com.ridebooking.auth.service;

import com.ridebooking.auth.config.EmailProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker gửi lại email bị trượt.
 *
 * <p>Chạy {@link MailService#retryPending()} mỗi {@code app.email.retry-scan-seconds}
 * giây. Đây là thứ biến "SMTP chết 5 phút lúc 3 giờ sáng" từ mất email vĩnh viễn
 * thành chuyện chậm vài phút.
 *
 * <p>Để tắt (ví dụ khi chạy unit test hoặc khi muốn xử lý thủ công): đặt
 * {@code app.email.retry-enabled=false}.
 */
@Component
public class EmailRetryJob {

    private final MailService mailService;
    private final EmailProperties properties;

    public EmailRetryJob(MailService mailService, EmailProperties properties) {
        this.mailService = mailService;
        this.properties = properties;
    }

    /**
     * Cố ý dùng {@code fixedDelayString} thay vì {@code cron}: quét theo chu kỳ
     * để hàng đợi luôn được rút cạn mà không cần tính lịch. {@code initialDelay}
     * tránh việc mọi bản sao cùng quét ngay lúc khởi động.
     */
    @Scheduled(initialDelayString = "${app.email.retry-initial-delay-ms:30000}",
               fixedDelayString = "${app.email.retry-scan-ms:60000}")
    public void retryFailedEmails() {
        if (properties.getRetryBatchSize() <= 0) {
            return;
        }
        try {
            mailService.retryPending();
        } catch (RuntimeException e) {
            // Job hỏng không được làm cả scheduler dừng: các job khác còn cần chạy.
            // Log để cảnh báo, lượt quét sau sẽ thử lại.
            org.slf4j.LoggerFactory.getLogger(EmailRetryJob.class)
                    .error("Worker gửi lại email lỗi: {}", e.getMessage(), e);
        }
    }
}

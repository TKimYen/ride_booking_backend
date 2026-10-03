package com.ridebooking.auth.service;

import com.ridebooking.auth.entity.EmailSendLog;
import com.ridebooking.auth.repository.EmailSendLogRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi và cập nhật hàng đợi gửi email trong transaction RIÊNG.
 *
 * <p>Không thể ghi thẳng bằng repository: hàng đợi phải sống sót cả khi
 * transaction nghiệp vụ rollback, vì chính nó là thứ áp dụng hạn mức gửi. Nếu bị
 * rollback theo thì kẻ spam chỉ cần làm hỏng request là lách được giới hạn.
 *
 * <p>{@code REQUIRES_NEW} còn là bắt buộc cho đường "sau commit": trong callback
 * {@code afterCommit}, EntityManager vẫn còn gắn với transaction vừa kết thúc
 * nên lệnh ghi không thuộc transaction nào và bị bỏ âm thầm — dòng log biến mất
 * mà không có lỗi nào được báo.
 *
 * <p>Thứ tự bắt buộc là <b>ghi PENDING trước, rồi mới gửi</b>. Nếu gửi trước rồi
 * ghi sau, thì khoảnh khắc JVM bị kill giữa hai việc thì email đã đi nhưng
 * không có dấu vết; ngược lại khi ghi trước thì email còn nằm trong hàng đợi và
 * lần quét kế tiếp sẽ gửi lại.
 */
@Component
class EmailSendLogWriter {

    private static final Logger log = LoggerFactory.getLogger(EmailSendLogWriter.class);

    private final EmailSendLogRepository repository;

    EmailSendLogWriter(EmailSendLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Thêm email vào hàng đợi. Trả về id dòng, hoặc {@code null} nếu ghi lỗi (xem
     * {@link #writeTerminal}).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long enqueue(String to, String subject, String kind, String ipAddress,
                        String htmlBody, String textBody, LocalDateTime retryUntil) {
        try {
            return repository.save(
                    EmailSendLog.pending(to, subject, kind, ipAddress, htmlBody, textBody, retryUntil))
                    .getId();
        } catch (RuntimeException e) {
            // Không ghi được hàng đợi thì cũng không được làm hỏng luồng chính.
            log.warn("Không ghi được hàng đợi email: {}", e.getMessage());
            return null;
        }
    }

    /** Gửi thành công: chuyển sang SENT. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long id) {
        update(id, EmailSendLog.STATUS_SENT, null, null);
    }

    /**
     * Gửi lỗi: giữ dòng ở PENDING và đẩy mốc thử lại lên {@code nextAttemptAt}.
     * Dòng KHÔNG bị chuyển sang trạng thái chết, vì đây mới chỉ là lỗi tạm thời.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetryLater(Long id, String reason, LocalDateTime nextAttemptAt) {
        update(id, EmailSendLog.STATUS_PENDING, reason, nextAttemptAt);
    }

    /** Hết số lần thử hoặc token hết hạn: chuyển sang DEAD, không thử nữa. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDead(Long id, String reason) {
        update(id, EmailSendLog.STATUS_DEAD, reason, null);
    }

    private void update(Long id, String status, String reason, LocalDateTime nextAttemptAt) {
        if (id == null) {
            return;
        }
        try {
            EmailSendLog row = repository.findById(id).orElse(null);
            if (row == null) {
                return;
            }
            row.setStatus(status);
            row.setFailureReason(truncate(reason));
            row.setAttempts(row.getAttempts() + 1);
            row.setNextAttemptAt(nextAttemptAt);
            // save(), không phải saveAndFlush(): dòng này thuộc transaction
            // REQUIRES_NEW riêng nên nó sẽ được commit khi method kết thúc, và
            // flush thủ công ở đây chỉ tốn thêm một vòng ghi SQL. Với email gửi
            // lại hàng loạt, đây là khác biệt không nhỏ.
            repository.save(row);
        } catch (RuntimeException e) {
            log.warn("Không cập nhật được trạng thái email #{}: {}", id, e.getMessage());
        }
    }

    /** Dùng cho dòng thuần lịch sử (không vào hàng đợi gửi lại). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeTerminal(String to, String subject, String kind, String status,
                              String failureReason, String ipAddress) {
        try {
            repository.save(new EmailSendLog(to, subject, kind, status,
                    truncate(failureReason), ipAddress, LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.warn("Không ghi được lịch sử gửi email: {}", e.getMessage());
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 490 ? text.substring(0, 490) : text;
    }
}

package com.ridebooking.auth.service;

import com.ridebooking.auth.config.LoginSecurityProperties;
import com.ridebooking.auth.entity.LoginAttempt;
import com.ridebooking.auth.repository.LoginAttemptRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Một lần tăng bộ đếm, chạy trong transaction riêng và khoá dòng đang đọc.
 *
 * <p>Tách riêng khỏi {@link LoginAttemptService} vì cần hai thứ mà một method
 * {@code @Transactional} bình thường không làm được:
 *
 * <ul>
 *   <li><b>Khoá ghi khi đọc</b> — nếu chỉ đọc rồi ghi, các request song song cùng
 *       đọc một giá trị rồi cùng ghi, lần đếm bị đè mất và giới hạn bị lách.
 *   <li><b>Transaction phải là REQUIRES_NEW</b> — khi INSERT đầu tiên va chạm
 *       với một request khác, transaction đó đã bị đánh dấu hỏng; lần thử lại bắt
 *       buộc phải bắt đầu từ một transaction sạch mới đọc được dòng vừa được tạo.
 * </ul>
 */
@Component
class LoginAttemptWriter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptWriter.class);

    private final LoginAttemptRepository repository;
    private final LoginSecurityProperties properties;

    LoginAttemptWriter(LoginAttemptRepository repository, LoginSecurityProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increment(String email, String ip, LocalDateTime now, LocalDateTime windowStart) {
        // findForUpdate khoá dòng nếu có; nếu chưa có thì tạo mới, va chạm
        // UNIQUE sẽ ném DataIntegrityViolationException ra ngoài transaction.
        LoginAttempt attempt = repository.findForUpdate(email, ip).orElseGet(() -> {
            LoginAttempt created = new LoginAttempt();
            created.setEmail(email);
            created.setIpAddress(ip);
            return created;
        });

        boolean staleWindow = attempt.getFirstFailedAt() == null
                || attempt.getFirstFailedAt().isBefore(windowStart);

        if (staleWindow) {
            // Sang cửa sổ mới: bộ đếm cũ không còn ý nghĩa, bắt đầu lại từ 0.
            attempt.setFirstFailedAt(now);
            attempt.setFailedCount(0);
        }
        attempt.setEmail(email);
        attempt.setIpAddress(ip);
        attempt.setFailedCount(attempt.getFailedCount() + 1);
        attempt.setLastFailedAt(now);

        if (attempt.getFailedCount() >= properties.getMaxFailedAttempts()) {
            attempt.setLockedUntil(now.plusMinutes(properties.getLockMinutes()));
            log.warn("Khoá cặp (email={}, IP={}) sau {} lần sai. "
                            + "Đổi mật khẩu hoặc xác minh email sẽ mở khoá ngay.",
                    email, ip, attempt.getFailedCount());
        }
        repository.saveAndFlush(attempt);
    }
}

package com.ridebooking.auth.service;

import com.ridebooking.auth.config.LoginSecurityProperties;
import com.ridebooking.auth.entity.LoginAttempt;
import com.ridebooking.auth.exception.AccountLockedException;
import com.ridebooking.auth.repository.LoginAttemptRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quyết định khoá / cho thử, theo hai lớp:
 *
 * <ol>
 *   <li><b>Giới hạn tổng theo IP</b> — một IP không được sai quá N lần trong cửa sổ
 *       thời gian, bất kể thử tài khoản nào. Chặn kiểu tấn công rải mật khẩu
 *       (password spray) và chặn việc dò email tồn tại hàng loạt.
 *   <li><b>Giới hạn theo cặp (email, IP)</b> — tránh việc ai đó cố tình khoá tài
 *       khoản của người khác chỉ bằng cách gửi 5 mật khẩu sai.
 * </ol>
 *
 * <p>Mọi thao tác ghi đều tự commit riêng (không bọc transaction bao ngoài), vì
 * {@code AuthService#login} ném exception sau khi đã tăng bộ đếm — nếu gộp
 * transaction thì RuntimeException sẽ rollback mất bộ đếm và chống brute-force
 * trở lại thành vô hiệu.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /**
     * Số lần thử lại khi INSERT va chạm với request song song. Ba là đủ: mỗi
     * lần thử lại đọc dòng đã tồn tại nên lần sau gần như chắc chắn ghi được.
     */
    private static final int MAX_INSERT_RETRIES = 3;

    private final LoginAttemptRepository repository;
    private final LoginSecurityProperties properties;
    private final LoginAttemptWriter writer;

    public LoginAttemptService(LoginAttemptRepository repository,
                               LoginSecurityProperties properties,
                               LoginAttemptWriter writer) {
        this.repository = repository;
        this.properties = properties;
        this.writer = writer;
    }

    /**
     * Kiểm tra trước khi kiểm tra mật khẩu.
     *
     * @throws AccountLockedException nếu IP đã vượt giới hạn tổng, hoặc cặp
     *                               (email, ip) đang bị khoá.
     */
    @Transactional(readOnly = true)
    public void checkAllowed(String email, String ipAddress) {
        String ip = normalizeIp(ipAddress);
        long totalFromIp = repository.countFailuresFromIpSince(ip, windowStart());
        if (totalFromIp >= properties.getMaxFailedAttemptsPerIp()) {
            long retryAfter = properties.getIpWindowMinutes() * 60L;
            log.warn("Chặn IP {}: {} lần sai trong {} phút (ngưỡng {})",
                    ip, totalFromIp, properties.getIpWindowMinutes(), properties.getMaxFailedAttemptsPerIp());
            throw new AccountLockedException(
                    "Quá nhiều lần đăng nhập sai từ thiết bị này. Vui lòng thử lại sau "
                            + properties.getIpWindowMinutes() + " phút", retryAfter);
        }

        repository.findByEmailAndIpAddress(email, ip).ifPresent(attempt -> {
            if (attempt.isLocked()) {
                long retryAfter = Duration.between(LocalDateTime.now(), attempt.getLockedUntil()).toSeconds();
                log.warn("Chặn đăng nhập: cặp ({} , IP {}) đang khoá, còn {} giây",
                        email, ip, Math.max(0, retryAfter));
                throw new AccountLockedException(
                        "Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau "
                                + Math.max(1, retryAfter / 60) + " phút",
                        Math.max(1, retryAfter));
            }
        });
    }

    /** Ghi nhận một lần đăng nhập sai, khoá cặp (email, ip) khi vượt ngưỡng. */
    public void registerFailure(String email, String ipAddress) {
        String ip = normalizeIp(ipAddress);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = windowStart();

        // Cố tình KHÔNG @Transactional: mỗi lần ghi phải là một transaction
        // riêng (xem LoginAttemptWriter) để lỗi UNIQUE không làm hỏng cả lượt
        // thử lại.
        for (int attempt = 1; ; attempt++) {
            try {
                writer.increment(email, ip, now, since);
                return;
            } catch (DataIntegrityViolationException e) {
                // Request đầu tiên cho cặp này đến đồng thời: cả hai đều chưa
                // thấy dòng nào nên cùng INSERT, một bên thắng, bên kia vi phạm
                // UNIQUE. Lần thử lại sẽ khoá và cộng tiếp vào dòng đã có.
                if (attempt >= MAX_INSERT_RETRIES) {
                    log.error("Không ghi được bộ đếm cho cặp ({} , IP {}) sau {} lần thử",
                            email, ip, attempt, e);
                    throw e;
                }
                log.debug("Va chạm khi tạo dòng cho cặp ({} , IP {}), thử lại", email, ip);
            }
        }
    }

    /** Đăng nhập đúng: xoá bộ đếm của cặp này. */
    @Transactional
    public void registerSuccess(String email, String ipAddress) {
        repository.findByEmailAndIpAddress(email, normalizeIp(ipAddress))
                .ifPresent(attempt -> {
                    log.debug("Đăng nhập đúng, xoá bộ đếm của {}", email);
                    repository.delete(attempt);
                });
    }

    /**
     * Mở khoá tài khoản: xoá mọi bộ đếm của email ở mọi IP.
     * Gọi khi đổi mật khẩu, xác minh email, hoặc đổi email — tức là những hành
     * động mà chỉ người đã chứng minh mình là chủ tài khoản mới làm được.
     */
    @Transactional
    public void clearForEmail(String email) {
        int removed = repository.deleteByEmail(email);
        if (removed > 0) {
            log.info("Mở khoá tài khoản {}: đã xoá {} bộ đếm đăng nhập sai", email, removed);
        }
    }

    /** Dọn bộ đếm cũ đã quá hạn. */
    @Transactional
    public int purgeOlderThan(long days) {
        return repository.deleteOlderThan(LocalDateTime.now().minusDays(days));
    }

    private LocalDateTime windowStart() {
        return LocalDateTime.now().minusMinutes(properties.getIpWindowMinutes());
    }

    private String normalizeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            // Không lấy được IP (ví dụ gọi nội bộ) thì gom vào một nhóm chung,
            // để cơ chế giới hạn vẫn hoạt động thay vì bị vòng vây.
            return "unknown";
        }
        return ipAddress.trim();
    }
}

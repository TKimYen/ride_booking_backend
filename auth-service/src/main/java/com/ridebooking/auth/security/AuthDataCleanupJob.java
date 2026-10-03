package com.ridebooking.auth.security;

import com.ridebooking.auth.config.EmailProperties;
import com.ridebooking.auth.config.LoginSecurityProperties;
import com.ridebooking.auth.service.EmailTokenService;
import com.ridebooking.auth.service.LoginAttemptService;
import com.ridebooking.auth.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dọn dữ liệu phụ sinh ra từ các luồng bảo mật. Không dọn thì cả ba bảng đều
 * phình vô hạn theo thời gian.
 *
 * <ul>
 *   <li>{@code email_tokens} — token hết hạn từ lâu, không còn giá trị dùng.</li>
 *   <li>{@code login_attempts} — bộ đếm sai cũ, đã nằm ngoài mọi cửa sổ thời gian.</li>
 *   <li>{@code email_send_log} — lịch sử gửi để chống spam, chỉ giữ vài ngày.</li>
 * </ul>
 */
@Component
public class AuthDataCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AuthDataCleanupJob.class);

    private final EmailTokenService emailTokenService;
    private final LoginAttemptService loginAttemptService;
    private final MailService mailService;
    private final EmailProperties emailProperties;
    private final LoginSecurityProperties loginSecurityProperties;

    public AuthDataCleanupJob(EmailTokenService emailTokenService,
                              LoginAttemptService loginAttemptService,
                              MailService mailService,
                              EmailProperties emailProperties,
                              LoginSecurityProperties loginSecurityProperties) {
        this.emailTokenService = emailTokenService;
        this.loginAttemptService = loginAttemptService;
        this.mailService = mailService;
        this.emailProperties = emailProperties;
        this.loginSecurityProperties = loginSecurityProperties;
    }

    @Scheduled(cron = "${app.cleanup.cron:0 15 3 * * *}")
    public void purge() {
        int tokens = emailTokenService.purgeExpired();
        int attempts = loginAttemptService.purgeOlderThan(7);
        int emails = mailService.purgeHistory();

        if (tokens + attempts + emails > 0) {
            log.info("Dọn dữ liệu: {} email token, {} bộ đếm đăng nhập, {} lịch sử gửi email",
                    tokens, attempts, emails);
        }
    }
}

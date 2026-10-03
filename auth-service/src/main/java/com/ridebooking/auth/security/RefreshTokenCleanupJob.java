package com.ridebooking.auth.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bảng {@code refresh_tokens} sẽ phình vô hạn nếu không dọn. Xoá token đã hết
 * hạn từ lâu mỗi đêm — token còn hiệu lực không bị đụng tới.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenCleanupJob(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Scheduled(cron = "${app.jwt.refresh-token-purge-cron:0 0 3 * * *}")
    public void purgeExpired() {
        int deleted = refreshTokenService.purgeExpired();
        if (deleted > 0) {
            log.info("Đã dọn {} refresh token hết hạn", deleted);
        }
    }
}
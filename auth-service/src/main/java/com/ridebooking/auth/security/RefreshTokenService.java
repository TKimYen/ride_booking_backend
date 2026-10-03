package com.ridebooking.auth.security;

import com.ridebooking.auth.config.JwtProperties;
import com.ridebooking.auth.entity.RefreshToken;
import com.ridebooking.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấp / xoay vòng / thu hồi refresh token.
 *
 * <p>Refresh token là chuỗi ngẫu nhiên 256 bit (không phải JWT) vì không cần
 * đọc nội dung, chỉ cần so khớp. Database chỉ lưu SHA-256 của nó.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Cấp refresh token mới cho một phiên đăng nhập. */
    @Transactional
    public String issue(Integer userId, String userAgent, String ipAddress) {
        String rawToken = generateRawToken();
        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(LocalDateTime.now().plusDays(properties.getRefreshTokenDays()));
        entity.setUserAgent(truncate(userAgent));
        entity.setIpAddress(truncate(ipAddress));
        repository.save(entity);
        return rawToken;
    }

    /**
     * Kiểm tra và xoay vòng.
     *
     * @return userId nếu hợp lệ, hoặc lý do từ chối
     */
    @Transactional
    public RefreshResult rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return RefreshResult.rejected(RefreshResult.Reason.INVALID);
        }

        Optional<RefreshToken> found = repository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return RefreshResult.rejected(RefreshResult.Reason.INVALID);
        }

        RefreshToken stored = found.get();

        // Token đã bị thu hồi nhưng vẫn được dùng lại => token đã bị lộ.
        // Thu hồi toàn bộ phiên của user cho an toàn.
        if (stored.getRevokedAt() != null) {
            repository.revokeAllForUser(stored.getUserId(), LocalDateTime.now());
            return RefreshResult.rejected(RefreshResult.Reason.REUSE_DETECTED);
        }

        if (!stored.isActive()) {
            return RefreshResult.rejected(RefreshResult.Reason.EXPIRED);
        }

        stored.setRevokedAt(LocalDateTime.now());
        repository.save(stored);

        return RefreshResult.accepted(stored.getUserId());
    }

    /**
     * Đăng xuất: thu hồi token được xuất trình bày.
     *
     * @param userId chủ sở hữu của token; bỏ qua nếu null (nội bộ, ví dụ đổi mật khẩu).
     *                Token thuộc tài khoản khác sẽ không bị thu hồi để một user
     *                đã đăng nhập không thể vô hiệu hoá phiên của người khác chỉ
     *                bằng cách đoán/đánh cắp token của họ.
     * @return số token thực sự bị thu hồi (0 hoặc 1)
     */
    @Transactional
    public int revokeForUser(String rawToken, Integer userId) {
        if (rawToken == null || rawToken.isBlank()) {
            return 0;
        }
        Optional<RefreshToken> found = repository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return 0;
        }
        RefreshToken stored = found.get();
        if (userId != null && !userId.equals(stored.getUserId())) {
            log.warn("Từ chối thu hồi refresh token: token thuộc userId={} nhưng được gửi bởi userId={}",
                    stored.getUserId(), userId);
            return 0;
        }
        if (stored.getRevokedAt() != null) {
            return 0;
        }
        stored.setRevokedAt(LocalDateTime.now());
        repository.save(stored);
        return 1;
    }

    /** Đăng xuất khỏi mọi thiết bị - dùng khi nghi ngờ tài khoản bị chiếm. */
    @Transactional
    public int revokeAll(Integer userId) {
        return repository.revokeAllForUser(userId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<RefreshToken> activeSessions(Integer userId) {
        return repository.findByUserIdAndRevokedAtIsNull(userId);
    }

    public long getRefreshTokenDays() {
        return properties.getRefreshTokenDays();
    }

    @Transactional
    public int purgeExpired() {
        return repository.deleteExpired(LocalDateTime.now().minusDays(30));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Thiết bị không hỗ trợ SHA-256", ex);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    /** Kết quả xoay vòng refresh token. */
    public record RefreshResult(Integer userId, Reason reason, boolean accepted) {

        public enum Reason {
            OK,
            /** Token không tồn tại trong DB, sai định dạng, hoặc đã hết hạn. */
            INVALID,
            EXPIRED,
            /** Token đã bị thu hồi lại được dùng - dấu hiệu bị đánh cắp. */
            REUSE_DETECTED
        }

        static RefreshResult accepted(Integer userId) {
            return new RefreshResult(userId, Reason.OK, true);
        }

        static RefreshResult rejected(Reason reason) {
            return new RefreshResult(null, reason, false);
        }
    }
}
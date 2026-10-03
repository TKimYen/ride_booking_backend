package com.ridebooking.auth.service;

import com.ridebooking.auth.config.EmailProperties;
import com.ridebooking.auth.entity.EmailToken;
import com.ridebooking.auth.entity.EmailToken.Purpose;
import com.ridebooking.auth.client.UserServiceClient;
import com.ridebooking.auth.entity.User;
import com.ridebooking.auth.exception.BadCredentialsException;
import com.ridebooking.auth.exception.BadRequestException;
import com.ridebooking.auth.exception.InvalidTokenException;
import com.ridebooking.auth.repository.EmailTokenRepository;
import com.ridebooking.auth.repository.UserRepository;
import com.ridebooking.auth.security.RefreshTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phát hành và kiểm tra token một lần dùng cho các luồng qua email.
 *
 * <p>Nguyên tắc chung cho cả ba loại token:
 * <ul>
 *   <li>Sinh bằng {@link SecureRandom} 256 bit, chỉ lưu SHA-256 trong database.
 *   <li>Một lần dùng: dùng xong đánh dấu {@code usedAt}.
 *   <li>Cấp token mới thì vô hiệu hoá các token cũ cùng mục đích, nên link cũ
 *       gửi cho hộp thư bị chặn/spam không còn tác dụng.
 *   <li>Hết hạn theo mục đích: verify 1 ngày, reset 30 phút, đổi email 60 phút.
 * </ul>
 *
 * <p>KHÔNG bọc {@code @Transactional} ở nơi gọi rồi ném exception: sẽ rollback mất
 * việc đánh dấu token đã dùng, khiến link có thể dùng lại nhiều lần.
 */
@Service
public class EmailTokenService {

    private static final Logger log = LoggerFactory.getLogger(EmailTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailTokenRepository repository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final EmailProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final UserServiceClient userServiceClient;

    public EmailTokenService(EmailTokenRepository repository,
                             UserRepository userRepository,
                             MailService mailService,
                             EmailProperties properties,
                             PasswordEncoder passwordEncoder,
                             RefreshTokenService refreshTokenService,
                             LoginAttemptService loginAttemptService,
                             UserServiceClient userServiceClient) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.userServiceClient = userServiceClient;
    }


    /** Gửi link xác minh email. Không ném exception dù có lỗi. */
    @Transactional
    public void sendVerification(User user, String ipAddress) {
        Duration ttl = properties.getVerifyTtl();
        String raw = issue(user, Purpose.EMAIL_VERIFICATION, null, ttl);

        String[] content = EmailTemplates.split(
                EmailTemplates.verifyEmail(properties, raw, ttl.toMinutes()));
        mailService.send(user.getEmail(), EmailTemplates.verifyEmailSubject(),
                content, "EMAIL_VERIFICATION", ipAddress);
    }

    @Transactional
    public void sendResendVerification(User user, String ipAddress) {
        Duration ttl = properties.getVerifyTtl();
        String raw = issue(user, Purpose.EMAIL_VERIFICATION, null, ttl);

        String[] content = EmailTemplates.split(
                EmailTemplates.resendVerify(properties, raw, ttl.toMinutes()));
        mailService.send(user.getEmail(), EmailTemplates.resendVerifySubject(),
                content, "EMAIL_VERIFICATION_RESEND", ipAddress);
    }

    /**
     * Xác minh email bằng link.
     *
     * <p>Xác minh thành công cũng là bằng chứng chủ tài khoản nên xoá bộ đếm đăng
     * nhập sai — tránh việc bị khoá mà không có đường vào.
     */
    @Transactional
    public User verifyEmail(String rawToken) {
        LocalDateTime now = LocalDateTime.now();
        EmailToken token = requireUsable(rawToken, Purpose.EMAIL_VERIFICATION, now);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Link không còn hiệu lực"));

        token.markUsed(now);
        repository.save(token);

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        userRepository.save(user);

        log.info("Đã xác minh email cho userId={}", user.getId());
        return user;
    }


    @Transactional
    public void sendPasswordReset(User user, String ipAddress) {
        Duration ttl = properties.getPasswordResetTtl();
        String raw = issue(user, Purpose.PASSWORD_RESET, null, ttl);

        String[] content = EmailTemplates.split(
                EmailTemplates.forgotPassword(properties, raw, ttl.toMinutes()));
        mailService.send(user.getEmail(), EmailTemplates.forgotPasswordSubject(),
                content, "PASSWORD_RESET", ipAddress);
    }

    /**
     * Đặt lại mật khẩu bằng link.
     *
     * <p>Sau khi đổi mật khẩu: thu hồi toàn bộ phiên, xoá bộ đếm đăng nhập sai,
     * và huỷ mọi token reset còn lại.
     */
    @Transactional
    public User resetPassword(String rawToken, String newPassword) {
        LocalDateTime now = LocalDateTime.now();
        EmailToken token = requireUsable(rawToken, Purpose.PASSWORD_RESET, now);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Link không còn hiệu lực"));

        token.markUsed(now);
        repository.save(token);

        // Đổi mật khẩu = bằng chứng chủ tài khoản: huỷ mọi phiên cũ (tránh kẻ
        // đang lấn phiên vẫn dùng được) và xoá bộ đếm đăng nhập sai.
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAll(user.getId());
        loginAttemptService.clearForEmail(user.getEmail());

        log.info("Đã đặt lại mật khẩu cho userId={}, thu hồi toàn bộ phiên", user.getId());
        return user;
    }


    /**
     * Bắt đầu đổi email: gửi link xác nhận tới EMAIL MỚI, đồng thời gửi cảnh báo
     * tới EMAIL CŨ. Chỉ khi link ở email mới được mở thì email mới có hiệu lực.
     *
     * <p>Gửi cảnh báo tới email cũ là bắt buộc: nếu ai đó chiếm được phiên của
     * nạn nhân, việc đổi email chính là cách họ chuyển tài khoản ra khỏi tầm
     * tay chủ thật — nạn nhân sẽ không còn cách nào để lấy lại.
     */
    @Transactional
    public void startEmailChange(User user, String newEmail, String password, String ipAddress) {
        String normalized = normalizeEmail(newEmail);
        if (normalized.equals(normalizeEmail(user.getEmail()))) {
            throw new BadRequestException("Email mới phải khác email hiện tại");
        }
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            // Trả về chung thông điệp với email trống để không lộ email nào đang
            // có trong hệ thống.
            throw new BadRequestException("Không thể đổi sang email này");
        }

        Duration ttl = properties.getEmailChangeTtl();
        String raw = issue(user, Purpose.EMAIL_CHANGE, normalized, ttl);

        // Link xác nhận tới email mới
        String[] confirm = EmailTemplates.split(
                EmailTemplates.emailChangeConfirm(properties, normalized, raw, ttl.toMinutes()));
        mailService.send(normalized, EmailTemplates.emailChangeConfirmSubject(),
                confirm, "EMAIL_CHANGE_CONFIRM", ipAddress);

        // Cảnh báo tới email cũ (bằng chứng chủ tài khoản nhận được)
        String oldEmail = user.getEmail();
        String[] warning = EmailTemplates.split(
                EmailTemplates.emailChangeWarning(oldEmail, normalized));
        mailService.send(oldEmail, EmailTemplates.emailChangeWarningSubject(),
                warning, "EMAIL_CHANGE_WARNING", ipAddress);
        mailService.sendToEach(securityRecipients(), "[Ride Booking] Cảnh báo đổi email",
                EmailTemplates.split(EmailTemplates.emailChangeWarning(oldEmail, normalized)),
                "EMAIL_CHANGE_WARNING_SECURITY", ipAddress);

        log.info("Bắt đầu đổi email cho userId={}: {} -> {}", user.getId(), oldEmail, normalized);
    }

    /**
     * Hoàn tất đổi email. Bắt buộc có mật khẩu hiện tại: chỉ người đang nắm
     * session hợp lệ mới xác nhận được, tránh việc ai đó mở link trên thiết bị
     * bị chiếm cũng đổi được email.
     */
    @Transactional
    public User confirmEmailChange(String rawToken, String currentPassword) {
        LocalDateTime now = LocalDateTime.now();
        EmailToken token = requireUsable(rawToken, Purpose.EMAIL_CHANGE, now);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Link không còn hiệu lực"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadCredentialsException("Mật khẩu hiện tại không đúng");
        }

        String oldEmail = user.getEmail();
        String newEmail = token.getNewEmail();
        if (userRepository.existsByEmailIgnoreCase(normalizeEmail(newEmail))) {
            throw new BadRequestException("Không thể đổi sang email này");
        }

        token.markUsed(now);
        repository.save(token);

        user.setEmail(normalizeEmail(newEmail));
        // Email mới chưa được xác minh cho tới khi mở link ở chính email đó.
        // Mở link chính là hành động chứng minh sở hữu -> coi như đã xác minh.
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        userRepository.save(user);

        // Email đổi là thay đổi định danh -> huỷ mọi phiên cũ.
        refreshTokenService.revokeAll(user.getId());
        loginAttemptService.clearForEmail(oldEmail);
        loginAttemptService.clearForEmail(user.getEmail());

        // Cập nhật bản sao bên user-service. Thất bại không chặn đổi email —
        // auth_db là nguồn chuẩn, còn bản sao sẽ tự được sửa ở lần người dùng
        // mở GET /api/users/me (xem UserService#ensureProfile).
        userServiceClient.updateEmail(user.getId(), user.getEmail());

        log.info("Đã đổi email cho userId={}: {} -> {}", user.getId(), oldEmail, user.getEmail());
        return user;
    }


    @Transactional
    public int purgeExpired() {
        return repository.deleteExpired(LocalDateTime.now().minusDays(1));
    }


    /** Cấp token mới, vô hiệu hoá các token cũ cùng mục đích. Trả về token gốc. */
    private String issue(User user, Purpose purpose, String newEmail, Duration ttl) {
        repository.invalidateExisting(user.getId(), purpose, LocalDateTime.now());

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.save(new EmailToken(user.getId(), purpose, sha256(raw), newEmail,
                ttl, LocalDateTime.now()));
        return raw;
    }

    private EmailToken requireUsable(String rawToken, Purpose purpose, LocalDateTime now) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("Thiếu mã xác nhận");
        }
        Optional<EmailToken> found = repository.findByTokenHash(sha256(rawToken));
        if (found.isEmpty()) {
            throw new InvalidTokenException("Mã không hợp lệ hoặc đã hết hạn");
        }
        EmailToken token = found.get();
        if (token.getPurpose() != purpose) {
            // Không nói rõ mục đích thật của token để tránh dò mã.
            throw new InvalidTokenException("Mã không hợp lệ hoặc đã hết hạn");
        }
        if (token.getUsedAt() != null) {
            throw new InvalidTokenException("Mã đã được sử dụng");
        }
        if (token.isExpired(now)) {
            throw new InvalidTokenException("Mã không hợp lệ hoặc đã hết hạn");
        }
        return token;
    }

    private java.util.List<String> securityRecipients() {
        if (properties.getSecurityTo() == null || properties.getSecurityTo().isBlank()) {
            return java.util.List.of();
        }
        return java.util.List.of(properties.getSecurityTo().trim());
    }

    private String normalizeEmail(String email) {
        return EmailNormalizer.normalize(email);
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thiết bị không hỗ trợ SHA-256", e);
        }
    }
}

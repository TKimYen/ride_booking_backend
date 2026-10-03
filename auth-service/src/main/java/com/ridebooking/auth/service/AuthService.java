package com.ridebooking.auth.service;

import com.ridebooking.auth.client.UserServiceClient;
import com.ridebooking.auth.client.UserServiceClientProperties.CreateProfileRequest;
import com.ridebooking.auth.client.UserServiceClientProperties.ProfileView;
import com.ridebooking.auth.config.JwtProperties;
import com.ridebooking.auth.dto.AuthResponse;
import com.ridebooking.auth.dto.ChangeEmailRequest;
import com.ridebooking.auth.dto.ChangePasswordRequest;
import com.ridebooking.auth.dto.ConfirmEmailChangeRequest;
import com.ridebooking.auth.dto.CreateUserRequest;
import com.ridebooking.auth.dto.EmailVerifiedResponse;
import com.ridebooking.auth.dto.ResetPasswordRequest;
import com.ridebooking.auth.dto.LoginRequest;
import com.ridebooking.auth.dto.RegisterRequest;
import com.ridebooking.auth.dto.UserResponse;
import com.ridebooking.auth.entity.User;
import com.ridebooking.auth.enums.Role;
import com.ridebooking.auth.config.EmailProperties;
import com.ridebooking.auth.config.LoginSecurityProperties;
import com.ridebooking.auth.exception.BadCredentialsException;
import com.ridebooking.auth.exception.EmailAlreadyExistsException;
import com.ridebooking.auth.exception.InvalidRefreshTokenException;
import com.ridebooking.auth.exception.EmailNotVerifiedException;
import com.ridebooking.auth.exception.ResourceNotFoundException;
import com.ridebooking.auth.exception.TooManyEmailRequestsException;
import com.ridebooking.auth.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import com.ridebooking.auth.security.JwtService;
import com.ridebooking.auth.security.RefreshTokenService;
import com.ridebooking.auth.security.RefreshTokenService.RefreshResult;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đăng ký, đăng nhập, đổi mật khẩu, refresh token, đăng xuất và quản lý tài khoản.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * Thông điệp trả về cho mọi yêu cầu gửi email tự động, bất kể email có tồn tại,
     * đã xác minh hay không. Trả lời khác nhau giữa các trường hợp chính là cách
     * đơn giản nhất để lộ danh sách email đã đăng ký.
     */
    private static final String GENERIC_EMAIL_MESSAGE =
            "Nếu tài khoản tồn tại và đã xác minh email, chúng tôi đã gửi link qua email. "
                    + "Vui lòng kiểm tra hộp thư (kể cả thư rác).";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserServiceClient userServiceClient;
    private final LoginAttemptService loginAttemptService;
    private final EmailTokenService emailTokenService;
    private final MailService mailService;
    private final EmailProperties emailProperties;
    private final LoginSecurityProperties loginSecurityProperties;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       UserServiceClient userServiceClient,
                       LoginAttemptService loginAttemptService,
                       EmailTokenService emailTokenService,
                       MailService mailService,
                       EmailProperties emailProperties,
                       LoginSecurityProperties loginSecurityProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userServiceClient = userServiceClient;
        this.loginAttemptService = loginAttemptService;
        this.emailTokenService = emailTokenService;
        this.mailService = mailService;
        this.emailProperties = emailProperties;
        this.loginSecurityProperties = loginSecurityProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String userAgent, String ipAddress) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);

        User saved = userRepository.save(user);
        log.info("Đăng ký tài khoản mới: {}", saved.getEmail());

        // Gửi link xác minh. Không bọc try/catch vì MailService đã tự nuốt lỗi:
        // hỏng SMTP không được làm hỏng luôn đăng ký.
        emailTokenService.sendVerification(saved, ipAddress);

        syncProfile(saved, request.fullName().trim(), request.phone(), request.dob(), request.gender());
        return buildAuthResponse(saved, userAgent, ipAddress);
    }

    // KHÔNG bọc @Transactional: hàm này ném BadCredentialsException / AccountLockedException
    // sau khi đã tăng bộ đếm, mà RuntimeException sẽ rollback toàn bộ transaction ->
    // bộ đếm sai mật khẩu không bao giờ được ghi xuống DB, chống brute-force vô hiệu.
    // Mỗi thao tác ghi đã tự commit riêng qua repository.
    public AuthResponse login(LoginRequest request, String userAgent, String ipAddress) {
        String email = normalizeEmail(request.email());

        // Lớp 1: giới hạn tổng theo IP, và giới hạn theo cặp (email, IP).
        // Ném trước khi đụng tới database user để chặn sớm cả dò email tồn tại.
        loginAttemptService.checkAllowed(email, ipAddress);

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        // Email không tồn tại: vẫn phải tốn thời gian tương đương để không lộ
        // tài khoản nào đã đăng ký (timing attack), và vẫn phải ghi nhận lần sai
        // để IP này bị giới hạn.
        if (user == null) {
            passwordEncoder.encode(request.password());
            loginAttemptService.registerFailure(email, ipAddress);
            throw new BadCredentialsException("Email hoặc mật khẩu không đúng");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginAttemptService.registerFailure(email, ipAddress);
            throw new BadCredentialsException("Email hoặc mật khẩu không đúng");
        }

        // Mật khẩu đúng: chỉ lúc này mới nói cho biết email chưa xác minh, để
        // không mở đường dò email tồn tại.
        if (emailProperties.isRequireVerifiedForLogin() && !user.isEmailVerified()) {
            throw new EmailNotVerifiedException(
                    "Email chưa được xác minh. Vui lòng kiểm tra hộp thư để lấy mã xác minh");
        }

        loginAttemptService.registerSuccess(email, ipAddress);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Đăng nhập thành công: {}", user.getEmail());
        // Refresh bản sao role bên user-service (xem UserService#upsertProfile).
        syncProfile(user, user.getFullName(), null, null, null);
        return buildAuthResponse(user, userAgent, ipAddress);
    }

    // KHÔNG bọc @Transactional: khi phát hiện tái sử dụng token, rotate() đã thu hồi
    // toàn bộ phiên rồi hàm ném InvalidRefreshTokenException. Nếu bọc transaction,
    // lệnh thu hồi sẽ bị rollback và tín hiệu "token bị đánh cắp" mất tác dụng.
    public AuthResponse refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        RefreshResult result = refreshTokenService.rotate(rawRefreshToken);

        if (!result.accepted()) {
            log.warn("Từ chối refresh token: {} (userId={})", result.reason(), result.userId());
            throw new InvalidRefreshTokenException(
                    switch (result.reason()) {
                        case REUSE_DETECTED ->
                                "Refresh token đã bị thu hồi trước đó. Toàn bộ phiên đăng nhập đã bị vô hiệu, vui lòng đăng nhập lại";
                        case EXPIRED -> "Refresh token đã hết hạn, vui lòng đăng nhập lại";
                        default -> "Refresh token không hợp lệ, vui lòng đăng nhập lại";
                    });
        }

        return buildAuthResponse(findById(result.userId()), userAgent, ipAddress);
    }

    @Transactional
    public void logout(String rawRefreshToken, Integer userId) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            int revoked = refreshTokenService.revokeForUser(rawRefreshToken, userId);
            if (revoked == 1) {
                log.info("Đăng xuất: đã thu hồi 1 refresh token của userId={}", userId);
            } else {
                log.info("Đăng xuất: userId={} gửi refresh token không thuộc về mình hoặc đã hết hiệu lực, bỏ qua", userId);
            }
            return;
        }
        // Không có refresh token thì đăng xuất toàn bộ phiên của tài khoản.
        int revoked = refreshTokenService.revokeAll(userId);
        log.info("Đăng xuất toàn bộ phiên của userId={}, đã thu hồi {} token", userId, revoked);
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Integer userId) {
        User user = findById(userId);
        return buildUserResponse(user, userServiceClient.fetchProfile(userId));
    }

    @Transactional
    public void changePassword(Integer userId, ChangePasswordRequest request) {
        User user = findById(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Mật khẩu hiện tại không đúng");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BadCredentialsException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Đổi mật khẩu = báo động bất thường, nên đuổi hết phiên đang mở
        // và mở khoá tài khoản (người dùng đã chứng minh mình là chủ).
        refreshTokenService.revokeAll(userId);
        loginAttemptService.clearForEmail(user.getEmail());
        notifyPasswordChanged(user);
        log.info("Đổi mật khẩu thành công cho user id={}, đã thu hồi các phiên cũ", userId);
    }


    /**
     * Gửi lại link xác minh email.
     *
     * <p>Trả về thông điệp giống hệt dù tài khoản có tồn tại hay không, để không
     * biến endpoint này thành công cụ dò email.
     */
    @Transactional
    public String resendVerification(String email, String ipAddress) {
        String normalized = normalizeEmail(email);

        String limited = mailService.checkRateLimit(normalized, ipAddress);
        if (limited != null) {
            throw new TooManyEmailRequestsException(
                    limited, emailProperties.getResendWindowMinutes() * 60L);
        }

        User user = userRepository.findByEmailIgnoreCase(normalized).orElse(null);
        if (user == null) {
            log.info("Yêu cầu gửi lại xác minh cho email không tồn tại (không gửi mail)");
            return GENERIC_EMAIL_MESSAGE;
        }
        if (user.isEmailVerified()) {
            return GENERIC_EMAIL_MESSAGE;
        }
        emailTokenService.sendResendVerification(user, ipAddress);
        return GENERIC_EMAIL_MESSAGE;
    }

    /**
     * Gửi link đặt lại mật khẩu.
     *
     * <p>Yêu cầu email đã xác minh: nếu không, kẻ xấu chỉ cần biết email của bạn
     * là có thể đăng ký trước rồi bấm quên mật khẩu để chiếm tài khoản.
     *
     * <p>Luôn trả về cùng một thông điệp, kể cả khi email không tồn tại, khi chưa
     * xác minh, hay khi đã gửi thành công — nếu phân biệt, chính endpoint này
     * lộ ra danh sách email đã đăng ký.
     */
    @Transactional
    public String forgotPassword(String email, String ipAddress) {
        String normalized = normalizeEmail(email);

        String limited = mailService.checkRateLimit(normalized, ipAddress);
        if (limited != null) {
            throw new TooManyEmailRequestsException(limited, emailProperties.getResendWindowMinutes() * 60L);
        }

        User user = userRepository.findByEmailIgnoreCase(normalized).orElse(null);
        if (user == null) {
            log.info("Yêu cầu quên mật khẩu cho email không tồn tại (không gửi mail)");
            return GENERIC_EMAIL_MESSAGE;
        }
        if (!user.isEmailVerified()) {
            log.info("Yêu cầu quên mật khẩu cho userId={} chưa xác minh email (không gửi mail)",
                    user.getId());
            return GENERIC_EMAIL_MESSAGE;
        }
        emailTokenService.sendPasswordReset(user, ipAddress);
        return GENERIC_EMAIL_MESSAGE;
    }

    /** Bắt đầu đổi email: link xác nhận tới email mới, cảnh báo tới email cũ. */
    @Transactional
    public void changeEmail(Integer userId, ChangeEmailRequest request, String ipAddress) {
        User user = findById(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Mật khẩu hiện tại không đúng");
        }
        emailTokenService.startEmailChange(user, request.newEmail(),
                request.currentPassword(), ipAddress);
    }

    /** Hoàn tất đổi email bằng link ở email mới. */
    @Transactional
    public EmailVerifiedResponse confirmEmailChange(ConfirmEmailChangeRequest request) {
        User user = emailTokenService.confirmEmailChange(request.token(), request.currentPassword());
        return EmailVerifiedResponse.of(true, user.getEmailVerifiedAt(),
                "Đổi email thành công. Vui lòng đăng nhập lại bằng email mới.");
    }

    /** Mở link xác minh email. */
    @Transactional
    public EmailVerifiedResponse verifyEmail(String rawToken) {
        User user = emailTokenService.verifyEmail(rawToken);
        // Xác minh = chứng minh sở hữu hộp thư, nên mở khoá luôn.
        loginAttemptService.clearForEmail(user.getEmail());
        return EmailVerifiedResponse.of(true, user.getEmailVerifiedAt(),
                "Xác minh email thành công.");
    }

    /** Đặt mật khẩu mới bằng link trong email. */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        emailTokenService.resetPassword(request.token(), request.newPassword());
    }

    private void notifyPasswordChanged(User user) {
        try {
            String when = EmailTemplates.formatTime(LocalDateTime.now());
            String[] content = EmailTemplates.split(EmailTemplates.changedPassword(when));
            mailService.send(user.getEmail(), EmailTemplates.changedPasswordSubject(),
                    content, "PASSWORD_CHANGED", null);
            mailService.sendToEach(securityRecipients(),
                    "[Ride Booking] Mật khẩu đã thay đổi", content, "PASSWORD_CHANGED_SECURITY", null);
        } catch (RuntimeException e) {
            log.warn("Không gửi được email thông báo đổi mật khẩu: {}", e.getMessage());
        }
    }

    private java.util.List<String> securityRecipients() {
        String to = emailProperties.getSecurityTo();
        return (to == null || to.isBlank()) ? java.util.List.of() : java.util.List.of(to.trim());
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role() == null ? Role.USER : request.role());

        User saved = userRepository.save(user);
        ProfileView profile = syncProfile(saved, request.fullName().trim(), request.phone(), null, null);

        return buildUserResponse(saved, profile);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(user -> buildUserResponse(user, null))
                .toList();
    }

    /**
     * Admin gỡ khoá: xoá bộ đếm sai ở mọi IP của tài khoản đó.
     *
     * <p>Tôn trọng {@code LOGIN_ADMIN_UNLOCK_ENABLED}. Trước đây biến này được
     * khai báo trong cấu hình và tài liệu nhưng không nơi nào đọc, nên đặt
     * {@code false} không có tác dụng gì — cấu hình chết là tệ hơn không có cấu
     * hình, vì người vận hành tưởng đã tắt được.
     */
    @Transactional
    public UserResponse unlockAccount(Integer userId) {
        if (!loginSecurityProperties.isAdminUnlockEnabled()) {
            log.warn("Từ chối gỡ khoá: LOGIN_ADMIN_UNLOCK_ENABLED đang đặt false");
            throw new AccessDeniedException(
                    "Tính năng gỡ khoá đang bị tắt bằng cấu hình. "
                            + "Đặt LOGIN_ADMIN_UNLOCK_ENABLED=true để bật lại.");
        }
        User user = findById(userId);
        loginAttemptService.clearForEmail(user.getEmail());
        log.info("Admin gỡ khoá tài khoản id={} (email={})", userId, user.getEmail());
        return buildUserResponse(user, null);
    }

    private ProfileView syncProfile(User user, String fullName, String phone,
                                    java.time.LocalDate dob, String gender) {
        ProfileView created = userServiceClient.createProfile(
                new CreateProfileRequest(user.getId(), fullName, user.getEmail(), phone, dob, gender,
                        user.getRole().name()));
        if (created == null) {
            log.warn("user-service chưa nhận hồ sơ của userId={} — sẽ được tại lazy ở lần truy cập đầu", user.getId());
        }
        return created;
    }

    private AuthResponse buildAuthResponse(User user, String userAgent, String ipAddress) {
        String refreshToken = refreshTokenService.issue(user.getId(), userAgent, ipAddress);
        return AuthResponse.of(
                jwtService.generateToken(user),
                jwtService.getExpiresInSeconds(),
                refreshToken,
                refreshTokenService.getRefreshTokenDays() * 86400,
                Instant.now(),
                buildUserResponse(user, userServiceClient.fetchProfile(user.getId())));
    }

    /**
     * Ghép dữ liệu credentials (auth_db) với hồ sơ (user_db). Nếu user-service
     * không trả lời thì dựng response từ dữ liệu auth — vẫn đủ để frontend dùng.
     */
    private UserResponse buildUserResponse(User user, ProfileView profile) {
        String fullName = profile != null && profile.fullName() != null ? profile.fullName() : user.getFullName();
        String phone = profile != null ? profile.phone() : null;
        java.time.LocalDate dob = profile != null ? profile.dob() : null;
        String gender = profile != null ? profile.gender() : null;

        return new UserResponse(
                user.getId(),
                fullName,
                phone,
                user.getEmail(),
                dob,
                gender,
                user.getRole(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
    }

    private User findById(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + userId));
    }

    private String normalizeEmail(String email) {
        return EmailNormalizer.normalize(email);
    }
}
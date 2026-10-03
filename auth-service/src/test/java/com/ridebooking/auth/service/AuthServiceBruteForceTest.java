package com.ridebooking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ridebooking.auth.client.UserServiceClient;
import com.ridebooking.auth.config.EmailProperties;
import com.ridebooking.auth.config.JwtProperties;
import com.ridebooking.auth.config.LoginSecurityProperties;
import com.ridebooking.auth.dto.ChangePasswordRequest;
import com.ridebooking.auth.dto.LoginRequest;
import com.ridebooking.auth.entity.RefreshToken;
import com.ridebooking.auth.entity.User;
import com.ridebooking.auth.enums.Role;
import com.ridebooking.auth.exception.AccountLockedException;
import com.ridebooking.auth.exception.BadCredentialsException;
import com.ridebooking.auth.exception.InvalidRefreshTokenException;
import com.ridebooking.auth.repository.EmailSendLogRepository;
import com.ridebooking.auth.repository.EmailTokenRepository;
import com.ridebooking.auth.repository.RefreshTokenRepository;
import com.ridebooking.auth.repository.UserRepository;
import com.ridebooking.auth.security.JwtService;
import com.ridebooking.auth.security.RefreshTokenService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Chống brute-force theo CẶP (email, IP).
 *
 * <p>Điểm cốt lõi: một IP kẻ tấn công chỉ bị khoá với email nó đang dò, KHÔNG khoá
 * tài khoản của nạn nhân. Nếu khoá theo tài khoản, chỉ cần biết email của ai đó là
 * gọi 5 lần sai mật khẩu là khoá trọn đời tài khoản đó — tự tạo DoS.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceBruteForceTest {

    private static final String EMAIL = "huy@test.com";
    private static final String GOOD_PASSWORD = "matkhau123";
    private static final String ATTACKER_IP = "203.0.113.9";
    private static final String VICTIM_IP = "198.51.100.7";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailTokenRepository emailTokenRepository;

    @Mock
    private EmailSendLogRepository emailSendLogRepository;

    private InMemoryLoginAttempts attempts;

    private AuthService authService;
    private PasswordEncoder passwordEncoder;
    private User user;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("ride-booking-microservices-dev-secret-key-change-me-0123456789");

        lenient().when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userServiceClient.fetchProfile(anyInt())).thenReturn(null);
        lenient().when(userServiceClient.createProfile(any())).thenReturn(null);

        LoginSecurityProperties security = new LoginSecurityProperties();
        security.setMaxFailedAttempts(3);
        security.setLockMinutes(15);
        security.setMaxFailedAttemptsPerIp(10);
        security.setIpWindowMinutes(15);

        attempts = new InMemoryLoginAttempts();
        LoginAttemptService loginAttemptService = new LoginAttemptService(attempts.repository(), security, attempts.writer(security));

        RefreshTokenService refreshTokenService =
                new RefreshTokenService(refreshTokenRepository, jwtProperties);
        EmailProperties emailProperties = new EmailProperties();
        MailService mailService = new MailService(null, emailSendLogRepository, emailProperties,
                new EmailSendLogWriter(emailSendLogRepository));
        EmailTokenService emailTokenService = new EmailTokenService(emailTokenRepository, userRepository,
                mailService, emailProperties, passwordEncoder, refreshTokenService,
                loginAttemptService, userServiceClient);

        authService = new AuthService(userRepository, passwordEncoder, new JwtService(jwtProperties),
                refreshTokenService, userServiceClient, loginAttemptService,
                emailTokenService, mailService, emailProperties, security);

        user = new User();
        user.setId(1);
        user.setEmail(EMAIL);
        user.setFullName("Nguyễn Anh Huy");
        user.setPassword(passwordEncoder.encode(GOOD_PASSWORD));
        user.setRole(Role.USER);
        user.setEmailVerified(true);
        user.setCreatedAt(LocalDateTime.now().minusDays(1));
    }


    @Test
    void login_belowThreshold_countsUpAndStaysOpen() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> authService.login(wrong(), null, ATTACKER_IP))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThat(pair(ATTACKER_IP).getFailedCount()).isEqualTo(2);
        assertThat(pair(ATTACKER_IP).getLockedUntil()).isNull();
    }

    @Test
    void login_reachingThreshold_locksThatPairOnly() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> authService.login(wrong(), null, ATTACKER_IP))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThat(pair(ATTACKER_IP).getFailedCount()).isEqualTo(3);
        assertThat(pair(ATTACKER_IP).isLocked()).isTrue();
    }

    @Test
    void login_lockedPair_blocksEvenCorrectPassword() {
        // Cố tình KHÔNG stub userRepository: cặp bị khoá phải bị chặn trước khi
        // chạm vào DB, nếu không thì kẻ tấn công dò email tồn tại vẫn tốn query.
        lockPair(ATTACKER_IP, LocalDateTime.now().plusMinutes(10));

        assertThatThrownBy(() -> authService.login(correct(), null, ATTACKER_IP))
                .isInstanceOf(AccountLockedException.class)
                .satisfies(ex -> assertThat(((AccountLockedException) ex).getRetryAfterSeconds()).isPositive());
    }

    /**
     * Bài toán DoS: kẻ tấn công khoá cặp (email, IP) của mình, nhưng nạn nhân đăng
     * nhập từ IP khác vẫn phải vào được bình thường.
     */
    @Test
    void login_lockedPairFromAttacker_doesNotBlockVictimFromOwnIp() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        lockPair(ATTACKER_IP, LocalDateTime.now().plusMinutes(10));

        var response = authService.login(correct(), null, VICTIM_IP);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void login_lockExpired_allowsRetry() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        lockPair(ATTACKER_IP, LocalDateTime.now().minusSeconds(1));

        var response = authService.login(correct(), null, ATTACKER_IP);

        assertThat(response.accessToken()).isNotBlank();
        // Đăng nhập đúng phải xoá sạch bộ đếm, nếu không lần sai kế tiếp lại chạm ngưỡng ngay
        assertThat(attempts.find(EMAIL, ATTACKER_IP)).isEmpty();
    }

    @Test
    void login_success_clearsOnlyThatPairCounter() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        attempts.save(InMemoryLoginAttempts.locked(EMAIL, ATTACKER_IP, 2, null));
        attempts.save(InMemoryLoginAttempts.locked(EMAIL, VICTIM_IP, 4, null));

        authService.login(correct(), null, VICTIM_IP);

        assertThat(attempts.find(EMAIL, VICTIM_IP)).isEmpty();
        assertThat(pair(ATTACKER_IP).getFailedCount()).isEqualTo(2);
    }

    @Test
    void login_wrongPassword_doesNotTouchOtherIps() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        attempts.save(InMemoryLoginAttempts.locked(EMAIL, VICTIM_IP, 2, null));

        assertThatThrownBy(() -> authService.login(wrong(), null, ATTACKER_IP))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(pair(VICTIM_IP).getFailedCount()).isEqualTo(2);
        assertThat(pair(ATTACKER_IP).getFailedCount()).isEqualTo(1);
    }


    @Test
    void login_sprayAcrossManyEmails_getsBlockedByIpLimit() {
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());

        // Ngưỡng IP = 10. Dò 10 email khác nhau từ cùng IP, không email nào chạm
        // ngưỡng cặp (3) — nhưng IP vẫn bị chặn.
        for (int i = 0; i < 10; i++) {
            String sprayed = "dung-thu-" + i + "@test.com";
            LoginRequest attempt = new LoginRequest(sprayed, "bat-ky");
            assertThatThrownBy(() -> authService.login(attempt, null, ATTACKER_IP))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThatThrownBy(() -> authService.login(new LoginRequest("hai-trieu@victim.com", "bat-ky"), null, ATTACKER_IP))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void login_ipLimitFromOneIp_doesNotBlockDifferentIp() {
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        for (int i = 0; i < 10; i++) {
            LoginRequest sprayed = new LoginRequest("dung-thu-" + i + "@test.com", "x");
            assertThatThrownBy(() -> authService.login(sprayed, null, ATTACKER_IP))
                    .isInstanceOf(BadCredentialsException.class);
        }

        // IP khác chưa từng sai gì — phải qua được lớp giới hạn IP
        assertThatThrownBy(() -> authService.login(new LoginRequest("nguoi-dung@legit.com", "x"), null, VICTIM_IP))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Email hoặc mật khẩu không đúng");
    }

    @Test
    void login_missingIp_isGroupedInsteadOfBypassingLimits() {
        lockPair("unknown", LocalDateTime.now().plusMinutes(10));

        assertThatThrownBy(() -> authService.login(correct(), null, null))
                .isInstanceOf(AccountLockedException.class);
    }


    @Test
    void login_unknownEmail_returnsSameMessageAsWrongPassword() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        String withUser = catchMessage(() -> authService.login(wrong(), null, VICTIM_IP));

        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        String withoutUser = catchMessage(() -> authService.login(wrong(), null, VICTIM_IP));

        assertThat(withoutUser).isEqualTo(withUser);
    }

    @Test
    void login_unknownEmail_stillCountsAgainstIp() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(correct(), null, ATTACKER_IP))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(pair(ATTACKER_IP).getFailedCount()).isEqualTo(1);
    }


    @Test
    void unlockAccount_clearsEveryIpOfThatEmail() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        attempts.save(InMemoryLoginAttempts.locked(EMAIL, ATTACKER_IP, 3, LocalDateTime.now().plusMinutes(15)));
        attempts.save(InMemoryLoginAttempts.locked(EMAIL, VICTIM_IP, 2, null));

        var response = authService.unlockAccount(1);

        assertThat(response.id()).isEqualTo(1);
        assertThat(attempts.find(EMAIL, ATTACKER_IP)).isEmpty();
        assertThat(attempts.find(EMAIL, VICTIM_IP)).isEmpty();
    }

    @Test
    void changePassword_revokesSessionsAndUnlocks() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        attempts.save(InMemoryLoginAttempts.locked(EMAIL, ATTACKER_IP, 3, LocalDateTime.now().plusMinutes(15)));
        String oldHash = user.getPassword();

        authService.changePassword(1, new ChangePasswordRequest(GOOD_PASSWORD, "matkhaumoi456"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isNotEqualTo(oldHash);
        verify(refreshTokenRepository).revokeAllForUser(org.mockito.ArgumentMatchers.eq(1), any());
        // Đổi mật khẩu chứng minh chủ tài khoản thật -> mở khoá ngay
        assertThat(attempts.find(EMAIL, ATTACKER_IP)).isEmpty();
    }


    @Test
    void refresh_invalidToken_throwsUnauthorized() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("token-sai", null, null))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_reuseDetected_tellsUserAllSessionsRevoked() {
        RefreshToken stolen = new RefreshToken();
        stolen.setUserId(1);
        stolen.setTokenHash("b".repeat(64));
        stolen.setExpiresAt(LocalDateTime.now().plusDays(3));
        stolen.setRevokedAt(LocalDateTime.now().minusHours(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stolen));

        assertThatThrownBy(() -> authService.refresh("bi-lay", null, null))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("Toàn bộ phiên");
        verify(refreshTokenRepository).revokeAllForUser(anyInt(), any(LocalDateTime.class));
    }

    @Test
    void refresh_returnsNewTokenPair() {
        RefreshToken active = new RefreshToken();
        active.setUserId(1);
        active.setTokenHash("c".repeat(64));
        active.setExpiresAt(LocalDateTime.now().plusDays(3));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(active));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        var response = authService.refresh("token-hop-le", "Chrome/120", "10.0.0.5");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotEqualTo("token-hop-le");
        assertThat(response.refreshExpiresIn()).isEqualTo(7 * 86400);
        assertThat(response.expiresIn()).isEqualTo(15 * 60);
    }

    @Test
    void refresh_succeedsEvenWhenLoginCounterIsHigh() {
        // Khoá bộ đếm đăng nhập KHÔNG được chặn phiên đang hợp lệ
        attempts.save(InMemoryLoginAttempts.locked(EMAIL, ATTACKER_IP, 3, LocalDateTime.now().plusMinutes(15)));
        RefreshToken active = new RefreshToken();
        active.setUserId(1);
        active.setTokenHash("d".repeat(64));
        active.setExpiresAt(LocalDateTime.now().plusDays(3));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(active));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        assertThat(authService.refresh("token", null, null).accessToken()).isNotBlank();
    }

    @Test
    void logout_withToken_revokesOnlyThatSession() {
        RefreshToken active = new RefreshToken();
        active.setUserId(1);
        active.setTokenHash("e".repeat(64));
        active.setExpiresAt(LocalDateTime.now().plusDays(3));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(active));

        authService.logout("token-can-bo", 1);

        assertThat(active.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository, never()).revokeAllForUser(anyInt(), any());
    }

    @Test
    void logout_withoutToken_revokesEverySession() {
        authService.logout(null, 1);

        verify(refreshTokenRepository).revokeAllForUser(org.mockito.ArgumentMatchers.eq(1), any());
    }


    private com.ridebooking.auth.entity.LoginAttempt pair(String ip) {
        return attempts.pair(EMAIL, ip);
    }

    private void lockPair(String ip, LocalDateTime lockedUntil) {
        attempts.save(InMemoryLoginAttempts.locked(EMAIL, ip, 3, lockedUntil));
    }

    /** Dựng sẵn bộ đếm: {@code count} lần sai, {@code lockedUntil} null nghĩa là chưa khoá. */
    private void fail(int count, LocalDateTime lockedUntil, String ip) {
        attempts.save(InMemoryLoginAttempts.locked(EMAIL, ip, count, lockedUntil));
    }

    private String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Kỳ vọng exception");
        } catch (BadCredentialsException e) {
            return e.getMessage();
        }
    }

    private LoginRequest correct() {
        return new LoginRequest(EMAIL, GOOD_PASSWORD);
    }

    private LoginRequest wrong() {
        return new LoginRequest(EMAIL, "sai-mat-khau");
    }
}

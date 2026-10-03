package com.ridebooking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ridebooking.auth.client.UserServiceClient;
import com.ridebooking.auth.client.UserServiceClientProperties;
import com.ridebooking.auth.config.EmailProperties;
import com.ridebooking.auth.config.JwtProperties;
import com.ridebooking.auth.config.LoginSecurityProperties;
import org.springframework.security.access.AccessDeniedException;
import static org.mockito.ArgumentMatchers.anyString;
import com.ridebooking.auth.dto.ChangePasswordRequest;
import com.ridebooking.auth.dto.LoginRequest;
import com.ridebooking.auth.dto.RegisterRequest;
import com.ridebooking.auth.dto.UserResponse;
import com.ridebooking.auth.entity.User;
import com.ridebooking.auth.enums.Role;
import com.ridebooking.auth.exception.BadCredentialsException;
import com.ridebooking.auth.exception.EmailAlreadyExistsException;
import com.ridebooking.auth.repository.EmailSendLogRepository;
import com.ridebooking.auth.repository.EmailTokenRepository;
import com.ridebooking.auth.repository.LoginAttemptRepository;
import com.ridebooking.auth.repository.RefreshTokenRepository;
import com.ridebooking.auth.config.LoginSecurityProperties;
import com.ridebooking.auth.repository.UserRepository;
import com.ridebooking.auth.security.JwtService;
import com.ridebooking.auth.security.RefreshTokenService;
import java.time.LocalDate;
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

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    /** Field riêng để test bật/tắt được, thay vì biến cục bộ trong setUp. */
    private final LoginSecurityProperties security = new LoginSecurityProperties();

    private LoginAttemptService loginAttemptService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @Mock
    private EmailTokenRepository emailTokenRepository;

    @Mock
    private EmailSendLogRepository emailSendLogRepository;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("ride-booking-microservices-dev-secret-key-change-me-0123456789");
        jwtProperties.setIssuer("ride-booking-auth-service");

        RefreshTokenService refreshTokenService =
                new RefreshTokenService(refreshTokenRepository, jwtProperties);

        // spy chứ không phải object thật trần: test cần verify rằng lệnh xoá bộ
        // đếm KHÔNG bị gọi khi tính năng đang tắt.
        loginAttemptService = spy(
                new LoginAttemptService(loginAttemptRepository, new LoginSecurityProperties(),
                        new LoginAttemptWriter(loginAttemptRepository, new LoginSecurityProperties())));

        EmailProperties emailProperties = new EmailProperties();
        // MailService với sender null: mọi lần gửi sẽ thất bại và bị nuốt, đúng như
        // hành vi khi SMTP hỏng — test không cần mock hạ tầng email.
        MailService mailService = new MailService(null, emailSendLogRepository, emailProperties,
                new EmailSendLogWriter(emailSendLogRepository));
        EmailTokenService emailTokenService = new EmailTokenService(emailTokenRepository, userRepository,
                mailService, emailProperties, passwordEncoder, refreshTokenService,
                loginAttemptService, userServiceClient);

        authService = new AuthService(userRepository, passwordEncoder, new JwtService(jwtProperties),
                refreshTokenService, userServiceClient, loginAttemptService,
                emailTokenService, mailService, emailProperties, security);
    }


    /** Chỉ các test đi tới bước cấp token mới cần stub repository refresh token. */
    private void stubRefreshTokenPersistence() {
        lenient().when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void register_newEmail_savesUserWithHashedPasswordAndReturnsToken() {
        stubRefreshTokenPersistence();
        when(userRepository.existsByEmailIgnoreCase("huy@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });

        RegisterRequest request = new RegisterRequest(
                " Nguyễn Anh Huy ", " Huy@Test.com ", "matkhau123", "0912345678",
                LocalDate.of(2002, 5, 20), "Nam");

        var response = authService.register(request, null, null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getFullName()).isEqualTo("Nguyễn Anh Huy");
        assertThat(saved.getEmail()).isEqualTo("huy@test.com");
        assertThat(saved.getPassword()).isNotEqualTo("matkhau123");
        assertThat(passwordEncoder.matches("matkhau123", saved.getPassword())).isTrue();
        assertThat(saved.getRole()).isEqualTo(Role.USER);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo("huy@test.com");

        // DB đã tách nên hồ sơ phải được đẩy sang user-service
        verify(userServiceClient).createProfile(any());
    }

    @Test
    void register_userServiceUnavailable_stillReturnsTokenAndLeavesProfileForLazyCreate() {
        stubRefreshTokenPersistence();
        when(userRepository.existsByEmailIgnoreCase("huy@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
        when(userServiceClient.createProfile(any())).thenReturn(null);
        when(userServiceClient.fetchProfile(1)).thenReturn(null);

        var response = authService.register(new RegisterRequest(
                "Nguyễn Anh Huy", "huy@test.com", "matkhau123", null, null, null), null, null);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().fullName()).isEqualTo("Nguyễn Anh Huy");
        assertThat(response.user().phone()).isNull();
    }

    @Test
    void register_duplicateEmail_throwsConflictAndDoesNotSave() {
        when(userRepository.existsByEmailIgnoreCase("huy@test.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest(
                "Huy", "huy@test.com", "matkhau123", null, null, null);

        assertThatThrownBy(() -> authService.register(request, null, null))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_validCredentials_returnsToken() {
        stubRefreshTokenPersistence();
        when(userRepository.findByEmailIgnoreCase("huy@test.com")).thenReturn(Optional.of(buildUser("matkhau123")));

        var response = authService.login(new LoginRequest("huy@test.com", "matkhau123"), null, null);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().id()).isEqualTo(1);
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        when(userRepository.findByEmailIgnoreCase("huy@test.com")).thenReturn(Optional.of(buildUser("matkhau123")));

        assertThatThrownBy(() -> authService.login(new LoginRequest("huy@test.com", "sai-mat-khau"), null, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_unknownEmail_throwsBadCredentials() {
        when(userRepository.findByEmailIgnoreCase("khong@co.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("khong@co.com", "matkhau123"), null, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsAndKeepsOldPassword() {
        User user = buildUser("matkhau123");
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword(1, new ChangePasswordRequest("sai", "matkhaumoi123")))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_validRequest_hashesNewPassword() {
        User user = buildUser("matkhau123");
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        authService.changePassword(1, new ChangePasswordRequest("matkhau123", "matkhaumoi123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(passwordEncoder.matches("matkhaumoi123", captor.getValue().getPassword())).isTrue();
    }

    @Test
    void getProfile_existingUser_returnsResponseWithoutPassword() {
        when(userRepository.findById(1)).thenReturn(Optional.of(buildUser("matkhau123")));
        when(userServiceClient.fetchProfile(1)).thenReturn(null);

        UserResponse response = authService.getProfile(1);

        assertThat(response.id()).isEqualTo(1);
        assertThat(response.role()).isEqualTo(Role.USER);
        assertThat(response.toString()).doesNotContain("matkhau123");
    }

    @Test
    void getProfile_mergesProfileFromUserService_withCredentialsFromAuthDb() {
        when(userRepository.findById(1)).thenReturn(Optional.of(buildUser("matkhau123")));
        when(userServiceClient.fetchProfile(1)).thenReturn(
                new UserServiceClientProperties.ProfileView(1, "Nguyễn Anh Huy", "0912345678", "huy@test.com",
                        LocalDate.of(2002, 5, 20), "Nam", "USER", "2026-09-30T10:00:00"));

        UserResponse response = authService.getProfile(1);

        assertThat(response.fullName()).isEqualTo("Nguyễn Anh Huy");
        assertThat(response.phone()).isEqualTo("0912345678");
        assertThat(response.dob()).isEqualTo(LocalDate.of(2002, 5, 20));
        assertThat(response.gender()).isEqualTo("Nam");
        assertThat(response.role()).isEqualTo(Role.USER);
    }

    /**
     * LOGIN_ADMIN_UNLOCK_ENABLED trước đây là cấu hình chết: khai báo trong
     * properties và .env.example nhưng không nơi nào đọc, nên đặt false cũng
     * không tắt được gì.
     */
    @Test
    void unlockAccount_disabledByConfig_isRefused() {
        security.setAdminUnlockEnabled(false);

        // Cố tình KHÔNG stub findById: nếu chặn sai chỗ, hàm sẽ tra user trước
        // rồi mới ném lỗi, và test này sẽ báo UnnecessaryStubbing — tức là
        // phát hiện được cả trường hợp "chặn sau khi đã đọc dữ liệu".
        assertThatThrownBy(() -> authService.unlockAccount(1))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("LOGIN_ADMIN_UNLOCK_ENABLED");

        // Quan trọng: phải chặn TRƯỚC khi xoá bộ đếm
        verify(loginAttemptService, never()).clearForEmail(anyString());
    }

    @Test
    void unlockAccount_enabledByConfig_clearsCounters() {
        security.setAdminUnlockEnabled(true);
        when(userRepository.findById(1)).thenReturn(Optional.of(buildUser("Huy@2002x")));

        authService.unlockAccount(1);

        verify(loginAttemptService).clearForEmail("huy@test.com");
    }

    private User buildUser(String rawPassword) {
        User user = new User();
        user.setId(1);
        user.setFullName("Nguyễn Anh Huy");
        user.setEmail("huy@test.com");
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}
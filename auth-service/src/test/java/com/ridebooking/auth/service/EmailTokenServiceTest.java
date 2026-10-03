package com.ridebooking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ridebooking.auth.client.UserServiceClient;
import com.ridebooking.auth.config.EmailProperties;
import com.ridebooking.auth.config.JwtProperties;
import com.ridebooking.auth.config.LoginSecurityProperties;
import com.ridebooking.auth.entity.EmailToken;
import com.ridebooking.auth.entity.User;
import com.ridebooking.auth.enums.Role;
import com.ridebooking.auth.exception.BadCredentialsException;
import com.ridebooking.auth.exception.InvalidTokenException;
import com.ridebooking.auth.repository.EmailSendLogRepository;
import com.ridebooking.auth.repository.EmailTokenRepository;
import com.ridebooking.auth.repository.RefreshTokenRepository;
import com.ridebooking.auth.repository.UserRepository;
import com.ridebooking.auth.security.JwtService;
import com.ridebooking.auth.security.RefreshTokenService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Luồng qua email: xác minh, quên/đặt lại mật khẩu, đổi email.
 *
 * <p>Dùng JavaMailSender giả để bắt nội dung mail thật — nhờ vậy test kiểm tra
 * được link trong mail, không chỉ việc "có gửi hay không".
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Luồng xác thực qua email")
class EmailTokenServiceTest {

    private static final String EMAIL = "huy@test.com";
    private static final String PASSWORD = "Huy@2002x";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailTokenRepository emailTokenRepository;

    @Mock
    private EmailSendLogRepository emailSendLogRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private JavaMailSender mailSender;

    private final List<MimeMessage> sent = new ArrayList<>();
    private final InMemoryLoginAttempts attempts = new InMemoryLoginAttempts();

    private EmailTokenService emailTokenService;
    private EmailProperties properties;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        properties = new EmailProperties();
        properties.setFrontendUrl("http://localhost:5173");

        // Mỗi lần gửi phải là một MimeMessage riêng, nếu dùng chung một instance thì
        // các email sau sẽ đè lên email trước và test đọc ra nội dung sai.
        lenient().when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new java.util.Properties())));
        lenient().doAnswer(call -> {
            sent.add(call.getArgument(0));
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        lenient().when(emailTokenRepository.save(any(EmailToken.class)))
                .thenAnswer(call -> call.getArgument(0));
        lenient().when(emailTokenRepository.invalidateExisting(anyInt(), any(), any()))
                .thenReturn(0);

        lenient().when(refreshTokenRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(userServiceClient.updateEmail(anyInt(), any())).thenReturn(true);

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("ride-booking-microservices-dev-secret-key-change-me-0123456789");
        RefreshTokenService refreshTokenService =
                new RefreshTokenService(refreshTokenRepository, jwtProperties);
        LoginSecurityProperties loginSecurity = new LoginSecurityProperties();
        LoginAttemptService loginAttemptService =
                new LoginAttemptService(attempts.repository(), loginSecurity,
                        attempts.writer(loginSecurity));

        EmailSendLogWriter logWriter = new EmailSendLogWriter(emailSendLogRepository);
        MailService mailService = new MailService(mailSender, emailSendLogRepository, properties, logWriter);
        emailTokenService = new EmailTokenService(emailTokenRepository, userRepository, mailService,
                properties, passwordEncoder, refreshTokenService, loginAttemptService, userServiceClient);
    }


    @Test
    @DisplayName("Email gửi đi có cả bản text thuần và bản HTML")
    void sendVerification_hasBothPlainTextAndHtmlParts() throws Exception {
        emailTokenService.sendVerification(user(), "203.0.113.5");

        MimeMessage message = sent.get(0);
        assertThat(message.getContent()).isInstanceOf(MimeMultipart.class);

        MimeMultipart body = (MimeMultipart) message.getContent();
        assertThat(body.getContentType()).startsWith("multipart/alternative");
        assertThat(body.getCount()).isEqualTo(2);

        MimeBodyPart plain = (MimeBodyPart) body.getBodyPart(0);
        MimeBodyPart html = (MimeBodyPart) body.getBodyPart(1);
        // Thứ tự phải là text trước, HTML sau — client ưu tiên bản HTML
        assertThat(plain.getContentType()).startsWith("text/plain");
        assertThat(html.getContentType()).startsWith("text/html");
        // Bản text thuần không được chứa thẻ HTML
        assertThat(plain.getContent().toString()).doesNotContain("<html");
        assertThat(html.getContent().toString()).contains("<html");
    }

    @Test
    @DisplayName("Đăng ký sinh token xác minh, lưu kèm hash chứ không lưu token thô")
    void sendVerification_storesOnlyHash() {
        emailTokenService.sendVerification(user(), "203.0.113.5");

        ArgumentCaptor<EmailToken> captor = ArgumentCaptor.forClass(EmailToken.class);
        verify(emailTokenRepository).save(captor.capture());
        EmailToken stored = captor.getValue();

        assertThat(stored.getTokenHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(stored.getPurpose()).isEqualTo(EmailToken.Purpose.EMAIL_VERIFICATION);
        assertThat(stored.getUserId()).isEqualTo(1);
        assertThat(stored.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("Link xác minh trỏ về frontend với token thô (token thô chỉ nằm trong mail)")
    void sendVerification_emailLinkContainsRawToken() throws Exception {
        emailTokenService.sendVerification(user(), "203.0.113.5");

        String body = contentOf(0);
        assertThat(body).contains("http://localhost:5173");
        assertThat(body).contains("token=");
        // Token lưu trong DB phải là hash, không phải giá trị có trong mail
        ArgumentCaptor<EmailToken> captor = ArgumentCaptor.forClass(EmailToken.class);
        verify(emailTokenRepository).save(captor.capture());
        assertThat(body).doesNotContain(captor.getValue().getTokenHash());
    }

    @Test
    @DisplayName("Xác minh đúng token thì user thành verified và token bị đánh dấu đã dùng")
    void verifyEmail_marksUserVerified() {
        User user = user();
        when(emailTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(usable(EmailToken.Purpose.EMAIL_VERIFICATION)));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        User result = emailTokenService.verifyEmail("token-tho");

        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getEmailVerifiedAt()).isNotNull();
        verify(emailTokenRepository).save(any(EmailToken.class));
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Token xác minh chỉ dùng được một lần")
    void verifyEmail_rejectsUsedToken() {
        EmailToken used = usable(EmailToken.Purpose.EMAIL_VERIFICATION);
        used.setUsedAt(LocalDateTime.now().minusMinutes(1));
        when(emailTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> emailTokenService.verifyEmail("token-tho"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("đã được sử dụng");
    }

    @Test
    @DisplayName("Token hết hạn bị từ chối")
    void verifyEmail_rejectsExpiredToken() {
        EmailToken expired = usable(EmailToken.Purpose.EMAIL_VERIFICATION);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(emailTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> emailTokenService.verifyEmail("token-tho"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("hết hạn");
    }

    @Test
    @DisplayName("Token sai bị từ chối, không lộ thêm thông tin gì")
    void verifyEmail_rejectsUnknownToken() {
        when(emailTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailTokenService.verifyEmail("token-khong-ton-tai"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Token dùng cho mục đích khác không xác minh được")
    void verifyEmail_rejectsWrongPurpose() {
        when(emailTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(usable(EmailToken.Purpose.PASSWORD_RESET)));

        assertThatThrownBy(() -> emailTokenService.verifyEmail("token-tho"))
                .isInstanceOf(InvalidTokenException.class);
    }


    @Test
    @DisplayName("Đặt lại mật khẩu đổi hash, thu hồi mọi phiên và mở khoá bộ đếm sai")
    void resetPassword_revokesSessionsAndUnlocks() {
        User user = user();
        when(emailTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(usable(EmailToken.Purpose.PASSWORD_RESET)));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        attempts.save(InMemoryLoginAttempts.locked(EMAIL, "203.0.113.5", 5,
                LocalDateTime.now().plusMinutes(15)));

        emailTokenService.resetPassword("token-tho", "MatKhauMoi@2026");

        assertThat(user.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches("MatKhauMoi@2026", user.getPassword())).isTrue();
        verify(refreshTokenRepository).revokeAllForUser(org.mockito.ArgumentMatchers.eq(1), any());
        assertThat(attempts.find(EMAIL, "203.0.113.5")).isEmpty();
    }

    @Test
    @DisplayName("Token reset không dùng lại được lần hai")
    void resetPassword_cannotReuseToken() {
        EmailToken used = usable(EmailToken.Purpose.PASSWORD_RESET);
        used.setUsedAt(LocalDateTime.now().minusMinutes(1));
        when(emailTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> emailTokenService.resetPassword("token-tho", "MatKhauMoi@2026"))
                .isInstanceOf(InvalidTokenException.class);
    }


    @Test
    @DisplayName("Đổi email gửi cả link xác nhận tới email mới và cảnh báo tới email cũ")
    void startEmailChange_warnsOldAndConfirmsNew() throws Exception {
        emailTokenService.startEmailChange(user(), "email-moi@test.com", PASSWORD, "203.0.113.5");

        // 2 email: 1 tới địa chỉ mới, 1 tới địa chỉ cũ
        assertThat(sent).hasSize(2);
        assertThat(recipientOf(0)).isEqualTo("email-moi@test.com");
        assertThat(recipientOf(1)).isEqualTo(EMAIL);

        ArgumentCaptor<EmailToken> captor = ArgumentCaptor.forClass(EmailToken.class);
        verify(emailTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getNewEmail()).isEqualTo("email-moi@test.com");
    }

    @Test
    @DisplayName("Xác nhận đổi email chuyển sang địa chỉ mới, đánh dấu verified và đồng bộ user-service")
    void confirmEmailChange_switchesEmail() {
        User user = user();
        EmailToken token = usable(EmailToken.Purpose.EMAIL_CHANGE);
        token.setNewEmail("email-moi@test.com");
        when(emailTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        emailTokenService.confirmEmailChange("token-tho", PASSWORD);

        assertThat(user.getEmail()).isEqualTo("email-moi@test.com");
        assertThat(user.isEmailVerified()).isTrue();
        verify(userServiceClient).updateEmail(1, "email-moi@test.com");
        verify(refreshTokenRepository).revokeAllForUser(org.mockito.ArgumentMatchers.eq(1), any());
    }

    @Test
    @DisplayName("Đổi email phải xác nhận bằng mật khẩu hiện tại")
    void confirmEmailChange_requiresCurrentPassword() {
        User user = user();
        EmailToken token = usable(EmailToken.Purpose.EMAIL_CHANGE);
        token.setNewEmail("email-moi@test.com");
        when(emailTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> emailTokenService.confirmEmailChange("token-tho", "mat-khau-sai"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("không đúng");
        assertThat(user.getEmail()).isEqualTo(EMAIL);
    }


    private User user() {
        User user = new User();
        user.setId(1);
        user.setEmail(EMAIL);
        user.setFullName("Nguyễn Anh Huy");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(Role.USER);
        return user;
    }

    /** Token sạch: đúng mục đích, còn hạn, chưa dùng. */
    private EmailToken usable(EmailToken.Purpose purpose) {
        EmailToken token = new EmailToken();
        token.setUserId(1);
        token.setPurpose(purpose);
        token.setTokenHash("a".repeat(64));
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        return token;
    }

    private String recipientOf(int index) throws Exception {
        return sent.get(index).getAllRecipients()[0].toString();
    }

    private String contentOf(int index) throws Exception {
        MimeMessage message = sent.get(index);
        message.saveChanges();
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        message.writeTo(buffer, new String[]{"UTF-8"});
        return buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}

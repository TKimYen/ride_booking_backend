package com.ridebooking.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ridebooking.auth.config.JwtProperties;
import com.ridebooking.auth.entity.RefreshToken;
import com.ridebooking.auth.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiểm chứng vòng đời refresh token: cấp -> xoay vòng -> phát hiện tái sử dụng.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setRefreshTokenDays(7);
        service = new RefreshTokenService(repository, properties);
        lenient().when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void issue_returnsRawTokenButStoresOnlyHash() {
        String raw = service.issue(7, "Chrome/120", "127.0.0.1");

        assertThat(raw).isNotBlank();
        // Token gốc phải đủ dài để chống brute-force offline
        assertThat(raw.length()).isGreaterThanOrEqualTo(40);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken stored = captor.getValue();

        assertThat(stored.getUserId()).isEqualTo(7);
        // Không lưu token gốc, chỉ lưu hash 64 ký tự hex
        assertThat(stored.getTokenHash()).hasSize(64).doesNotContain(raw);
        assertThat(stored.getRevokedAt()).isNull();
        assertThat(stored.getUserAgent()).isEqualTo("Chrome/120");
        assertThat(stored.getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void issue_truncatesOverlongUserAgent() {
        service.issue(1, "x".repeat(500), null);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserAgent()).hasSize(255);
    }

    @Test
    void issue_generatesDifferentTokenEachTime() {
        assertThat(service.issue(1, null, null)).isNotEqualTo(service.issue(1, null, null));
    }

    @Test
    void rotate_validToken_returnsUserIdAndRevokesOldToken() {
        RefreshToken stored = activeToken(3);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        RefreshTokenService.RefreshResult result = service.rotate("raw-token");

        assertThat(result.accepted()).isTrue();
        assertThat(result.userId()).isEqualTo(3);
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(repository).save(stored);
    }

    @Test
    void rotate_unknownToken_rejected() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        RefreshTokenService.RefreshResult result = service.rotate("khong-ton-tai");

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo(RefreshTokenService.RefreshResult.Reason.INVALID);
    }

    @Test
    void rotate_blankToken_rejectedWithoutDbHit() {
        RefreshTokenService.RefreshResult result = service.rotate("   ");

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo(RefreshTokenService.RefreshResult.Reason.INVALID);
    }

    @Test
    void rotate_expiredToken_rejected() {
        RefreshToken stored = activeToken(5);
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        RefreshTokenService.RefreshResult result = service.rotate("raw");

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo(RefreshTokenService.RefreshResult.Reason.EXPIRED);
        assertThat(stored.getRevokedAt()).isNull();
    }

    @Test
    void rotate_revokedTokenDetectsReuseAndRevokesEverySession() {
        RefreshToken stolen = activeToken(9);
        stolen.setRevokedAt(LocalDateTime.now().minusDays(1));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(stolen));

        RefreshTokenService.RefreshResult result = service.rotate("token-da-bi-dung-lai");

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo(RefreshTokenService.RefreshResult.Reason.REUSE_DETECTED);
        // Toàn bộ phiên của user phải bị thu hồi, không chỉ token bị lộ
        verify(repository).revokeAllForUser(anyInt(), any(LocalDateTime.class));
    }

    @Test
    void revokeForUser_marksTokenRevoked() {
        RefreshToken stored = activeToken(2);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        int revoked = service.revokeForUser("raw", 2);

        assertThat(revoked).isEqualTo(1);
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(repository).save(stored);
    }

    @Test
    void revokeForUser_ignoresTokenOfAnotherUser() {
        RefreshToken stored = activeToken(2);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        // userId=3 đang đăng nhập nhưng cầm token của userId=2
        int revoked = service.revokeForUser("raw", 3);

        assertThat(revoked).isZero();
        assertThat(stored.getRevokedAt()).isNull();
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void revokeForUser_alreadyRevokedTokenIsNoop() {
        RefreshToken stored = activeToken(2);
        stored.setRevokedAt(LocalDateTime.now().minusHours(2));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThat(service.revokeForUser("raw", 2)).isZero();

        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void revokeForUser_unknownTokenIsNoop() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThat(service.revokeForUser("raw", 2)).isZero();

        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void revokeForUser_nullUserIdSkipsOwnershipCheck() {
        RefreshToken stored = activeToken(2);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThat(service.revokeForUser("raw", null)).isEqualTo(1);
    }

    @Test
    void revokeForUser_blankTokenIsNoop() {
        assertThat(service.revokeForUser(null, 2)).isZero();
        assertThat(service.revokeForUser("", 2)).isZero();
    }

    private RefreshToken activeToken(Integer userId) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash("a".repeat(64));
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        return token;
    }
}
package com.ridebooking.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ridebooking.user.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Token do auth-service cấp phải được user-service chấp nhận.
 * Test khoá lại hợp đồng (secret + issuer + tên claim), vì vậy token ở đây được
 * dựng đúng theo cách auth-service ký — không phụ thuộc vào code của service kia.
 */
class CrossServiceJwtTest {

    private static final String SECRET = "ride-booking-microservices-dev-secret-key-change-me-0123456789";
    private static final String ISSUER = "ride-booking-auth-service";

    private JwtService userService;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer(ISSUER);

        userService = new JwtService(properties);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void tokenFromAuthService_isAcceptedByUserService() {
        AuthenticatedUser parsed = userService.parseToken(signToken(42, "huy@test.com", "Nguyễn Anh Huy", "USER"));

        assertThat(parsed).isNotNull();
        assertThat(parsed.id()).isEqualTo(42);
        assertThat(parsed.email()).isEqualTo("huy@test.com");
        assertThat(parsed.fullName()).isEqualTo("Nguyễn Anh Huy");
        assertThat(parsed.role()).isEqualTo(com.ridebooking.user.enums.Role.USER);
        assertThat(parsed.isAdmin()).isFalse();
    }

    @Test
    void adminRole_mapsToAdminAuthority() {
        AuthenticatedUser parsed = userService.parseToken(signToken(1, "admin@test.com", "Admin", "ADMIN"));

        assertThat(parsed.isAdmin()).isTrue();
        assertThat(parsed.authorities()).containsExactly("ROLE_ADMIN");
    }

    @Test
    void tokenWithWrongIssuer_isRejected() {
        String token = Jwts.builder()
                .issuer("service-khac")
                .subject("huy@test.com")
                .claim("userId", 1)
                .claim("fullName", "Huy")
                .claim("role", "USER")
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThat(userService.parseToken(token)).isNull();
    }

    @Test
    void tokenSignedWithDifferentSecret_isRejected() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "mot-secret-khac-hoan-toan-khac-secret-123456".getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("huy@test.com")
                .claim("userId", 1)
                .claim("fullName", "Huy")
                .claim("role", "USER")
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertThat(userService.parseToken(token)).isNull();
    }

    @Test
    void expiredToken_isRejected() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("huy@test.com")
                .claim("userId", 1)
                .claim("fullName", "Huy")
                .claim("role", "USER")
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThat(userService.parseToken(token)).isNull();
    }

    /** Dựng token y hệt cách auth-service ký (JwtService.generateToken). */
    private String signToken(Integer userId, String email, String fullName, String role) {
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(email)
                .claim("userId", userId)
                .claim("fullName", fullName)
                .claim("role", role)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(2, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
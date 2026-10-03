package com.ridebooking.auth.security;

import com.ridebooking.auth.config.JwtProperties;
import com.ridebooking.auth.entity.User;
import com.ridebooking.auth.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Tạo & đọc JWT (HS256). Token mang: sub = email, userId, fullName, role.
 */
@Service
public class JwtService {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_FULL_NAME = "fullName";
    public static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret phải dài tối thiểu 32 ký tự");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public long getExpiresInSeconds() {
        return properties.getExpirationMinutes() * 60;
    }

    public String generateToken(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getExpirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_FULL_NAME, user.getFullName())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Trả về null nếu token sai/hết hạn, thay vì ném exception. */
    public AuthenticatedUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AuthenticatedUser(
                    claims.get(CLAIM_USER_ID, Integer.class),
                    claims.getSubject(),
                    claims.get(CLAIM_FULL_NAME, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
        } catch (Exception ex) {
            return null;
        }
    }
}
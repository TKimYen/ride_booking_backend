package com.ridebooking.payment.security;

import com.ridebooking.payment.config.JwtProperties;
import com.ridebooking.payment.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Đọc & xác thực JWT do auth-service cấp (cùng secret HS256, cùng issuer). */
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

    /** Trả về null nếu token sai/hết hạn. */
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

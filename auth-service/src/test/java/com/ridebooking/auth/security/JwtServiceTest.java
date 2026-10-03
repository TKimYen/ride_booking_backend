package com.ridebooking.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ridebooking.auth.config.JwtProperties;
import com.ridebooking.auth.entity.User;
import com.ridebooking.auth.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtProperties properties;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret("ride-booking-microservices-dev-secret-key-change-me-0123456789");
        properties.setExpirationMinutes(30);
        properties.setIssuer("ride-booking-auth-service");
        jwtService = new JwtService(properties);
    }

    @Test
    void generateToken_thenParse_returnsSamePrincipal() {
        User user = buildUser();

        AuthenticatedUser parsed = jwtService.parseToken(jwtService.generateToken(user));

        assertThat(parsed).isNotNull();
        assertThat(parsed.id()).isEqualTo(7);
        assertThat(parsed.email()).isEqualTo("huy@test.com");
        assertThat(parsed.fullName()).isEqualTo("Nguyễn Anh Huy");
        assertThat(parsed.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void parseToken_withTamperedToken_returnsNull() {
        String token = jwtService.generateToken(buildUser());
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThat(jwtService.parseToken(tampered)).isNull();
    }

    @Test
    void parseToken_withExpiredToken_returnsNull() {
        properties.setExpirationMinutes(-1);

        assertThat(jwtService.parseToken(jwtService.generateToken(buildUser()))).isNull();
    }

    @Test
    void parseToken_fromDifferentIssuer_returnsNull() {
        JwtProperties other = new JwtProperties();
        other.setSecret(properties.getSecret());
        other.setIssuer("khac-service");

        assertThat(new JwtService(other).parseToken(jwtService.generateToken(buildUser()))).isNull();
    }

    @Test
    void secretShorterThan32Bytes_throwsOnStartup() {
        JwtProperties weak = new JwtProperties();
        weak.setSecret("too-short");

        assertThatThrownBy(() -> new JwtService(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 ký tự");
    }

    private User buildUser() {
        User user = new User();
        user.setId(7);
        user.setFullName("Nguyễn Anh Huy");
        user.setEmail("huy@test.com");
        user.setRole(Role.ADMIN);
        return user;
    }
}
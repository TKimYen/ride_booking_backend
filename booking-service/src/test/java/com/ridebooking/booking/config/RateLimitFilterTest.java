package com.ridebooking.booking.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Cấu hình limit thấp cho test: max 3 guest booking, max 2 lookups
        filter = new RateLimitFilter(objectMapper, true, 3, 2);
    }

    @Test
    @DisplayName("[5.3] Khách vãng lai gọi POST /api/bookings quá 3 lần bị chặn 429")
    void guestBooking_exceedsRateLimit_returns429() throws Exception {
        String clientIp = "203.0.113.10";

        // 3 lần đầu thành công
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/bookings");
            req.setRemoteAddr(clientIp);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }

        // Lần thứ 4 vượt giới hạn -> 429 Too Many Requests
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/bookings");
        req.setRemoteAddr(clientIp);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentAsString()).contains("Too Many Requests");
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    @DisplayName("[5.3] Người dùng đã đăng nhập (có Bearer JWT) không bị áp rate limit khách vãng lai")
    void authenticatedUser_notBlockedByGuestRateLimit() throws Exception {
        String clientIp = "203.0.113.20";

        // Gửi 5 lần kèm Authorization header -> đều không bị chặn
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/bookings");
            req.setRemoteAddr(clientIp);
            req.addHeader("Authorization", "Bearer mock-token-123");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("[5.3] Tra cứu vé POST /api/bookings/lookup quá 2 lần bị chặn 429")
    void bookingLookup_exceedsRateLimit_returns429() throws Exception {
        String clientIp = "203.0.113.30";

        // 2 lần đầu qua
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/bookings/lookup");
            req.setRemoteAddr(clientIp);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }

        // Lần thứ 3 -> 429
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/bookings/lookup");
        req.setRemoteAddr(clientIp);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentAsString()).contains("Bạn đã tra cứu quá nhiều lần");
    }
}


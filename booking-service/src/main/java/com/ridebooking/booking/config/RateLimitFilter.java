package com.ridebooking.booking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chống spam [5.3]: Giới hạn số lần gọi theo IP cho:
 * <ul>
 *   <li>{@code POST /api/bookings} của khách vãng lai (không có JWT).</li>
 *   <li>{@code POST /api/bookings/lookup} tra cứu vé.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int maxGuestBookingsPerMinute;
    private final int maxLookupsPerMinute;

    private final ConcurrentHashMap<String, RequestCounter> guestBookingLimitMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RequestCounter> lookupLimitMap = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.rate-limit.guest-booking.max-per-minute:10}") int maxGuestBookingsPerMinute,
                           @Value("${app.rate-limit.lookup.max-per-minute:15}") int maxLookupsPerMinute) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.maxGuestBookingsPerMinute = maxGuestBookingsPerMinute;
        this.maxLookupsPerMinute = maxLookupsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method)) {
            if ("/api/bookings".equals(path)) {
                // Chỉ rate limit khách vãng lai (không có Authorization header)
                String authHeader = request.getHeader("Authorization");
                boolean isGuest = !StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ");
                if (isGuest) {
                    String ip = extractClientIp(request);
                    if (!isAllowed(guestBookingLimitMap, ip, maxGuestBookingsPerMinute)) {
                        writeRateLimitResponse(request, response, "Bạn đã tạo quá nhiều đơn đặt vé, vui lòng thử lại sau");
                        return;
                    }
                }
            } else if ("/api/bookings/lookup".equals(path)) {
                String ip = extractClientIp(request);
                if (!isAllowed(lookupLimitMap, ip, maxLookupsPerMinute)) {
                    writeRateLimitResponse(request, response, "Bạn đã tra cứu quá nhiều lần, vui lòng thử lại sau");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(ConcurrentHashMap<String, RequestCounter> map, String ip, int maxRequests) {
        long currentMinute = System.currentTimeMillis() / 60000;
        RequestCounter counter = map.compute(ip, (key, existing) -> {
            if (existing == null || existing.minute != currentMinute) {
                return new RequestCounter(currentMinute, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        // Dọn dẹp các IP cũ định kỳ
        if (map.size() > 1000) {
            cleanupExpired(map, currentMinute);
        }

        return counter.count.get() <= maxRequests;
    }

    private void cleanupExpired(ConcurrentHashMap<String, RequestCounter> map, long currentMinute) {
        Iterator<Map.Entry<String, RequestCounter>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RequestCounter> entry = iterator.next();
            if (entry.getValue().minute < currentMinute) {
                iterator.remove();
            }
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "60");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", "Too Many Requests");
        body.put("message", message);
        body.put("path", request.getRequestURI());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static class RequestCounter {
        final long minute;
        final AtomicInteger count;

        RequestCounter(long minute, AtomicInteger count) {
            this.minute = minute;
            this.count = count;
        }
    }
}


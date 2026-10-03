package com.ridebooking.booking.client;

import com.ridebooking.booking.dto.BatchProfilesRequest;
import com.ridebooking.booking.dto.InternalUserProfile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Gọi {@code POST /api/internal/users/batch} của user-service.
 *
 * <p>Ba lớp bảo vệ, cả ba nhằm một mục tiêu: Trang danh sách đánh giá không được
 * sập theo user-service.
 * <ul>
 *   <li>timeout ngắn cho cả connect lẫn read — user-service chậm thà treo trang của
 *       khách, không phải chỉ chậm;</li>
 *   <li>bắt lỗi ra log — thiếu tên hiển thị thì chấp nhận được, lỗi 500 thì không;</li>
 *   <li>cache theo id — các trang đánh giá liên tiếp hỏi lại cùng một nhóm người.</li>
 * </ul>
 */
@Component
public class UserProfileClientImpl implements UserProfileClient {

    private static final Logger log = LoggerFactory.getLogger(UserProfileClientImpl.class);

    private static final ParameterizedTypeReference<List<InternalUserProfile>> PROFILE_LIST =
            new ParameterizedTypeReference<>() {
            };

    /** Tên hiển thị đã xem, kèm thời điểm hết hạn. */
    private record CachedName(String name, long expiresAtMillis) {
    }

    private final RestTemplate restTemplate;
    private final String internalKey;
    private final ConcurrentHashMap<Integer, CachedName> cache = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxCacheEntries;

    /**
     * Chặn cache phình vô hạn khi user-service lỗi liên tục và mọi id đều được hỏi
     * lại. Vượt ngưỡng thì xoá hết: xoá hết đơn giản và chắc chắn, còn thuế chọn lọc
     * thì phức tạp vì không ai đo được nó có đáng không.
     */
    @Autowired
    public UserProfileClientImpl(
            RestTemplateBuilder builder,
            @Value("${app.clients.user-service.base-url}") String baseUrl,
            @Value("${app.internal.key}") String internalKey,
            @Value("${app.clients.user-service.timeout-ms:1500}") long timeoutMs,
            @Value("${app.clients.user-service.cache-ttl-seconds:300}") long ttlSeconds,
            @Value("${app.clients.user-service.cache-max-entries:5000}") int maxCacheEntries) {
        this(builder.rootUri(baseUrl)
                        .connectTimeout(Duration.ofMillis(timeoutMs))
                        .readTimeout(Duration.ofMillis(timeoutMs))
                        .build(),
                internalKey, ttlSeconds, maxCacheEntries);
    }

    /**
     * Nhận {@code RestTemplate} dựng sẵn — dành cho test, để gắn
     * {@code MockRestServiceServer}. Test dùng reflection để chèn client sẽ đứng
     * vỡ mỗi lần đổi tên field.
     */
    UserProfileClientImpl(RestTemplate restTemplate, String internalKey,
                          long ttlSeconds, int maxCacheEntries) {
        this.restTemplate = restTemplate;
        this.internalKey = internalKey;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.maxCacheEntries = maxCacheEntries;
    }

    @Override
    public Map<Integer, String> fetchDisplayNames(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        long now = System.currentTimeMillis();
        Map<Integer, String> result = new LinkedHashMap<>();
        List<Integer> missing = new ArrayList<>();

        for (Integer id : userIds.stream().distinct().toList()) {
            if (id == null) {
                continue;
            }
            CachedName cached = cache.get(id);
            if (cached != null && cached.expiresAtMillis() > now) {
                result.put(id, cached.name());
            } else {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return result;
        }
        result.putAll(loadFromUserService(missing, now));
        return result;
    }

    /** Trả map rỗng nếu user-service lỗi: thiếu tên thì hiện "Người dùng", hơn là lỗi 500. */
    private Map<Integer, String> loadFromUserService(List<Integer> userIds, long now) {
        try {
            ResponseEntity<List<InternalUserProfile>> response = restTemplate.exchange(
                    "/api/internal/users/batch",
                    HttpMethod.POST,
                    new HttpEntity<>(new BatchProfilesRequest(userIds), internalHeaders()),
                    PROFILE_LIST);

            List<InternalUserProfile> profiles = response.getBody();
            if (profiles == null || profiles.isEmpty()) {
                return Map.of();
            }
            if (cache.size() + profiles.size() > maxCacheEntries) {
                log.warn("Cache tên người dùng đầy ({} mục), xoá hết trước khi nạp lại", cache.size());
                cache.clear();
            }
            long expiresAt = now + ttl.toMillis();
            Map<Integer, String> fresh = new LinkedHashMap<>();
            for (InternalUserProfile profile : profiles) {
                if (profile.id() == null) {
                    continue;
                }
                // Tên rỗng thì KHÔNG đưa vào cache: user-service có thể chưa có hồ sơ
                // lúc này nhưng có sau, và tên rỗng sẽ bị đóng băng cả 5 phút.
                if (profile.fullName() != null && !profile.fullName().isBlank()) {
                    fresh.put(profile.id(), profile.fullName());
                    cache.put(profile.id(), new CachedName(profile.fullName(), expiresAt));
                }
            }
            return fresh;
        } catch (Exception ex) {
            log.warn("Không lấy được tên người đánh giá từ user-service: {}", ex.getMessage());
            return Map.of();
        }
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Key", internalKey);
        return headers;
    }
}
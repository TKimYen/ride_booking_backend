package com.ridebooking.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ridebooking.auth.config.LoginSecurityProperties;
import com.ridebooking.auth.entity.LoginAttempt;
import com.ridebooking.auth.repository.LoginAttemptRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link LoginAttemptRepository} giả, lưu trong RAM.
 *
 * <p>Cơ chế chống brute-force nằm ở chỗ TÍCH LUỸ bộ đếm qua nhiều lần gọi. Nếu
 * dùng {@code @Mock} trần, mỗi lần {@code findByEmailAndIpAddress} đều trả
 * {@code Optional.empty()} nên bộ đếm không bao giờ tăng — test sẽ pass trong
 * khi luồng thật thì hỏng. Ở đây mock chỉ đóng vai trò "giao diện", còn dữ liệu
 * thật nằm trong list.
 *
 * <p>Các stub đều {@code lenient}: mỗi test chỉ gọi một phần API, còn strict
 * stubs sẽ báo "unnecessary stubbing" dù harness đã dùng đúng cách.
 */
final class InMemoryLoginAttempts {

    private final List<LoginAttempt> rows = new ArrayList<>();
    private final LoginAttemptRepository repository;

    InMemoryLoginAttempts() {
        repository = mock(LoginAttemptRepository.class);

        lenient().when(repository.findByEmailAndIpAddress(anyString(), anyString())).thenAnswer(call -> {
            String email = call.getArgument(0);
            String ip = call.getArgument(1);
            return rows.stream()
                    .filter(r -> r.getEmail().equalsIgnoreCase(email) && r.getIpAddress().equals(ip))
                    .findFirst();
        });

        // findForUpdate là biến thể khoá ghi; với list trong RAM thì "khoá" chỉ
        // là cùng một nguồn dữ liệu, nên logic phải giống findByEmailAndIpAddress.
        lenient().when(repository.findForUpdate(anyString(), anyString())).thenAnswer(call -> {
            String email = call.getArgument(0);
            String ip = call.getArgument(1);
            return rows.stream()
                    .filter(r -> r.getEmail().equalsIgnoreCase(email) && r.getIpAddress().equals(ip))
                    .findFirst();
        });

        lenient().when(repository.save(any(LoginAttempt.class))).thenAnswer(call -> {
            LoginAttempt entity = call.getArgument(0);
            if (!rows.contains(entity)) {
                rows.add(entity);
            }
            return entity;
        });

        // LoginAttemptWriter gọi saveAndFlush để bắt lỗi UNIQUE ngay
        lenient().when(repository.saveAndFlush(any(LoginAttempt.class))).thenAnswer(call -> {
            LoginAttempt entity = call.getArgument(0);
            if (!rows.contains(entity)) {
                rows.add(entity);
            }
            return entity;
        });

        // delete() trả void nên phải dùng doAnswer, không when(...).thenAnswer
        lenient().doAnswer(call -> {
            rows.remove(call.getArgument(0));
            return null;
        }).when(repository).delete(any(LoginAttempt.class));

        lenient().when(repository.deleteByEmail(anyString())).thenAnswer(call -> {
            String email = call.getArgument(0);
            int before = rows.size();
            rows.removeIf(r -> r.getEmail().equalsIgnoreCase(email));
            return before - rows.size();
        });

        lenient().when(repository.countFailuresFromIpSince(anyString(), any(LocalDateTime.class)))
                .thenAnswer(call -> {
                    String ip = call.getArgument(0);
                    LocalDateTime since = call.getArgument(1);
                    return rows.stream()
                            .filter(r -> r.getIpAddress().equals(ip) && !r.getLastFailedAt().isBefore(since))
                            .mapToLong(LoginAttempt::getFailedCount)
                            .sum();
                });

        lenient().when(repository.deleteOlderThan(any(LocalDateTime.class))).thenAnswer(call -> {
            LocalDateTime before = call.getArgument(0);
            int size = rows.size();
            rows.removeIf(r -> r.getLastFailedAt().isBefore(before));
            return size - rows.size();
        });
    }

    LoginAttemptRepository repository() {
        return repository;
    }

    Optional<LoginAttempt> find(String email, String ip) {
        return repository.findByEmailAndIpAddress(email, ip);
    }

    /** Cặp (email, IP) bắt buộc phải tồn tại — dùng khi test khẳng định trạng thái. */
    LoginAttempt pair(String email, String ip) {
        return repository.findByEmailAndIpAddress(email, ip)
                .orElseThrow(() -> new AssertionError("Chưa có bộ đếm cho cặp (" + email + ", " + ip + ")"));
    }

    void save(LoginAttempt attempt) {
        repository.save(attempt);
    }

    /** writer dùng chung repository này, để test khởi tạo LoginAttemptService. */
    LoginAttemptWriter writer(LoginSecurityProperties properties) {
        return new LoginAttemptWriter(repository, properties);
    }

    int size() {
        return rows.size();
    }

    /** Tạo một dòng bộ đếm ở trạng thái đã khoá, dùng để dựng tiền trạng thái test. */
    static LoginAttempt locked(String email, String ip, int count, LocalDateTime lockedUntil) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setEmail(email);
        attempt.setIpAddress(ip);
        attempt.setFailedCount(count);
        attempt.setFirstFailedAt(LocalDateTime.now().minusMinutes(5));
        attempt.setLastFailedAt(LocalDateTime.now());
        attempt.setLockedUntil(lockedUntil);
        return attempt;
    }
}

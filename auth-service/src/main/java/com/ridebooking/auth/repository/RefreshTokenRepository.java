package com.ridebooking.auth.repository;

import com.ridebooking.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Integer userId);

    /** Thu hồi toàn bộ phiên của user - dùng khi phát hiện tái sử dụng token đã bị thu hồi. */
    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now
             WHERE t.userId = :userId
               AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") Integer userId, @Param("now") LocalDateTime now);

    /** Dọn token đã hết hạn từ lâu, chạy định kỳ để bảng không phình vô hạn. */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :threshold")
    int deleteExpired(@Param("threshold") LocalDateTime threshold);
}
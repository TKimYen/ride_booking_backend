package com.ridebooking.auth.repository;

import com.ridebooking.auth.entity.EmailToken;
import com.ridebooking.auth.entity.EmailToken.Purpose;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    Optional<EmailToken> findByTokenHash(String tokenHash);

    List<EmailToken> findByUserIdAndPurposeAndUsedAtIsNull(Integer userId, Purpose purpose);

    /** Vô hiệu hoá các token cũ cùng mục đích khi tạo token mới. */
    @Modifying
    @Query("update EmailToken t set t.usedAt = :now "
            + "where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateExisting(@Param("userId") Integer userId,
                           @Param("purpose") Purpose purpose,
                           @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from EmailToken t where t.expiresAt < :before")
    int deleteExpired(@Param("before") LocalDateTime before);
}

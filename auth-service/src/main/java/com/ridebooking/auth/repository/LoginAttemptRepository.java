package com.ridebooking.auth.repository;

import com.ridebooking.auth.entity.LoginAttempt;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    Optional<LoginAttempt> findByEmailAndIpAddress(String email, String ipAddress);

    /**
     * Đọc kèm khoá ghi (SELECT ... FOR UPDATE).
     *
     * <p>Bắt buộc khi tăng bộ đếm: nếu chỉ đọc rồi ghi, nhiều request song song
     * cùng đọc một giá trị rồi cùng ghi giá trị đó, các lần đếm bị đè mất và
     * kẻ tấn công thử song song sẽ lách được giới hạn.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoginAttempt a where a.email = :email and a.ipAddress = :ip")
    Optional<LoginAttempt> findForUpdate(@Param("email") String email, @Param("ip") String ip);

    /** Xoá toàn bộ bộ đếm của một tài khoản — gọi khi đổi mật khẩu hoặc xác minh email. */
    int deleteByEmail(String email);

    List<LoginAttempt> findByIpAddressAndLastFailedAtAfter(String ipAddress, LocalDateTime since);

    /** Số lần sai từ một IP trong cửa sổ thời gian, dùng chặn tấn công rải mật khẩu. */
    @Query("select coalesce(sum(a.failedCount), 0) from LoginAttempt a "
            + "where a.ipAddress = :ip and a.lastFailedAt >= :since")
    long countFailuresFromIpSince(@Param("ip") String ip,
                                  @Param("since") LocalDateTime since);

    /** Dọn dòng cũ đã hết hiệu lực, tránh bảng phình vô hạn. */
    @Modifying
    @Query("delete from LoginAttempt a where a.lastFailedAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}

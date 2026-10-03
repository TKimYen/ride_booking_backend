package com.ridebooking.auth.repository;

import com.ridebooking.auth.entity.EmailSendLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailSendLogRepository extends JpaRepository<EmailSendLog, Long> {

    long countByRecipientAndCreatedAtAfter(String recipient, LocalDateTime since);

    long countByIpAddressAndCreatedAtAfter(String ipAddress, LocalDateTime since);

    /**
     * Các dòng đến hạn gửi lại. Chặn trên {@code id} để worker chạy song song
     * (nhiều bản sao của service) không gửi trùng cùng một email.
     */
    @Query("""
            select l from EmailSendLog l
            where l.status = com.ridebooking.auth.entity.EmailSendLog.STATUS_PENDING
              and l.nextAttemptAt is not null
              and l.nextAttemptAt <= :now
            order by l.nextAttemptAt, l.id
            """)
    List<EmailSendLog> findDueForRetry(@Param("now") LocalDateTime now,
                                       org.springframework.data.domain.Pageable pageable);

    /**
     * Chỉ xoá dòng đã KẾT THÚC. Dòng PENDING phải giữ lại bất kể tuổi: xoá nó
     * nghĩa là xoá email chưa gửi được, đúng lúc cần nhất thì mất.
     */
    @Modifying
    @Query("""
            delete from EmailSendLog l
            where l.createdAt < :before
              and l.status <> com.ridebooking.auth.entity.EmailSendLog.STATUS_PENDING
            """)
    int deleteOlderThan(@Param("before") LocalDateTime before);
}

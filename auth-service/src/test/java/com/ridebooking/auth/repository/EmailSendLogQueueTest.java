package com.ridebooking.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ridebooking.auth.entity.EmailSendLog;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hai lỗi dễ mắc nhất của hàng đợi gửi email.
 *
 * <p>Cả hai đều là loại lỗi test mà không test thì hỏng lúc chạy thật: job dọn dẹp
 * chạy lúc 3h sáng, xoá nhầm hàng đợi rồi xem như email đã gửi; còn retry không
 * giới hạn thì tự gửi mail vô hạn mỗi phút một lần.
 */
@SpringBootTest
// @Transactional: lệnh DELETE của deleteOlderThan() cần transaction đang mở.
// Gọi trực tiếp từ test (không qua service) nên phải tự mở.
@Transactional
@DisplayName("Hàng đợi gửi email: dọn dẹp và chống gửi vô hạn")
class EmailSendLogQueueTest {

    @Autowired
    private EmailSendLogRepository repository;

    private EmailSendLog row(String recipient, String status,
                             LocalDateTime nextAttempt, LocalDateTime createdAt) {
        EmailSendLog pending = EmailSendLog.pending(recipient, "Chủ đề", "PASSWORD_RESET",
                "10.0.0.9", "<p>x</p>", "x", LocalDateTime.now().plusHours(1));
        pending.setStatus(status);
        pending.setNextAttemptAt(nextAttempt);
        pending.setCreatedAt(createdAt);
        return repository.saveAndFlush(pending);
    }

    @Test
    @DisplayName("Job dọn dẹp KHÔNG được xoá email chưa gửi được")
    void purgeKeepsPendingRows() {
        repository.deleteAll();
        // Dòng cũ 40 ngày, vẫn đang chờ gửi lại.
        row("pending-cu@test.com", EmailSendLog.STATUS_PENDING,
                LocalDateTime.now().plusMinutes(5), LocalDateTime.now().minusDays(40));
        // Dòng cũ 40 ngày, đã gửi xong.
        row("sent-cu@test.com", EmailSendLog.STATUS_SENT,
                null, LocalDateTime.now().minusDays(40));

        int removed = repository.deleteOlderThan(LocalDateTime.now().minusDays(7));

        assertThat(removed).as("chỉ dòng đã kết thúc mới bị xoá").isEqualTo(1);
        assertThat(repository.findAll())
                .as("xoá cả PENDING nghĩa là xoá email lúc cần gửi nhất")
                .extracting(EmailSendLog::getRecipient)
                .containsExactly("pending-cu@test.com");
    }

    @Test
    @DisplayName("Chỉ lấy dòng PENDING đã tới hạn, không lấy dòng chưa tới hạn")
    void findDueForRetryRespectsTiming() {
        repository.deleteAll();
        row("chua-toi-han@test.com", EmailSendLog.STATUS_PENDING,
                LocalDateTime.now().plusMinutes(30), LocalDateTime.now());
        row("da-toi-han@test.com", EmailSendLog.STATUS_PENDING,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now());
        row("da-gui-xong@test.com", EmailSendLog.STATUS_SENT,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now());

        assertThat(repository.findDueForRetry(LocalDateTime.now(),
                        org.springframework.data.domain.PageRequest.of(0, 50)))
                .extracting(EmailSendLog::getRecipient)
                .containsExactly("da-toi-han@test.com");
    }
}

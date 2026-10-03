package com.ridebooking.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ridebooking.user.enums.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Che dữ liệu nhạy cảm: user thường xem hồ sơ người khác chỉ thấy họ tên và
 * email đã che, không thấy SĐT / ngày sinh / giới tính.
 */
class UserResponseMaskingTest {

    private UserResponse full() {
        return UserResponse.of(1, "Nguyễn Anh Huy", "0912345678", "huy.nguyen@gmail.com",
                java.time.LocalDate.of(2002, 5, 20), "Nam", Role.USER, "2026-01-01T10:00:00");
    }

    @Test
    void maskedView_hidesContactInfoAndDob() {
        UserResponse masked = full().maskedView();

        assertThat(masked.masked()).isTrue();
        assertThat(masked.fullName()).isEqualTo("Nguyễn Anh Huy");
        assertThat(masked.phone()).isNull();
        assertThat(masked.dob()).isNull();
        assertThat(masked.gender()).isNull();
        assertThat(masked.id()).isEqualTo(1);
    }

    @Test
    void maskedView_masksEmailKeepingDomain() {
        UserResponse masked = full().maskedView();

        assertThat(masked.email()).isEqualTo("h*********@gmail.com");
        assertThat(masked.email()).doesNotContain("huy.nguyen");
        // Vẫn cho biết có bao nhiêu ký tự đã che
        assertThat(masked.hiddenEmailChars()).isEqualTo(9);
    }

    @Test
    void of_isNotMasked() {
        UserResponse response = full();

        assertThat(response.masked()).isFalse();
        assertThat(response.email()).isEqualTo("huy.nguyen@gmail.com");
        assertThat(response.phone()).isEqualTo("0912345678");
    }

    @Test
    void maskedView_onAlreadyMasked_isIdempotent() {
        UserResponse once = full().maskedView();
        UserResponse twice = once.maskedView();

        assertThat(twice).isSameAs(once);
    }

    @Test
    void maskedView_withoutEmail_doesNotCrash() {
        UserResponse noEmail = UserResponse.of(1, "Khách", null, null, null, null, Role.USER, null);

        UserResponse masked = noEmail.maskedView();

        assertThat(masked.masked()).isTrue();
        assertThat(masked.email()).isNull();
        assertThat(masked.hiddenEmailChars()).isNull();
    }

    @Test
    void maskedView_keepsRoleAndCreatedAt() {
        UserResponse masked = full().maskedView();

        // Role và thời điểm tạo không phải dữ liệu cá nhân nhạy cảm
        assertThat(masked.role()).isEqualTo(Role.USER);
        assertThat(masked.createdAt()).isEqualTo("2026-01-01T10:00:00");
    }

    @ParameterizedTest
    @CsvSource({
            "'huy.nguyen@gmail.com', 'h*********@gmail.com'",
            "'a@gmail.com',                '*@gmail.com'",
            "'ab@gmail.com',               'a*@gmail.com'",
            "'khong-phai-email',           '***'" })
    void maskEmail_hidesLocalPart(String input, String expected) {
        assertThat(UserResponse.maskEmail(input)).isEqualTo(expected);
    }

    @Test
    void maskEmail_nullOrBlankReturnsNull() {
        assertThat(UserResponse.maskEmail(null)).isNull();
        assertThat(UserResponse.maskEmail("")).isNull();
        assertThat(UserResponse.maskEmail("   ")).isNull();
    }
}
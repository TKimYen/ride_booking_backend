package com.ridebooking.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

/**
 * Quy tắc mật khẩu: 8–72 ký tự (72 là giới hạn của BCrypt), phải có cả chữ
 * và chữ số, và không được là một trong các mật khẩu quá phổ biến.
 */
@Documented
@Constraint(validatedBy = StrongPassword.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Mật khẩu phải từ 8 đến 72 ký tự, có cả chữ và chữ số, không dùng mật khẩu phổ biến";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<StrongPassword, String> {

        private static final Pattern HAS_LETTER = Pattern.compile(".*[a-zA-Zàáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ].*");
        private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");

        private static final java.util.Set<String> WEAK_PASSWORDS = java.util.Set.of(
                "12345678", "password", "password1", "11111111", "123456789",
                "1234567890", "qwerty123", "admin123", "user1234", "matkhau123",
                "abcd1234", "a1234567", "pass1234", "iloveyou1");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isEmpty()) {
                // Để @NotBlank lo phần "không được để trống"
                return true;
            }
            if (value.length() < 8 || value.length() > 72) {
                return false;
            }
            if (WEAK_PASSWORDS.contains(value.toLowerCase())) {
                return false;
            }
            return HAS_LETTER.matcher(value).matches() && HAS_DIGIT.matcher(value).matches();
        }
    }
}
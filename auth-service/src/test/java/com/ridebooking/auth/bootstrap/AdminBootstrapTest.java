package com.ridebooking.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ridebooking.auth.config.AdminBootstrapProperties;
import com.ridebooking.auth.entity.User;
import com.ridebooking.auth.enums.Role;
import com.ridebooking.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.ridebooking.auth.client.UserServiceClient userServiceClient;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AdminBootstrap build(String email, String password) {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        properties.setEmail(email);
        properties.setPassword(password);
        properties.setFullName("Quản trị viên");
        return new AdminBootstrap(userRepository, passwordEncoder, properties, userServiceClient);
    }

    @Test
    void emptyEmail_doesNotCreateAnything() {
        build("", "Admin@12345").run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void newEmail_createsAdminWithHashedPassword() {
        when(userRepository.findByEmailIgnoreCase("admin@ridebooking.local")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        build("  Admin@RideBooking.Local  ", "Admin@12345").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User admin = captor.getValue();

        assertThat(admin.getEmail()).isEqualTo("admin@ridebooking.local");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getFullName()).isEqualTo("Quản trị viên");
        assertThat(admin.getPassword()).isNotEqualTo("Admin@12345");
        assertThat(passwordEncoder.matches("Admin@12345", admin.getPassword())).isTrue();
    }

    @Test
    void existingUser_keepsPasswordButPromotesToAdmin() {
        User existing = new User();
        existing.setId(7);
        existing.setEmail("a@test.com");
        existing.setPassword(passwordEncoder.encode("matkhau123"));
        existing.setRole(Role.USER);
        when(userRepository.findByEmailIgnoreCase("a@test.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        build("a@test.com", "Admin@12345").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
        // không đụng mật khẩu người dùng đã có
        assertThat(passwordEncoder.matches("matkhau123", captor.getValue().getPassword())).isTrue();
    }

    @Test
    void existingAdmin_isLeftUntouched() {
        User existing = new User();
        existing.setEmail("admin@ridebooking.local");
        existing.setRole(Role.ADMIN);
        when(userRepository.findByEmailIgnoreCase("admin@ridebooking.local")).thenReturn(Optional.of(existing));

        build("admin@ridebooking.local", "Admin@12345").run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shortPassword_failsLoudlyInsteadOfCreatingWeakAdmin() {
        when(userRepository.findByEmailIgnoreCase("admin@ridebooking.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> build("admin@ridebooking.local", "short").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("8 ký tự");

        verify(userRepository, never()).save(any(User.class));
    }
}
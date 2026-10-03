package com.ridebooking.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ridebooking.user.dto.CreateProfileRequest;
import com.ridebooking.user.dto.UpdateProfileRequest;
import com.ridebooking.user.entity.UserProfile;
import com.ridebooking.user.enums.Role;
import com.ridebooking.user.exception.ResourceNotFoundException;
import com.ridebooking.user.repository.UserProfileRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserProfileRepository profileRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(profileRepository);
        lenient().when(profileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getProfile_existingUser_returnsProfile() {
        when(profileRepository.findById(1)).thenReturn(Optional.of(buildProfile()));

        var response = userService.getProfile(1);

        assertThat(response.id()).isEqualTo(1);
        assertThat(response.fullName()).isEqualTo("Nguyễn Anh Huy");
        assertThat(response.email()).isEqualTo("huy@test.com");
        assertThat(response.role()).isEqualTo(Role.USER);
    }

    @Test
    void getProfile_unknownId_throwsNotFound() {
        when(profileRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateProfile_trimsAndNormalizesFields() {
        UserProfile profile = buildProfile();
        when(profileRepository.findById(1)).thenReturn(Optional.of(profile));

        UpdateProfileRequest request = new UpdateProfileRequest(
                "  Nguyễn Anh Huy 2  ", "  0999888777  ", LocalDate.of(2001, 1, 1), " Nữ ");

        var response = userService.updateProfile(1, request);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(captor.capture());
        UserProfile saved = captor.getValue();

        assertThat(saved.getFullName()).isEqualTo("Nguyễn Anh Huy 2");
        assertThat(saved.getPhone()).isEqualTo("0999888777");
        assertThat(saved.getGender()).isEqualTo("Nữ");
        assertThat(response.dob()).isEqualTo(LocalDate.of(2001, 1, 1));
    }

    @Test
    void updateProfile_blankPhoneAndGender_becomesNull() {
        UserProfile profile = buildProfile();
        when(profileRepository.findById(1)).thenReturn(Optional.of(profile));

        userService.updateProfile(1, new UpdateProfileRequest("Huy", "   ", null, "  "));

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(captor.capture());

        assertThat(captor.getValue().getPhone()).isNull();
        assertThat(captor.getValue().getGender()).isNull();
        assertThat(captor.getValue().getDob()).isNull();
    }

    @Test
    void search_withoutKeyword_usesFindAll() {
        when(profileRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.List.of(buildProfile())));

        var result = userService.search(null, 0, 10, true);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void upsertProfile_newUser_createsProfileWithSyncedRole() {
        when(profileRepository.findById(1)).thenReturn(Optional.empty());

        var response = userService.upsertProfile(new CreateProfileRequest(
                1, "Nguyễn Anh Huy", "huy@test.com", "0912345678",
                LocalDate.of(2002, 5, 20), "Nam", "USER"));

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(captor.capture());

        assertThat(captor.getValue().getUserId()).isEqualTo(1);
        assertThat(captor.getValue().getRole()).isEqualTo("USER");
        assertThat(response.role()).isEqualTo(Role.USER);
    }

    @Test
    void upsertProfile_existingProfile_keepsUserEditsButRefreshesRole() {
        UserProfile existing = buildProfile();
        existing.setPhone("0900111222");
        when(profileRepository.findById(1)).thenReturn(Optional.of(existing));

        userService.upsertProfile(new CreateProfileRequest(
                1, "Tên Từ Auth", "email-moi@test.com", "0912345678", null, null, "ADMIN"));

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(captor.capture());
        UserProfile saved = captor.getValue();

        // các trường người dùng tự sửa thì giữ nguyên
        assertThat(saved.getFullName()).isEqualTo("Nguyễn Anh Huy");
        assertThat(saved.getPhone()).isEqualTo("0900111222");
        // email và role đều do auth-service sở hữu nên phải ghi đè. Trước đây
        // email cũng đi qua keepExisting, nên sau khi đổi email hồ sơ giữ email
        // cũ vĩnh viễn và admin tìm theo email mới không ra.
        assertThat(saved.getEmail()).isEqualTo("email-moi@test.com");
        assertThat(saved.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void upsertProfile_existingProfile_withoutRole_keepsCurrentRole() {
        UserProfile existing = buildProfile();
        when(profileRepository.findById(1)).thenReturn(Optional.of(existing));

        userService.upsertProfile(new CreateProfileRequest(1, "Tên", "huy@test.com", null, null, null, null));

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("USER");
    }

    @Test
    void ensureProfile_missingProfile_createsFromJwtClaims() {
        when(profileRepository.findById(7)).thenReturn(Optional.empty());

        var response = userService.ensureProfile(
                7, "moi@test.com", "Người Mới", Role.USER);

        verify(profileRepository).save(any(UserProfile.class));
        assertThat(response.id()).isEqualTo(7);
        assertThat(response.email()).isEqualTo("moi@test.com");
        assertThat(response.fullName()).isEqualTo("Người Mới");
        assertThat(response.role()).isEqualTo(Role.USER);
    }

    @Test
    void ensureProfile_existingProfile_doesNotWrite() {
        when(profileRepository.findById(1)).thenReturn(Optional.of(buildProfile()));

        var response = userService.ensureProfile(1, "huy@test.com", "Nguyễn Anh Huy", Role.USER);

        verify(profileRepository, never()).save(any(UserProfile.class));
        assertThat(response.id()).isEqualTo(1);
    }

    /**
     * Tự chữa lành: nếu lúc đổi email user-service sập hoặc timeout, bản sao sẽ
     * giữ email cũ vĩnh viễn. Mỗi lần user mở trang Thông tin tài khoản là một
     * lần sửa lại, không cần job riêng.
     */
    @Test
    void ensureProfile_emailDrifted_repairsItself() {
        UserProfile stale = buildProfile(); // email = huy@test.com (đã cũ)
        when(profileRepository.findById(1)).thenReturn(Optional.of(stale));

        var response = userService.ensureProfile(1, "email-moi@test.com", "Nguyễn Anh Huy", Role.USER);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("email-moi@test.com");
        assertThat(response.email()).isEqualTo("email-moi@test.com");
    }

    @Test
    void ensureProfile_roleDrifted_repairsItself() {
        UserProfile stale = buildProfile();
        stale.setRole("USER");
        when(profileRepository.findById(1)).thenReturn(Optional.of(stale));

        userService.ensureProfile(1, "huy@test.com", "Nguyễn Anh Huy", Role.ADMIN);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("ADMIN");
    }

    @Test
    void upsertProfile_emailNull_doesNotWipeEmail() {
        UserProfile existing = buildProfile();
        when(profileRepository.findById(1)).thenReturn(Optional.of(existing));

        userService.upsertProfile(
                new CreateProfileRequest(1, "Tên", null, null, null, null, null));

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(captor.capture());
        // auth-service gửi rỗng thì giữ email cũ, không xoá mất
        assertThat(captor.getValue().getEmail()).isEqualTo("huy@test.com");
    }

    @Test
    void toResponse_unknownRoleValue_fallsBackToUser() {
        UserProfile profile = buildProfile();
        profile.setRole("SUPER_ADMIN");

        when(profileRepository.findById(1)).thenReturn(Optional.of(profile));

        assertThat(userService.getProfile(1).role()).isEqualTo(Role.USER);
    }

    private UserProfile buildProfile() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1);
        profile.setFullName("Nguyễn Anh Huy");
        profile.setEmail("huy@test.com");
        profile.setPhone("0912345678");
        profile.setDob(LocalDate.of(2002, 5, 20));
        profile.setGender("Nam");
        profile.setRole("USER");
        profile.setCreatedAt(LocalDateTime.now());
        return profile;
    }
}
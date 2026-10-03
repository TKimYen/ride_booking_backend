package com.ridebooking.user.service;

import com.ridebooking.user.dto.CreateProfileRequest;
import com.ridebooking.user.dto.PageResponse;
import com.ridebooking.user.dto.UpdateProfileRequest;
import com.ridebooking.user.dto.UserResponse;
import com.ridebooking.user.entity.UserProfile;
import com.ridebooking.user.enums.Role;
import com.ridebooking.user.exception.ResourceNotFoundException;
import com.ridebooking.user.repository.UserProfileRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module Quản lý Cá nhân (nhóm Frontend: Trang Thông tin tài khoản)
 * và danh sách người dùng cho Admin.
 *
 * <p>Database {@code user_db} chỉ chứa hồ sơ; mật khẩu nằm ở {@code auth_db.users}.
 * Vì vậy {@code role} ở đây là bản sao được auth-service đồng bộ sang.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserProfileRepository profileRepository;

    public UserService(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /** Hồ sơ của chính mình - luôn thấy đầy đủ. */
    @Transactional(readOnly = true)
    /**
     * Lấy nhiều hồ sơ trong một lần gọi.
     *
     * <p>Cho booking-service đọc danh sách đánh giá: mỗi đánh giá có một
     * {@code user_id} khác nhau, gọi endpoint theo id sẽ tốn N request và N lần
     * đọc database cho một trang duy nhất.
     *
     * <p>Hồ sơ không tồn tại bị BỎ QUA chứ không ném lỗi 404: một id không có hồ
     * sơ không được làm hỏng cả trang đánh giá. Người gọi tự hiển thị "Người dùng
     * không còn tồn tại" cho id đó.
     */
    public List<UserResponse> getProfiles(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        // distinct: một trang đánh giá có thể có nhiều đánh giá của cùng một
        // người, và trả về trùng lặp thì khách gọi vẫn phải lọc lại.
        return profileRepository.findAllById(userIds.stream().distinct().toList()).stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse getProfile(Integer userId) {
        return toResponse(findById(userId));
    }

    @Transactional
    public UserResponse updateProfile(Integer userId, UpdateProfileRequest request) {
        UserProfile profile = findById(userId);
        profile.setFullName(request.fullName().trim());
        profile.setPhone(trimToNull(request.phone()));
        profile.setDob(request.dob());
        profile.setGender(trimToNull(request.gender()));
        return toResponse(profileRepository.save(profile));
    }

    /**
     * Xem hồ sơ người khác: admin và chính mình thấy đầy đủ, user thường chỉ thấy
     * họ tên + email đã che. Không trả 403 vì frontend vẫn cần tên hiển thị.
     */
    @Transactional(readOnly = true)
    public UserResponse getByIdAs(Integer userId, Integer viewerId, boolean viewerIsAdmin) {
        UserResponse response = toResponse(findById(userId));
        return viewerIsAdmin || userId.equals(viewerId) ? response : response.maskedView();
    }

    /**
     * Đồng bộ hồ sơ từ auth-service. Idempotent: trường do chính người dùng sửa ở
     * trang Thông tin tài khoản (họ tên, SĐT, ngày sinh, giới tính) thì chỉ bổ
     * sung khi còn trống, không ghi đè dữ liệu họ đã nhập.
     *
     * <p>Riêng {@code email} và {@code role} là dữ liệu của auth-service nên LUÔN
     * ghi đè. Trước đây email cũng đi qua {@link #keepExisting}, nên sau khi đổi
     * email hồ sơ vẫn giữ email cũ vĩnh viễn: admin tìm theo email mới không ra,
     * và người dùng tự xem hồ sơ cũng thấy email đã không còn dùng.
     */
    @Transactional
    public UserResponse upsertProfile(CreateProfileRequest request) {
        UserProfile profile = profileRepository.findById(request.userId())
                .orElseGet(() -> {
                    UserProfile created = new UserProfile();
                    created.setUserId(request.userId());
                    return created;
                });

        profile.setFullName(keepExisting(profile.getFullName(), trimToNull(request.fullName())));
        // Không dùng keepExisting: không có "giữ giá trị cũ" cho email, vì giá
        // trị cũ chính là thứ đã bị thay. Chỉ bỏ qua khi auth-service gửi
        // rỗng, để không xoá mất email vì một lần gọi thiếu dữ liệu.
        String email = trimToNull(request.email());
        if (email != null) {
            profile.setEmail(email);
        }
        profile.setPhone(keepExisting(profile.getPhone(), trimToNull(request.phone())));
        profile.setDob(profile.getDob() != null ? profile.getDob() : request.dob());
        profile.setGender(keepExisting(profile.getGender(), trimToNull(request.gender())));
        String role = trimToNull(request.role());
        if (role != null) {
            profile.setRole(role);
        }

        return toResponse(profileRepository.save(profile));
    }

    /**
     * Đảm bảo hồ sơ tồn tại và khớp với auth-service.
     *
     * <p>Hai việc: tạo hồ sơ nếu chưa có (đăng ký lúc user-service sập), và sửa
     * lại email/role nếu lệch. Việc sửa là cần thiết vì nếu lúc đổi email
     * auth-service gọi sang user-service mà user-service đang sập hoặc timeout,
     * bản sao sẽ giữ email cũ vĩnh viễn. Mỗi lần người dùng mở trang Thông tin
     * tài khoản là một lần tự sửa lại, không cần job riêng.
     */
    @Transactional
    public UserResponse ensureProfile(Integer userId, String email, String fullName, Role role) {
        UserProfile existing = profileRepository.findById(userId).orElse(null);
        if (existing == null) {
            UserResponse created = upsertProfile(
                    new CreateProfileRequest(userId, fullName, email, null, null, null,
                            role == null ? null : role.name()));
            log.info("Tự tạo hồ sơ cho userId={} do đăng ký lúc user-service chưa sẵn sàng", userId);
            return created;
        }

        String wantedEmail = trimToNull(email);
        String wantedRole = role == null ? null : role.name();
        boolean emailDrifted = wantedEmail != null && !wantedEmail.equals(existing.getEmail());
        boolean roleDrifted = wantedRole != null && !wantedRole.equals(existing.getRole());
        if (emailDrifted || roleDrifted) {
            log.warn("Hồ sơ userId={} lệch với auth-service (email: {} -> {}, role: {} -> {}), tự sửa lại",
                    userId, existing.getEmail(), wantedEmail, existing.getRole(), wantedRole);
            return upsertProfile(new CreateProfileRequest(userId, existing.getFullName(), wantedEmail,
                    existing.getPhone(), existing.getDob(), existing.getGender(), wantedRole));
        }
        return toResponse(existing);
    }

    /**
     * Tra cứu hành khách theo tên / email / SĐT. Admin thấy đầy đủ; user thường
     * xem danh sách để hiển thị tên (chọn tài xế, đánh giá chuyến xe) nên các
     * trường liên hệ bị che.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String keyword, int page, int size, boolean viewerIsAdmin) {
        String normalized = keyword == null ? "" : keyword.trim();
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<UserProfile> result = normalized.isBlank()
                ? profileRepository.findAll(pageable)
                : profileRepository.search(normalized, pageable);

        return PageResponse.from(result.map(profile -> {
            UserResponse response = toResponse(profile);
            return viewerIsAdmin ? response : response.maskedView();
        }));
    }

    /** Admin xem toàn bộ - không che gì. */
    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return profileRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    private UserProfile findById(Integer userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + userId));
    }

    private UserResponse toResponse(UserProfile profile) {
        return UserResponse.of(
                profile.getUserId(),
                profile.getFullName(),
                profile.getPhone(),
                profile.getEmail(),
                profile.getDob(),
                profile.getGender(),
                parseRole(profile.getRole()),
                profile.getCreatedAt() == null ? null : profile.getCreatedAt().toString());
    }

    private Role parseRole(String value) {
        if (value == null || value.isBlank()) {
            return Role.USER;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Role.USER;
        }
    }

    private String keepExisting(String current, String incoming) {
        return current != null ? current : incoming;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
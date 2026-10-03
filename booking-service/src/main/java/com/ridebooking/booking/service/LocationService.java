package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.LocationRequest;
import com.ridebooking.booking.dto.LocationResponse;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.util.LikePattern;
import java.text.Normalizer;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản lý danh sách tỉnh/thành phố. */
@Service
@Transactional(readOnly = true)
public class LocationService {

    /** Số gợi ý tối đa trả về cho dropdown. */
    static final int SUGGEST_LIMIT = 10;

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    /**
     * Danh sách địa điểm cho dropdown.
     *
     * <p>Không tìm thì trả tên các địa điểm phổ biến nhất — ở đây chính là toàn
     * bộ danh sách vì dữ liệu công khai chỉ vài chục dòng, và dropdown vẫn dùng
     * được khi người dùng chưa gõ gì.
     */
    public List<LocationResponse> suggest(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listAll();
        }
        return search(keyword, SUGGEST_LIMIT);
    }

    /** Gợi ý theo từ khoá, chỉ khớp ở đầu tên để "Hà" ra "Hà Nội"/"Hà Giang". */
    public List<LocationResponse> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String escaped = LikePattern.escape(keyword.trim());
        int safeLimit = Math.clamp(limit, 1, SUGGEST_LIMIT);
        return locationRepository.suggest(escaped, PageRequest.of(0, safeLimit))
                .stream().map(LocationResponse::from).toList();
    }

    public List<LocationResponse> listAll() {
        return locationRepository.findAll().stream()
                .map(LocationResponse::from)
                .toList();
    }

    public LocationResponse getById(Integer id) {
        return LocationResponse.from(requireById(id));
    }

    @Transactional
    public LocationResponse create(LocationRequest request) {
        String slug = resolveSlug(request.slug(), request.name());
        ensureSlugFree(slug, null);

        Location location = new Location();
        location.setName(request.name().trim());
        location.setSlug(slug);
        return LocationResponse.from(locationRepository.save(location));
    }

    @Transactional
    public LocationResponse update(Integer id, LocationRequest request) {
        Location location = requireById(id);
        String slug = resolveSlug(request.slug(), request.name());
        ensureSlugFree(slug, id);

        location.setName(request.name().trim());
        location.setSlug(slug);
        return LocationResponse.from(locationRepository.save(location));
    }

    /**
     * Xoá địa điểm.
     *
     * <p>Từ chối nếu còn tuyến nào trỏ tới. Khi đó trả 409 chứ không phải 500 từ
     * khoá ngoại — thông điệp cho Admin biết phải xoá tuyến trước.
     */
    @Transactional
    public void delete(Integer id) {
        Location location = requireById(id);
        locationRepository.delete(location);
    }

    private Location requireById(Integer id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm với id " + id));
    }

    /**
     * Slug lấy từ tên nếu Admin không nhập.
     *
     * <p>Bỏ dấu tiếng Việt rồi đưa về chữ thường: "Hà Giang" -> "ha-giang".
     * Lỗi ở đây rất dễ bỏ sót vì dấu tiếng Việt tổ hợp (ví dụ "ệ" là 2 codepoint)
     * và regex {@code \p{ASCII}} một mình cắt mất chữ.
     */
    public static String resolveSlug(String provided, String name) {
        if (provided != null && !provided.isBlank()) {
            return provided.trim();
        }
        // Đổi "đ" thành "d" TRƯỚC khi bỏ dấu. NFD không tách được chữ đ: nó là một
        // codepoint riêng (U+0110) chứ không phải "d" + dấu, nên regex sẽ xoá nó
        // thành dấu gạch nối và "Đà Nẵng" ra slug "a-nang".
        // Còn "ơ", "ư" thì NFD tách được (o + dấu sừng), không cần xử lý riêng.
        String withoutDStroke = name.trim()
                .replace("đ", "d")
                .replace("Đ", "D");
        String stripped = Normalizer.normalize(withoutDStroke, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug = stripped.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("Tên địa điểm không tạo được slug, nhập slug thủ công");
        }
        return slug;
    }

    private void ensureSlugFree(String slug, Integer excludeId) {
        locationRepository.findBySlug(slug)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ConflictException("Slug '" + slug + "' đã dùng bởi địa điểm khác");
                });
    }
}
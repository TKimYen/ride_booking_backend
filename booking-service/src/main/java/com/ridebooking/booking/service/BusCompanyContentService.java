package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.AmenityRequest;
import com.ridebooking.booking.dto.AmenityResponse;
import com.ridebooking.booking.dto.BusCompanyProfileResponse;
import com.ridebooking.booking.dto.CancellationRuleRequest;
import com.ridebooking.booking.dto.CancellationRuleResponse;
import com.ridebooking.booking.dto.CompanyImageRequest;
import com.ridebooking.booking.dto.CompanyImageResponse;
import com.ridebooking.booking.dto.CompanyPolicyRequest;
import com.ridebooking.booking.dto.CompanyPolicyResponse;
import com.ridebooking.booking.entity.Amenity;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.CancellationRule;
import com.ridebooking.booking.entity.CompanyImage;
import com.ridebooking.booking.entity.CompanyPolicy;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.AmenityRepository;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.CancellationRuleRepository;
import com.ridebooking.booking.repository.CompanyImageRepository;
import com.ridebooking.booking.repository.CompanyPolicyRepository;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nội dung nhà xe cho Trang chi tiết: ảnh, tiện ích, chính sách, mốc hoàn tiền.
 *
 * <p>Mọi thao tác trên một mục đều kiểm tra mục đó thuộc đúng nhà xe trên URL, để
 * {@code /bus-companies/1/images/9} không sửa được ảnh của nhà xe khác.
 */
@Service
@Transactional(readOnly = true)
public class BusCompanyContentService {

    private final BusCompanyRepository busCompanyRepository;
    private final CompanyImageRepository imageRepository;
    private final CompanyPolicyRepository policyRepository;
    private final CancellationRuleRepository ruleRepository;
    private final AmenityRepository amenityRepository;
    private final ReviewService reviewService;

    public BusCompanyContentService(BusCompanyRepository busCompanyRepository,
                                    CompanyImageRepository imageRepository,
                                    CompanyPolicyRepository policyRepository,
                                    CancellationRuleRepository ruleRepository,
                                    AmenityRepository amenityRepository,
                                    ReviewService reviewService) {
        this.reviewService = reviewService;
        this.busCompanyRepository = busCompanyRepository;
        this.imageRepository = imageRepository;
        this.policyRepository = policyRepository;
        this.ruleRepository = ruleRepository;
        this.amenityRepository = amenityRepository;
    }

    public BusCompanyProfileResponse getProfile(Integer companyId) {
        BusCompany company = requireCompany(companyId);
        return BusCompanyProfileResponse.of(company,
                reviewService.ratingsOf(List.of(companyId)).get(companyId),
                imageRepository.findByCompanyIdOrderBySortOrderAscIdAsc(companyId).stream()
                        .map(CompanyImageResponse::from).toList(),
                company.getAmenities().stream()
                        .sorted(Comparator.comparing(Amenity::getName))
                        .map(AmenityResponse::from).toList(),
                policyRepository.findByCompanyIdOrderBySortOrderAscIdAsc(companyId).stream()
                        .map(CompanyPolicyResponse::from).toList(),
                ruleRepository.findByCompanyIdOrderByHoursBeforeDesc(companyId).stream()
                        .map(CancellationRuleResponse::from).toList());
    }

    // ---- Ảnh ----

    @Transactional
    public CompanyImageResponse addImage(Integer companyId, CompanyImageRequest request) {
        CompanyImage image = new CompanyImage();
        image.setCompany(requireCompany(companyId));
        apply(image, request);
        return CompanyImageResponse.from(imageRepository.save(image));
    }

    @Transactional
    public CompanyImageResponse updateImage(Integer companyId, Integer imageId, CompanyImageRequest request) {
        CompanyImage image = requireImage(companyId, imageId);
        apply(image, request);
        return CompanyImageResponse.from(image);
    }

    @Transactional
    public void deleteImage(Integer companyId, Integer imageId) {
        imageRepository.delete(requireImage(companyId, imageId));
    }

    private void apply(CompanyImage image, CompanyImageRequest request) {
        image.setImageUrl(request.imageUrl().trim());
        image.setCaption(request.caption());
        image.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 1);
    }

    private CompanyImage requireImage(Integer companyId, Integer imageId) {
        return imageRepository.findByIdAndCompanyId(imageId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nhà xe " + companyId + " không có ảnh với id " + imageId));
    }

    // ---- Chính sách ----

    @Transactional
    public CompanyPolicyResponse addPolicy(Integer companyId, CompanyPolicyRequest request) {
        CompanyPolicy policy = new CompanyPolicy();
        policy.setCompany(requireCompany(companyId));
        apply(policy, request);
        return CompanyPolicyResponse.from(policyRepository.save(policy));
    }

    @Transactional
    public CompanyPolicyResponse updatePolicy(Integer companyId, Integer policyId, CompanyPolicyRequest request) {
        CompanyPolicy policy = requirePolicy(companyId, policyId);
        apply(policy, request);
        return CompanyPolicyResponse.from(policy);
    }

    @Transactional
    public void deletePolicy(Integer companyId, Integer policyId) {
        policyRepository.delete(requirePolicy(companyId, policyId));
    }

    private void apply(CompanyPolicy policy, CompanyPolicyRequest request) {
        policy.setPolicyType(request.policyType());
        policy.setTitle(request.title().trim());
        policy.setContent(request.content());
        policy.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 1);
    }

    private CompanyPolicy requirePolicy(Integer companyId, Integer policyId) {
        return policyRepository.findByIdAndCompanyId(policyId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nhà xe " + companyId + " không có chính sách với id " + policyId));
    }

    // ---- Mốc hoàn tiền ----

    @Transactional
    public CancellationRuleResponse addRule(Integer companyId, CancellationRuleRequest request) {
        BusCompany company = requireCompany(companyId);
        requireHoursFree(companyId, request.hoursBefore(), null);
        return CancellationRuleResponse.from(ruleRepository.save(
                new CancellationRule(company, request.hoursBefore(), request.refundPercent())));
    }

    @Transactional
    public CancellationRuleResponse updateRule(Integer companyId, Integer ruleId, CancellationRuleRequest request) {
        CancellationRule rule = requireRule(companyId, ruleId);
        requireHoursFree(companyId, request.hoursBefore(), ruleId);
        rule.setHoursBefore(request.hoursBefore());
        rule.setRefundPercent(request.refundPercent());
        return CancellationRuleResponse.from(rule);
    }

    @Transactional
    public void deleteRule(Integer companyId, Integer ruleId) {
        ruleRepository.delete(requireRule(companyId, ruleId));
    }

    private void requireHoursFree(Integer companyId, Integer hoursBefore, Integer ignoredRuleId) {
        ruleRepository.findForCompanyAtHours(companyId, hoursBefore)
                .filter(existing -> !existing.getId().equals(ignoredRuleId))
                .ifPresent(existing -> {
                    throw new ConflictException("Nhà xe đã có mốc hoàn tiền " + hoursBefore + " giờ");
                });
    }

    private CancellationRule requireRule(Integer companyId, Integer ruleId) {
        return ruleRepository.findByIdAndCompanyId(ruleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nhà xe " + companyId + " không có mốc hoàn tiền với id " + ruleId));
    }

    // ---- Tiện ích ----

    public List<AmenityResponse> listAmenities() {
        return amenityRepository.findAll().stream()
                .sorted(Comparator.comparing(Amenity::getName))
                .map(AmenityResponse::from)
                .toList();
    }

    @Transactional
    public AmenityResponse createAmenity(AmenityRequest request) {
        String code = normalizeCode(request.code());
        requireCodeFree(code, null);
        return AmenityResponse.from(amenityRepository.save(
                new Amenity(code, request.name().trim(), request.icon())));
    }

    @Transactional
    public AmenityResponse updateAmenity(Integer id, AmenityRequest request) {
        Amenity amenity = requireAmenity(id);
        String code = normalizeCode(request.code());
        requireCodeFree(code, id);
        amenity.setCode(code);
        amenity.setName(request.name().trim());
        amenity.setIcon(request.icon());
        return AmenityResponse.from(amenity);
    }

    /** Từ chối khi còn nhà xe dùng: khoá ngoại CASCADE sẽ âm thầm gỡ tiện ích khỏi các nhà xe đó. */
    @Transactional
    public void deleteAmenity(Integer id) {
        Amenity amenity = requireAmenity(id);
        if (busCompanyRepository.existsByAmenities_Id(id)) {
            throw new ConflictException("Tiện ích đang được nhà xe sử dụng, hãy gỡ khỏi nhà xe trước");
        }
        amenityRepository.delete(amenity);
    }

    /** Thay toàn bộ tiện ích của nhà xe; id nào không tồn tại thì báo 404, không bỏ qua. */
    @Transactional
    public List<AmenityResponse> replaceCompanyAmenities(Integer companyId, List<Integer> amenityIds) {
        BusCompany company = requireCompany(companyId);
        LinkedHashSet<Amenity> amenities = new LinkedHashSet<>();
        for (Integer amenityId : new LinkedHashSet<>(amenityIds)) {
            amenities.add(requireAmenity(amenityId));
        }
        company.getAmenities().clear();
        company.getAmenities().addAll(amenities);
        return amenities.stream()
                .sorted(Comparator.comparing(Amenity::getName))
                .map(AmenityResponse::from)
                .toList();
    }

    private String normalizeCode(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private void requireCodeFree(String code, Integer ignoredId) {
        amenityRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(ignoredId))
                .ifPresent(existing -> {
                    throw new ConflictException("Mã tiện ích '" + code + "' đã tồn tại");
                });
    }

    private Amenity requireAmenity(Integer id) {
        return amenityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tiện ích với id " + id));
    }

    private BusCompany requireCompany(Integer id) {
        return busCompanyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà xe với id " + id));
    }
}

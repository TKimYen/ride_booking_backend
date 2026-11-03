package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.AmenityRequest;
import com.ridebooking.booking.dto.AmenityResponse;
import com.ridebooking.booking.dto.CancellationRuleRequest;
import com.ridebooking.booking.dto.CancellationRuleResponse;
import com.ridebooking.booking.dto.CompanyAmenitiesRequest;
import com.ridebooking.booking.dto.CompanyImageRequest;
import com.ridebooking.booking.dto.CompanyImageResponse;
import com.ridebooking.booking.dto.CompanyPolicyRequest;
import com.ridebooking.booking.dto.CompanyPolicyResponse;
import com.ridebooking.booking.service.BusCompanyContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin quản lý nội dung nhà xe: ảnh, chính sách, mốc hoàn tiền, tiện ích. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "4. Admin - Nội dung nhà xe", description = "Ảnh, tiện ích, chính sách, quy định hoàn huỷ của nhà xe")
public class AdminCompanyContentController {

    private final BusCompanyContentService contentService;

    public AdminCompanyContentController(BusCompanyContentService contentService) {
        this.contentService = contentService;
    }

    @Operation(summary = "Thêm ảnh nhà xe")
    @PostMapping("/bus-companies/{companyId}/images")
    public ResponseEntity<CompanyImageResponse> addImage(@PathVariable Integer companyId,
                                                         @Valid @RequestBody CompanyImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contentService.addImage(companyId, request));
    }

    @Operation(summary = "Sửa ảnh nhà xe")
    @PutMapping("/bus-companies/{companyId}/images/{imageId}")
    public CompanyImageResponse updateImage(@PathVariable Integer companyId, @PathVariable Integer imageId,
                                            @Valid @RequestBody CompanyImageRequest request) {
        return contentService.updateImage(companyId, imageId, request);
    }

    @Operation(summary = "Xoá ảnh nhà xe")
    @DeleteMapping("/bus-companies/{companyId}/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Integer companyId, @PathVariable Integer imageId) {
        contentService.deleteImage(companyId, imageId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Thêm chính sách nhà xe")
    @PostMapping("/bus-companies/{companyId}/policies")
    public ResponseEntity<CompanyPolicyResponse> addPolicy(@PathVariable Integer companyId,
                                                           @Valid @RequestBody CompanyPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contentService.addPolicy(companyId, request));
    }

    @Operation(summary = "Sửa chính sách nhà xe")
    @PutMapping("/bus-companies/{companyId}/policies/{policyId}")
    public CompanyPolicyResponse updatePolicy(@PathVariable Integer companyId, @PathVariable Integer policyId,
                                              @Valid @RequestBody CompanyPolicyRequest request) {
        return contentService.updatePolicy(companyId, policyId, request);
    }

    @Operation(summary = "Xoá chính sách nhà xe")
    @DeleteMapping("/bus-companies/{companyId}/policies/{policyId}")
    public ResponseEntity<Void> deletePolicy(@PathVariable Integer companyId, @PathVariable Integer policyId) {
        contentService.deletePolicy(companyId, policyId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Thêm mốc hoàn tiền", description = "Mỗi nhà xe chỉ có một mốc cho mỗi số giờ, trùng thì 409.")
    @PostMapping("/bus-companies/{companyId}/cancellation-rules")
    public ResponseEntity<CancellationRuleResponse> addRule(@PathVariable Integer companyId,
                                                            @Valid @RequestBody CancellationRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contentService.addRule(companyId, request));
    }

    @Operation(summary = "Sửa mốc hoàn tiền")
    @PutMapping("/bus-companies/{companyId}/cancellation-rules/{ruleId}")
    public CancellationRuleResponse updateRule(@PathVariable Integer companyId, @PathVariable Integer ruleId,
                                               @Valid @RequestBody CancellationRuleRequest request) {
        return contentService.updateRule(companyId, ruleId, request);
    }

    @Operation(summary = "Xoá mốc hoàn tiền")
    @DeleteMapping("/bus-companies/{companyId}/cancellation-rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable Integer companyId, @PathVariable Integer ruleId) {
        contentService.deleteRule(companyId, ruleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Đặt lại tiện ích của nhà xe", description = "Thay toàn bộ danh sách. Danh sách rỗng là bỏ hết.")
    @PutMapping("/bus-companies/{companyId}/amenities")
    public List<AmenityResponse> replaceAmenities(@PathVariable Integer companyId,
                                                  @Valid @RequestBody CompanyAmenitiesRequest request) {
        return contentService.replaceCompanyAmenities(companyId, request.amenityIds());
    }

    @Operation(summary = "Danh mục tiện ích")
    @GetMapping("/amenities")
    public List<AmenityResponse> listAmenities() {
        return contentService.listAmenities();
    }

    @Operation(summary = "Thêm tiện ích vào danh mục")
    @PostMapping("/amenities")
    public ResponseEntity<AmenityResponse> createAmenity(@Valid @RequestBody AmenityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contentService.createAmenity(request));
    }

    @Operation(summary = "Sửa tiện ích trong danh mục")
    @PutMapping("/amenities/{id}")
    public AmenityResponse updateAmenity(@PathVariable Integer id, @Valid @RequestBody AmenityRequest request) {
        return contentService.updateAmenity(id, request);
    }

    @Operation(summary = "Xoá tiện ích khỏi danh mục", description = "Từ chối (409) khi còn nhà xe đang dùng.")
    @DeleteMapping("/amenities/{id}")
    public ResponseEntity<Void> deleteAmenity(@PathVariable Integer id) {
        contentService.deleteAmenity(id);
        return ResponseEntity.noContent().build();
    }
}

package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.CompanyPolicy;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một chính sách của nhà xe")
public record CompanyPolicyResponse(
        @Schema(example = "1") Integer id,
        @Schema(example = "LUGGAGE") String policyType,
        @Schema(example = "Hành lý") String title,
        @Schema(example = "Mỗi khách được mang 20kg hành lý.") String content,
        @Schema(example = "1") Integer sortOrder) {

    public static CompanyPolicyResponse from(CompanyPolicy policy) {
        return new CompanyPolicyResponse(policy.getId(), policy.getPolicyType().name(), policy.getTitle(),
                policy.getContent(), policy.getSortOrder());
    }
}

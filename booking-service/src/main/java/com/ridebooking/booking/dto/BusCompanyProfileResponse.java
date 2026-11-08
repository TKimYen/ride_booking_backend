package com.ridebooking.booking.dto;

import com.ridebooking.booking.entity.BusCompany;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Toàn bộ nội dung nhà xe cho các tab của Trang chi tiết. */
@Schema(description = "Thông tin đầy đủ của nhà xe")
public record BusCompanyProfileResponse(
        @Schema(example = "3") Integer id,
        @Schema(example = "G8 SAPA OPEN TOUR") String name,
        @Schema(description = "Mô tả nhà xe") String description,
        @Schema(description = "URL logo") String logoUrl,
        @Schema(description = "Điểm trung bình trên mọi chuyến của nhà xe, không tính đánh giá đã ẩn")
        CompanyRatingResponse rating,
        List<CompanyImageResponse> images,
        List<AmenityResponse> amenities,
        List<CompanyPolicyResponse> policies,
        @Schema(description = "Mốc hoàn tiền, mốc giờ lớn nhất trước") List<CancellationRuleResponse> cancellationRules) {

    public static BusCompanyProfileResponse of(BusCompany company,
                                               CompanyRatingResponse rating,
                                               List<CompanyImageResponse> images,
                                               List<AmenityResponse> amenities,
                                               List<CompanyPolicyResponse> policies,
                                               List<CancellationRuleResponse> cancellationRules) {
        return new BusCompanyProfileResponse(company.getId(), company.getName(), company.getDescription(),
                company.getLogoUrl(), rating, images, amenities, policies, cancellationRules);
    }
}

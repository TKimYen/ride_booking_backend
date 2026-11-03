package com.ridebooking.booking.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ridebooking.booking.dto.BusCompanyProfileResponse;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Promotion;
import com.ridebooking.booking.repository.AmenityRepository;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.CompanyImageRepository;
import com.ridebooking.booking.repository.CompanyPolicyRepository;
import com.ridebooking.booking.repository.PromotionRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.service.BusCompanyContentService;
import com.ridebooking.booking.service.RouteService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Dữ liệu mẫu [3.7].
 *
 * <p>Dùng database H2 riêng: seed sinh hàng nghìn ghế, chung database với các test
 * khác thì khoá ngoại ảnh/chính sách sẽ làm {@code deleteAll} của chúng thất bại.
 */
@SpringBootTest(properties = {
        "app.seed.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:seeddb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
})
class SeedDataLoaderTest {

    @Autowired private SeedDataLoader seedDataLoader;
    @Autowired private BusCompanyRepository busCompanyRepository;
    @Autowired private BusCompanyContentService contentService;
    @Autowired private RouteService routeService;
    @Autowired private RouteRepository routeRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private AmenityRepository amenityRepository;
    @Autowired private CompanyImageRepository companyImageRepository;
    @Autowired private CompanyPolicyRepository companyPolicyRepository;
    @Autowired private PromotionRepository promotionRepository;

    @Test
    @DisplayName("Mỗi nhà xe có đủ ảnh, tiện ích, chính sách và mốc hoàn tiền")
    void everyCompanyHasFullProfile() {
        List<BusCompany> companies = busCompanyRepository.findAll();
        assertThat(companies).isNotEmpty();

        for (BusCompany company : companies) {
            BusCompanyProfileResponse profile = contentService.getProfile(company.getId());
            assertThat(profile.images()).as(company.getName()).isNotEmpty();
            assertThat(profile.amenities()).as(company.getName()).hasSizeGreaterThanOrEqualTo(4);
            assertThat(profile.policies()).as(company.getName()).isNotEmpty();
            assertThat(profile.cancellationRules()).as(company.getName()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("Có 4-6 mã khuyến mãi, tất cả đang hiệu lực")
    void promotionsAreActive() {
        List<Promotion> promotions = promotionRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        assertThat(promotions).hasSizeBetween(4, 6);
        assertThat(promotions).allMatch(promotion -> promotion.isActive()
                && promotion.getStartsAt().isBefore(now) && promotion.getEndsAt().isAfter(now));
        assertThat(promotionRepository.findFeatured(now)).isNotEmpty();
    }

    @Test
    @DisplayName("Tuyến nổi bật có ảnh và có giá thấp nhất để hiện ở Trang chủ")
    void featuredRoutesAreReadyForHomePage() {
        assertThat(routeRepository.findFeaturedWithLocations()).hasSizeGreaterThanOrEqualTo(4);

        assertThat(routeService.popular(20))
                .isNotEmpty()
                .allMatch(route -> route.featured() && route.imageUrl() != null && route.minPrice() != null);
    }

    @Test
    @DisplayName("Chạy lại không nhân bản dữ liệu")
    void runningAgainAddsNothing() {
        long trips = tripRepository.count();
        long amenities = amenityRepository.count();
        long images = companyImageRepository.count();
        long policies = companyPolicyRepository.count();
        long promotions = promotionRepository.count();

        seedDataLoader.run(null);

        assertThat(tripRepository.count()).isEqualTo(trips);
        assertThat(amenityRepository.count()).isEqualTo(amenities);
        assertThat(companyImageRepository.count()).isEqualTo(images);
        assertThat(companyPolicyRepository.count()).isEqualTo(policies);
        assertThat(promotionRepository.count()).isEqualTo(promotions);
    }
}

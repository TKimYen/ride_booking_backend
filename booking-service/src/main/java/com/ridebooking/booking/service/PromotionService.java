package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.PromotionRequest;
import com.ridebooking.booking.dto.PromotionResponse;
import com.ridebooking.booking.dto.PromotionValidateRequest;
import com.ridebooking.booking.dto.PromotionValidateResponse;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Promotion;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.entity.TripSeat;
import com.ridebooking.booking.enums.DiscountType;
import com.ridebooking.booking.exception.BadRequestException;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.PromotionRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final TripRepository tripRepository;
    private final TripSeatRepository tripSeatRepository;
    private final BusCompanyRepository busCompanyRepository;
    private final RouteRepository routeRepository;

    public PromotionService(PromotionRepository promotionRepository,
                            TripRepository tripRepository,
                            TripSeatRepository tripSeatRepository,
                            BusCompanyRepository busCompanyRepository,
                            RouteRepository routeRepository) {
        this.promotionRepository = promotionRepository;
        this.tripRepository = tripRepository;
        this.tripSeatRepository = tripSeatRepository;
        this.busCompanyRepository = busCompanyRepository;
        this.routeRepository = routeRepository;
    }

    /**
     * Lấy danh sách mã khuyến mãi đang dùng được.
     * <ul>
     *   <li>{@code featured=true}: ưu đãi nổi bật trang chủ.</li>
     *   <li>{@code tripId}: mã áp dụng được cho chuyến xe cụ thể.</li>
     *   <li>Mặc định: tất cả mã còn hiệu lực trên toàn hệ thống.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<PromotionResponse> listActivePromotions(Boolean featured, Integer tripId) {
        LocalDateTime now = LocalDateTime.now();
        List<Promotion> promotions;

        if (Boolean.TRUE.equals(featured)) {
            promotions = promotionRepository.findFeatured(now);
        } else if (tripId != null) {
            Trip trip = tripRepository.findById(tripId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến xe với id " + tripId));
            Integer companyId = trip.getCompany() != null ? trip.getCompany().getId() : null;
            Integer routeId = trip.getRoute() != null ? trip.getRoute().getId() : null;
            promotions = promotionRepository.findApplicable(companyId, routeId, now);
        } else {
            promotions = promotionRepository.findAllActiveUsable(now);
        }

        return promotions.stream().map(PromotionResponse::from).toList();
    }

    /** Kiểm tra mã khuyến mãi và tính số tiền giảm. */
    @Transactional(readOnly = true)
    public PromotionValidateResponse validatePromotion(PromotionValidateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Trip trip = tripRepository.findById(request.tripId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến xe với id " + request.tripId()));

        List<TripSeat> seats = tripSeatRepository.findAllById(request.seatIds());
        BigDecimal subtotal = BigDecimal.ZERO;
        if (!seats.isEmpty()) {
            for (TripSeat seat : seats) {
                subtotal = subtotal.add(seat.effectivePrice(trip.getBasePrice()));
            }
        } else {
            subtotal = trip.getBasePrice().multiply(BigDecimal.valueOf(request.seatIds().size()));
        }

        Promotion promotion = validateAndGetPromotion(request.code(), trip, subtotal, now);
        BigDecimal discount = promotion.discountFor(subtotal);
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO);

        return new PromotionValidateResponse(promotion.getCode(), subtotal, discount, total);
    }

    /**
     * Kiểm tra tính hợp lệ của mã khuyến mãi với chuyến xe và giá trị đơn hàng cụ thể.
     * Ném lỗi nếu mã không hợp lệ.
     */
    @Transactional(readOnly = true)
    public Promotion validateAndGetPromotion(String code, Trip trip, BigDecimal subtotal, LocalDateTime now) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Mã giảm giá không được bỏ trống");
        }

        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BadRequestException("Mã giảm giá không tồn tại"));

        if (!promotion.isActive()) {
            throw new BadRequestException("Mã giảm giá không còn hiệu lực");
        }
        if (now.isBefore(promotion.getStartsAt())) {
            throw new BadRequestException("Mã giảm giá chưa đến thời gian áp dụng");
        }
        if (!now.isBefore(promotion.getEndsAt())) {
            throw new BadRequestException("Mã giảm giá đã hết hạn");
        }
        if (promotion.getUsageLimit() != null && promotion.getUsedCount() >= promotion.getUsageLimit()) {
            throw new BadRequestException("Mã giảm giá đã hết lượt sử dụng");
        }

        if (promotion.getCompany() != null) {
            if (trip.getCompany() == null || !promotion.getCompany().getId().equals(trip.getCompany().getId())) {
                String companyName = promotion.getCompany().getName();
                throw new BadRequestException("Mã chỉ áp dụng cho nhà xe " + companyName);
            }
        }

        if (promotion.getRoute() != null) {
            if (trip.getRoute() == null || !promotion.getRoute().getId().equals(trip.getRoute().getId())) {
                String routeName = promotion.getRoute().getName();
                throw new BadRequestException("Mã chỉ áp dụng cho tuyến " + routeName);
            }
        }

        if (subtotal != null && promotion.getMinOrderValue() != null && subtotal.compareTo(promotion.getMinOrderValue()) < 0) {
            throw new BadRequestException("Đơn tối thiểu " + promotion.getMinOrderValue() + "đ để dùng mã này");
        }

        return promotion;
    }

    /** Danh sách mã cho trang quản trị Admin, sắp xếp id giảm dần. */
    @Transactional(readOnly = true)
    public List<PromotionResponse> listForAdmin() {
        return promotionRepository.findAllForAdmin().stream()
                .map(PromotionResponse::from)
                .toList();
    }

    /** Admin tạo mã mới. */
    @Transactional
    public PromotionResponse createPromotion(PromotionRequest request) {
        String code = request.code().trim().toUpperCase();
        if (promotionRepository.existsByCodeIgnoreCaseAndIdNot(code, null)) {
            throw new ConflictException("Mã '" + code + "' đã tồn tại");
        }
        validatePromotionRequest(request);

        Promotion p = new Promotion();
        p.setCode(code);
        applyRequestToEntity(request, p);
        p.setUsedCount(0);
        p = promotionRepository.save(p);

        return PromotionResponse.from(p);
    }

    /** Admin cập nhật mã. */
    @Transactional
    public PromotionResponse updatePromotion(Integer id, PromotionRequest request) {
        Promotion p = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã khuyến mãi với id " + id));

        String code = request.code().trim().toUpperCase();
        if (promotionRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("Mã '" + code + "' đã tồn tại");
        }
        validatePromotionRequest(request);

        p.setCode(code);
        applyRequestToEntity(request, p);
        p = promotionRepository.save(p);

        return PromotionResponse.from(p);
    }

    /** Admin xoá mã (chỉ xoá được nếu chưa có lượt sử dụng). */
    @Transactional
    public void deletePromotion(Integer id) {
        Promotion p = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã khuyến mãi với id " + id));

        if (p.getUsedCount() != null && p.getUsedCount() > 0) {
            throw new ConflictException("Mã đã được dùng, hãy tắt thay vì xoá");
        }

        promotionRepository.delete(p);
    }

    private void validatePromotionRequest(PromotionRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BadRequestException("Thời gian kết thúc phải sau bắt đầu");
        }
        if (request.discountType() == DiscountType.PERCENT) {
            if (request.discountValue().compareTo(BigDecimal.ZERO) <= 0 || request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BadRequestException("Giảm theo % phải từ 1 đến 100");
            }
        } else if (request.discountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Số tiền giảm phải lớn hơn 0");
        }
    }

    private void applyRequestToEntity(PromotionRequest request, Promotion p) {
        p.setTitle(request.title().trim());
        p.setDescription(request.description() != null ? request.description().trim() : null);
        p.setBannerUrl(request.bannerUrl() != null ? request.bannerUrl().trim() : null);
        p.setDiscountType(request.discountType());
        p.setDiscountValue(request.discountValue());
        p.setMaxDiscount(request.maxDiscount());
        p.setMinOrderValue(request.minOrderValue() != null ? request.minOrderValue() : BigDecimal.ZERO);

        if (request.companyId() != null) {
            BusCompany company = busCompanyRepository.findById(request.companyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà xe với id " + request.companyId()));
            p.setCompany(company);
        } else {
            p.setCompany(null);
        }

        if (request.routeId() != null) {
            Route route = routeRepository.findById(request.routeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tuyến xe với id " + request.routeId()));
            p.setRoute(route);
        } else {
            p.setRoute(null);
        }

        p.setStartsAt(request.startsAt());
        p.setEndsAt(request.endsAt());
        p.setUsageLimit(request.usageLimit());
        p.setFeatured(Boolean.TRUE.equals(request.featured()));
        p.setActive(request.active() == null || request.active());
    }
}


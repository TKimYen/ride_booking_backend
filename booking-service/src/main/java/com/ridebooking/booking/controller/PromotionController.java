package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.PromotionResponse;
import com.ridebooking.booking.dto.PromotionValidateRequest;
import com.ridebooking.booking.dto.PromotionValidateResponse;
import com.ridebooking.booking.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/promotions")
@Tag(name = "Khuyến mãi", description = "Xem và kiểm tra mã khuyến mãi")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách mã khuyến mãi đang dùng được")
    public List<PromotionResponse> list(
            @Parameter(description = "true để lấy danh sách ưu đãi nổi bật ở trang chủ")
            @RequestParam(value = "featured", required = false) Boolean featured,
            @Parameter(description = "id chuyến xe để lấy mã áp dụng được cho chuyến")
            @RequestParam(value = "tripId", required = false) Integer tripId) {
        return promotionService.listActivePromotions(featured, tripId);
    }

    @PostMapping("/validate")
    @Operation(summary = "Kiểm tra mã khuyến mãi và tính tiền giảm")
    public PromotionValidateResponse validate(@Valid @RequestBody PromotionValidateRequest request) {
        return promotionService.validatePromotion(request);
    }
}


package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.PromotionRequest;
import com.ridebooking.booking.dto.PromotionResponse;
import com.ridebooking.booking.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/promotions")
@Tag(name = "Admin - Khuyến mãi", description = "Quản lý mã khuyến mãi dành cho Admin")
public class AdminPromotionController {

    private final PromotionService promotionService;

    public AdminPromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping
    @Operation(summary = "Danh sách mã khuyến mãi, mới nhất xếp trước")
    public List<PromotionResponse> list() {
        return promotionService.listForAdmin();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo mã khuyến mãi mới")
    public PromotionResponse create(@Valid @RequestBody PromotionRequest request) {
        return promotionService.createPromotion(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin mã khuyến mãi")
    public PromotionResponse update(@PathVariable("id") Integer id, @Valid @RequestBody PromotionRequest request) {
        return promotionService.updatePromotion(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xoá mã khuyến mãi (chỉ xoá được nếu chưa có lượt sử dụng)")
    public Map<String, Object> delete(@PathVariable("id") Integer id) {
        promotionService.deletePromotion(id);
        return Map.of("ok", true);
    }
}


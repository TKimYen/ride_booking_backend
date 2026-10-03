package com.ridebooking.booking.controller;

import com.ridebooking.booking.dto.BusCompanyResponse;
import com.ridebooking.booking.service.BusCompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Nhóm Guest — API đọc nhà xe. Thêm/sửa/xoá nằm ở {@code AdminBusCompanyController}. */
@RestController
@RequestMapping("/api/bus-companies")
@Tag(name = "1. Catalog - Nhà xe", description = "Nhà xe, dùng cho bộ lọc \"theo nhà xe\"")
public class BusCompanyController {

    private final BusCompanyService busCompanyService;

    public BusCompanyController(BusCompanyService busCompanyService) {
        this.busCompanyService = busCompanyService;
    }

    @Operation(summary = "Danh sách / tìm nhà xe")
    @GetMapping
    public List<BusCompanyResponse> list(
            @Parameter(description = "Từ khoá tên nhà xe", example = "Sapa")
            @RequestParam(required = false) String keyword) {
        return busCompanyService.suggest(keyword);
    }

    @Operation(summary = "Chi tiết một nhà xe")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhà xe")})
    @GetMapping("/{id}")
    public BusCompanyResponse getById(@PathVariable Integer id) {
        return busCompanyService.getById(id);
    }
}

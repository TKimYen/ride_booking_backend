package com.ridebooking.booking.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Wrapper phân trang cho danh sách người dùng (trang Quản lý của Admin). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }

    /**
     * Thông tin phân trang lấy từ {@code page}, nội dung lấy từ {@code content}.
     *
     * <p>Hai kiểu tham số tách biệt ({@code S} cho phần tử trang, {@code T} cho
     * phần tử trả về) vì nội dung thường được bọc thêm thông tin. Ví dụ trang
     * chứa {@code Trip} nhưng ta gắn số ghế trống rồi đổi thành
     * {@code TripSearchItemResponse}. Gọi {@code T = S} vẫn chạy đúng nên dùng
     * được cho trường hợp không bọc.
     */
    public static <S, T> PageResponse<T> from(Page<S> page, List<T> content) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
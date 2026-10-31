package com.tung.warehouse.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Khuôn trả về cho mọi API danh sách có phân trang (dùng từ Ngày 4).
 * record là kiểu của Java 17: tự có constructor, getter, equals, toString.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}

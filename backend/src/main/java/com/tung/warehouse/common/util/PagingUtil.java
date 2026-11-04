package com.tung.warehouse.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Tạo Pageable từ số trang và kích thước trang, có giới hạn để client không xin 1 triệu dòng một lần. */
public final class PagingUtil {

    private static final int MAX_SIZE = 100;

    private PagingUtil() {
    }

    /** page bắt đầu từ 0. Mặc định sắp xếp bản ghi mới nhất lên đầu (id giảm dần). */
    public static Pageable of(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));
    }
}

package com.tung.warehouse.inventory.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Công cụ nhỏ để ghép câu WHERE động: gọi .and(...) cho từng điều kiện cần thêm.
 * Giống cách ghép Specification: điều kiện nào không cần thì không gọi.
 * An toàn trước SQL injection vì giá trị người dùng luôn đi qua dấu ? (tham số), không bao giờ nối thẳng vào chuỗi SQL.
 */
final class SqlWhere {

    private final StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
    private final List<Object> args = new ArrayList<>();

    /** Thêm một điều kiện. condition chứa dấu ? và values là các giá trị tương ứng, theo đúng thứ tự. */
    SqlWhere and(String condition, Object... values) {
        sql.append(" AND ").append(condition);
        args.addAll(Arrays.asList(values));
        return this;
    }

    String sql() {
        return sql.toString();
    }

    List<Object> args() {
        return args;
    }

    /** Tạo chuỗi "?, ?, ?" với n dấu hỏi, dùng cho điều kiện IN (...). */
    static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }
}

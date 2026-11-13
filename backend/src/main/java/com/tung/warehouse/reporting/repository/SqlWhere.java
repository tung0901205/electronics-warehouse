package com.tung.warehouse.reporting.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Ghép câu WHERE động: gọi .and(...) cho từng điều kiện cần thêm. Giá trị luôn đi qua dấu ? (tham số) nên an toàn trước
 * SQL injection. (Bản riêng của module reporting, giống bản trong module inventory.)
 */
final class SqlWhere {

    private final StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
    private final List<Object> args = new ArrayList<>();

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

    static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }
}

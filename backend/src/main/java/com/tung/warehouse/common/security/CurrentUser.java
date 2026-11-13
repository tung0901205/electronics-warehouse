package com.tung.warehouse.common.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Tiện ích lấy "ai đang đăng nhập" ở bất kỳ đâu trong code.
 * SecurityContextHolder lưu thông tin người dùng của request hiện tại (mỗi request một bản riêng).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Id người dùng lấy từ claim "sub" của JWT. Rỗng nếu chưa đăng nhập. */
    public static Optional<Long> id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return Optional.of(Long.valueOf(jwtAuthentication.getToken().getSubject()));
        }
        return Optional.empty();
    }

    /** [MỚI Ngày 7] Người đang đăng nhập có vai trò ADMIN không? Admin không bị giới hạn kho. */
    public static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /** [MỚI Giai đoạn 6] Người đang đăng nhập có phải Quản lý kho hoặc Admin không? (dùng cho các việc cần Quản lý duyệt) */
    public static boolean isManagerOrAbove() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_WAREHOUSE_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * [MỚI Ngày 7] Danh sách id kho được gán, đọc từ claim "warehouses" trong JWT.
     * Lưu ý: danh sách này được chốt lúc đăng nhập hoặc làm mới token, nên thay đổi phân quyền kho
     * có hiệu lực sau lần làm mới token kế tiếp (tối đa 15 phút).
     */
    public static Set<Long> warehouseIds() {
        Set<Long> result = new HashSet<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            Object claim = jwt.getClaim("warehouses");
            if (claim instanceof Collection<?> values) {
                for (Object value : values) {
                    if (value instanceof Number number) {
                        result.add(number.longValue());
                    }
                }
            }
        }
        return result;
    }
}

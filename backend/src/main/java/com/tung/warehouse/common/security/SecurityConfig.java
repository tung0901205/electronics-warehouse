package com.tung.warehouse.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity // cho phép dùng @PreAuthorize trên controller/service
public class SecurityConfig {

    /** Các đường dẫn không cần đăng nhập. */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/auth/login",
            "/api/auth/refresh"
    };

    /** BCrypt: băm mật khẩu một chiều, mỗi lần băm ra chuỗi khác nhau (có salt ngẫu nhiên). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Quan hệ kế thừa tác nhân (mục 2.2 tài liệu đặc tả).
     * "A implies B" nghĩa là A có mọi quyền của B.
     * Nhờ vậy @PreAuthorize("hasRole('VIEWER')") tự cho phép cả ADMIN, MANAGER, STAFF, SALES.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("WAREHOUSE_MANAGER")
                .role("WAREHOUSE_MANAGER").implies("WAREHOUSE_STAFF")
                .role("WAREHOUSE_STAFF").implies("VIEWER")
                .role("SALES").implies("VIEWER")
                .build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter) throws Exception {
        http
                // API dùng token trong header, không dùng cookie nên không cần CSRF và không cần session
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                // Bật chế độ "resource server": Spring tự đọc header "Authorization: Bearer <jwt>", kiểm tra chữ ký và hạn
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeForbidden))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeForbidden));
        return http.build();
    }

    /** Chưa đăng nhập, thiếu token hoặc token sai/hết hạn: trả 401 dạng JSON. */
    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response,
                                   AuthenticationException ex) throws IOException {
        writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                "Chưa đăng nhập hoặc token không hợp lệ hoặc đã hết hạn");
    }

    /** Đã đăng nhập nhưng không đủ quyền: trả 403 dạng JSON. */
    private void writeForbidden(HttpServletRequest request, HttpServletResponse response,
                                AccessDeniedException ex) throws IOException {
        writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                "Bạn không có quyền thực hiện chức năng này");
    }

    private void writeProblem(HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status
                + ",\"title\":\"" + code + "\""
                + ",\"detail\":\"" + detail + "\""
                + ",\"code\":\"" + code + "\"}");
    }
}

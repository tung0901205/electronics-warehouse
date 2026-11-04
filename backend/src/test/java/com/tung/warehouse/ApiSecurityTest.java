package com.tung.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.tung.warehouse.support.AbstractApiTest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Kiểm tra các đường dẫn công khai, lỗi 401/403 và quan hệ kế thừa vai trò. */
class ApiSecurityTest extends AbstractApiTest {

    @Test
    @DisplayName("Health và tài liệu OpenAPI mở công khai")
    void publicEndpoints() throws Exception {
        HttpResponse<String> health = call("GET", "/actuator/health", null, null);
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("UP");

        HttpResponse<String> docs = call("GET", "/v3/api-docs", null, null);
        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body()).contains("/api/auth/login");
    }

    @Test
    @DisplayName("Không có token thì bị 401 dạng JSON")
    void noTokenIs401() throws Exception {
        HttpResponse<String> response = call("GET", "/api/auth/me", null, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHORIZED");
    }

    @Test
    @DisplayName("Token giả hoặc sai chữ ký thì bị 401")
    void garbageTokenIs401() throws Exception {
        HttpResponse<String> response = call("GET", "/api/auth/me", null, "day-khong-phai-jwt");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Chỉ Admin xem được danh sách vai trò, Viewer bị 403")
    void rolesOnlyForAdmin() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String viewer = loginAndGetAccessToken("viewer");

        HttpResponse<String> asAdmin = call("GET", "/api/roles", null, admin);
        assertThat(asAdmin.statusCode()).isEqualTo(200);
        assertThat(asAdmin.body()).contains("WAREHOUSE_MANAGER");

        HttpResponse<String> asViewer = call("GET", "/api/roles", null, viewer);
        assertThat(asViewer.statusCode()).isEqualTo(403);
        assertThat(asViewer.body()).contains("FORBIDDEN");
    }

    @Test
    @DisplayName("Id không tồn tại trả 404 theo định dạng ProblemDetail")
    void unknownRoleIs404() throws Exception {
        String admin = loginAndGetAccessToken("admin");

        HttpResponse<String> response = call("GET", "/api/roles/999999", null, admin);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("NOT_FOUND");
    }

    /**
     * Ma trận kế thừa vai trò, đúng với mục 2.3 của tài liệu đặc tả.
     * Mỗi dòng: tài khoản đăng nhập, mức quyền của endpoint, kết quả mong đợi (200 được vào, 403 bị chặn).
     */
    @ParameterizedTest(name = "{0} gọi endpoint mức {1} thì nhận {2}")
    @CsvSource({
            "viewer,viewer,200",  "viewer,staff,403",  "viewer,manager,403",  "viewer,admin,403",
            "sales,viewer,200",   "sales,staff,403",   "sales,manager,403",   "sales,admin,403",
            "staff,viewer,200",   "staff,staff,200",   "staff,manager,403",   "staff,admin,403",
            "manager,viewer,200", "manager,staff,200", "manager,manager,200", "manager,admin,403",
            "admin,viewer,200",   "admin,staff,200",   "admin,manager,200",   "admin,admin,200"
    })
    @DisplayName("Quan hệ kế thừa vai trò")
    void roleHierarchy(String username, String level, int expectedStatus) throws Exception {
        String token = loginAndGetAccessToken(username);

        HttpResponse<String> response = call("GET", "/test-probe/" + level, null, token);

        assertThat(response.statusCode()).isEqualTo(expectedStatus);
    }
}

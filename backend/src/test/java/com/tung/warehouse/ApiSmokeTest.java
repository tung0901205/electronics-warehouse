package com.tung.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test "khói": chạy ứng dụng thật trên một cổng ngẫu nhiên rồi gọi HTTP thật,
 * để chắc chắn cấu hình bảo mật, Swagger và xử lý lỗi hoạt động đúng với nhau.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiSmokeTest {

    @Value("${local.server.port}")
    private int port;

    @Value("${spring.security.user.name}")
    private String devUser;

    @Value("${spring.security.user.password}")
    private String devPassword;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, boolean withLogin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (withLogin) {
            String token = Base64.getEncoder()
                    .encodeToString((devUser + ":" + devPassword).getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Health endpoint mở công khai và báo UP")
    void healthIsPublic() throws Exception {
        HttpResponse<String> response = get("/actuator/health", false);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    @Test
    @DisplayName("Tài liệu OpenAPI mở công khai và có endpoint /api/roles")
    void apiDocsListRolesEndpoint() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs", false);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("/api/roles");
    }

    @Test
    @DisplayName("Chưa đăng nhập thì /api/roles trả 401")
    void rolesRequireLogin() throws Exception {
        assertThat(get("/api/roles", false).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Đăng nhập rồi thì /api/roles trả danh sách vai trò")
    void rolesWithLogin() throws Exception {
        HttpResponse<String> response = get("/api/roles", true);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("ADMIN").contains("WAREHOUSE_MANAGER");
    }

    @Test
    @DisplayName("Id không tồn tại trả 404 theo định dạng ProblemDetail có code NOT_FOUND")
    void unknownRoleReturnsProblemDetail() throws Exception {
        HttpResponse<String> response = get("/api/roles/999999", true);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("NOT_FOUND");
    }
}

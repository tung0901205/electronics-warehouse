package com.tung.warehouse.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.tung.warehouse.support.AbstractApiTest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test API quản lý người dùng (UC1.4, UC1.5). */
class UserApiTest extends AbstractApiTest {

    private static final String PASSWORD = "Test@12345";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Mọi user test đều có username bắt đầu bằng "tst" nên dọn được mà không đụng 5 tài khoản demo. */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from refresh_tokens where user_id in (select id from users where username like 'tst%')");
        jdbcTemplate.update("delete from user_roles where user_id in (select id from users where username like 'tst%')");
        jdbcTemplate.update("delete from users where username like 'tst%'");
    }

    private static String random() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private HttpResponse<String> createUser(String token, String username, String rolesJson) throws Exception {
        String body = """
                {"username":"%s","email":"%s@test.local","fullName":"Người test","phone":"0900000000",
                 "password":"%s","roleNames":%s}
                """.formatted(username, username, PASSWORD, rolesJson);
        return call("POST", "/api/users", body, token);
    }

    @Test
    @DisplayName("Admin tạo user: trả 201, không lộ mật khẩu, user mới đăng nhập được")
    void adminCreatesUser() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String username = "tst" + random();

        HttpResponse<String> response = createUser(admin, username, "[\"VIEWER\"]");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains(username).contains("VIEWER").doesNotContain("password");
        assertThat(login(username, PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Trùng username hoặc email thì 409; dữ liệu sai thì 400 kèm lỗi từng trường")
    void conflictAndValidation() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String username = "tst" + random();
        assertThat(createUser(admin, username, "[\"VIEWER\"]").statusCode()).isEqualTo(201);

        HttpResponse<String> duplicate = createUser(admin, username, "[\"VIEWER\"]");
        assertThat(duplicate.statusCode()).isEqualTo(409);

        String bad = "{\"username\":\"a b\",\"email\":\"khong-phai-email\",\"fullName\":\"X\",\"password\":\"123\",\"roleNames\":[]}";
        HttpResponse<String> invalid = call("POST", "/api/users", bad, admin);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body()).contains("VALIDATION_ERROR").contains("username").contains("email")
                .contains("password").contains("roleNames");
    }

    @Test
    @DisplayName("Chỉ Admin được quản lý người dùng, Quản lý kho bị 403")
    void onlyAdmin() throws Exception {
        String manager = loginAndGetAccessToken("manager");

        assertThat(call("GET", "/api/users", null, manager).statusCode()).isEqualTo(403);
        assertThat(createUser(manager, "tst" + random(), "[\"VIEWER\"]").statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Tìm kiếm kết hợp 3 điều kiện: từ khóa, vai trò, trạng thái")
    void searchWithThreeFilters() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String r = random();
        createUser(admin, "tst" + r + "a", "[\"WAREHOUSE_STAFF\"]");
        createUser(admin, "tst" + r + "b", "[\"VIEWER\"]");

        String base = "/api/users?keyword=tst" + r;
        assertThat(number(call("GET", base, null, admin).body(), "totalElements")).isEqualTo(2);
        assertThat(number(call("GET", base + "&role=WAREHOUSE_STAFF", null, admin).body(), "totalElements")).isEqualTo(1);
        assertThat(number(call("GET", base + "&role=WAREHOUSE_STAFF&enabled=true", null, admin).body(), "totalElements")).isEqualTo(1);
        assertThat(number(call("GET", base + "&role=WAREHOUSE_STAFF&enabled=false", null, admin).body(), "totalElements")).isZero();

        HttpResponse<String> paged = call("GET", base + "&page=0&size=1", null, admin);
        assertThat(number(paged.body(), "totalPages")).isEqualTo(2);
    }

    @Test
    @DisplayName("Gán vai trò: thay toàn bộ vai trò cũ; vai trò không tồn tại trả 404")
    void assignRoles() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String username = "tst" + random();
        long id = number(createUser(admin, username, "[\"VIEWER\"]").body(), "id");

        HttpResponse<String> changed = call("PUT", "/api/users/" + id + "/roles",
                "{\"roleNames\":[\"WAREHOUSE_STAFF\",\"SALES\"]}", admin);
        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(changed.body()).contains("WAREHOUSE_STAFF").contains("SALES").doesNotContain("\"VIEWER\"");

        HttpResponse<String> unknown = call("PUT", "/api/users/" + id + "/roles", "{\"roleNames\":[\"KHONG_CO\"]}", admin);
        assertThat(unknown.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Vô hiệu hóa thì không đăng nhập được (403), kích hoạt lại thì đăng nhập được")
    void disableAndEnable() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String username = "tst" + random();
        long id = number(createUser(admin, username, "[\"VIEWER\"]").body(), "id");

        assertThat(call("PATCH", "/api/users/" + id + "/disable", null, admin).statusCode()).isEqualTo(200);
        HttpResponse<String> blocked = login(username, PASSWORD);
        assertThat(blocked.statusCode()).isEqualTo(403);
        assertThat(blocked.body()).contains("ACCOUNT_DISABLED");

        assertThat(call("PATCH", "/api/users/" + id + "/enable", null, admin).statusCode()).isEqualTo(200);
        assertThat(login(username, PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Admin không được tự vô hiệu hóa hoặc tự gỡ vai trò ADMIN của chính mình")
    void adminCannotLockSelfOut() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        long adminId = number(call("GET", "/api/auth/me", null, admin).body(), "id");

        assertThat(call("PATCH", "/api/users/" + adminId + "/disable", null, admin).statusCode()).isEqualTo(409);
        assertThat(call("PUT", "/api/users/" + adminId + "/roles", "{\"roleNames\":[\"VIEWER\"]}", admin).statusCode())
                .isEqualTo(409);
    }
}

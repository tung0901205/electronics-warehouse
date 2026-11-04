package com.tung.warehouse.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.tung.warehouse.identity.entity.User;
import com.tung.warehouse.identity.repository.RoleRepository;
import com.tung.warehouse.identity.repository.UserRepository;
import com.tung.warehouse.support.AbstractApiTest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Test luồng đăng nhập, làm mới phiên, đăng xuất, đổi mật khẩu bằng HTTP thật. */
class AuthApiTest extends AbstractApiTest {

    private static final String OLD_PASSWORD = "Old@12345";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Id các user tạo riêng cho test, để dọn sau mỗi test (không đụng tới 5 tài khoản demo). */
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long id : createdUserIds) {
            jdbcTemplate.update("delete from refresh_tokens where user_id = ?", id);
            jdbcTemplate.update("delete from user_roles where user_id = ?", id);
            jdbcTemplate.update("delete from users where id = ?", id);
        }
        createdUserIds.clear();
    }

    private String createTestUser(boolean enabled) {
        String username = "t" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User(username, username + "@test.local", passwordEncoder.encode(OLD_PASSWORD), "Người dùng test");
        user.setEnabled(enabled);
        user.addRole(roleRepository.findByName("VIEWER").orElseThrow());
        createdUserIds.add(userRepository.saveAndFlush(user).getId());
        return username;
    }

    @Test
    @DisplayName("Đăng nhập đúng trả access token (JWT 3 phần) và refresh token")
    void loginSuccess() throws Exception {
        HttpResponse<String> response = login("admin", demoPassword);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(field(response.body(), "accessToken").split("\\.")).hasSize(3);
        assertThat(field(response.body(), "refreshToken")).isNotBlank();
        assertThat(field(response.body(), "tokenType")).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("Sai mật khẩu và sai tên đăng nhập đều trả cùng một lỗi 401")
    void wrongCredentials() throws Exception {
        HttpResponse<String> wrongPassword = login("admin", "sai-mat-khau");
        HttpResponse<String> wrongUser = login("khong-ton-tai", "bat-ky");

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(wrongUser.statusCode()).isEqualTo(401);
        assertThat(wrongPassword.body()).contains("INVALID_CREDENTIALS");
        assertThat(wrongUser.body()).contains("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("Tài khoản bị vô hiệu hóa không đăng nhập được: 403")
    void disabledAccount() throws Exception {
        String username = createTestUser(false);

        HttpResponse<String> response = login(username, OLD_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("ACCOUNT_DISABLED");
    }

    @Test
    @DisplayName("Bỏ trống tên đăng nhập hoặc mật khẩu trả 400 kèm lỗi từng trường")
    void blankFields() throws Exception {
        HttpResponse<String> response = call("POST", "/api/auth/login", "{\"username\":\"\",\"password\":\"\"}", null);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("VALIDATION_ERROR").contains("username").contains("password");
    }

    @Test
    @DisplayName("/me trả đúng người đang đăng nhập và vai trò")
    void meReturnsCurrentUser() throws Exception {
        String token = loginAndGetAccessToken("manager");

        HttpResponse<String> response = call("GET", "/api/auth/me", null, token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("manager").contains("WAREHOUSE_MANAGER");
        assertThat(response.body()).doesNotContain("password");
    }

    @Test
    @DisplayName("Làm mới phiên: token cũ chỉ dùng được một lần, dùng lại thì thu hồi toàn bộ phiên")
    void refreshRotationAndReuseDetection() throws Exception {
        String username = createTestUser(true);
        HttpResponse<String> first = login(username, OLD_PASSWORD);
        String refresh1 = field(first.body(), "refreshToken");

        // Lần 1: đổi refresh1 lấy refresh2
        HttpResponse<String> second = call("POST", "/api/auth/refresh", "{\"refreshToken\":\"" + refresh1 + "\"}", null);
        assertThat(second.statusCode()).isEqualTo(200);
        String refresh2 = field(second.body(), "refreshToken");
        assertThat(refresh2).isNotEqualTo(refresh1);

        // Dùng lại refresh1 (đã bị thu hồi): bị từ chối
        HttpResponse<String> reuse = call("POST", "/api/auth/refresh", "{\"refreshToken\":\"" + refresh1 + "\"}", null);
        assertThat(reuse.statusCode()).isEqualTo(401);
        assertThat(reuse.body()).contains("INVALID_REFRESH_TOKEN");

        // Vì phát hiện dùng lại, refresh2 cũng bị thu hồi
        HttpResponse<String> afterReuse = call("POST", "/api/auth/refresh", "{\"refreshToken\":\"" + refresh2 + "\"}", null);
        assertThat(afterReuse.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Đăng xuất thu hồi refresh token")
    void logoutRevokesRefreshToken() throws Exception {
        String username = createTestUser(true);
        HttpResponse<String> loginResponse = login(username, OLD_PASSWORD);
        String accessToken = field(loginResponse.body(), "accessToken");
        String refreshToken = field(loginResponse.body(), "refreshToken");
        String body = "{\"refreshToken\":\"" + refreshToken + "\"}";

        HttpResponse<String> logout = call("POST", "/api/auth/logout", body, accessToken);
        assertThat(logout.statusCode()).isEqualTo(204);

        HttpResponse<String> refresh = call("POST", "/api/auth/refresh", body, null);
        assertThat(refresh.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Đổi mật khẩu: mật khẩu cũ hết tác dụng, refresh token cũ bị thu hồi")
    void changePassword() throws Exception {
        String username = createTestUser(true);
        HttpResponse<String> loginResponse = login(username, OLD_PASSWORD);
        String accessToken = field(loginResponse.body(), "accessToken");
        String refreshToken = field(loginResponse.body(), "refreshToken");

        // Nhập sai mật khẩu cũ
        HttpResponse<String> wrongOld = call("POST", "/api/auth/change-password",
                "{\"oldPassword\":\"sai\",\"newPassword\":\"New@123456\"}", accessToken);
        assertThat(wrongOld.statusCode()).isEqualTo(400);
        assertThat(wrongOld.body()).contains("WRONG_OLD_PASSWORD");

        // Mật khẩu mới quá ngắn
        HttpResponse<String> tooShort = call("POST", "/api/auth/change-password",
                "{\"oldPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"123\"}", accessToken);
        assertThat(tooShort.statusCode()).isEqualTo(400);
        assertThat(tooShort.body()).contains("VALIDATION_ERROR");

        // Đổi thành công
        HttpResponse<String> changed = call("POST", "/api/auth/change-password",
                "{\"oldPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"New@123456\"}", accessToken);
        assertThat(changed.statusCode()).isEqualTo(204);

        assertThat(login(username, OLD_PASSWORD).statusCode()).isEqualTo(401);   // mật khẩu cũ hết tác dụng
        assertThat(login(username, "New@123456").statusCode()).isEqualTo(200);   // mật khẩu mới dùng được

        HttpResponse<String> oldRefresh = call("POST", "/api/auth/refresh",
                "{\"refreshToken\":\"" + refreshToken + "\"}", null);
        assertThat(oldRefresh.statusCode()).isEqualTo(401);                      // phiên cũ bị đá ra
    }
}

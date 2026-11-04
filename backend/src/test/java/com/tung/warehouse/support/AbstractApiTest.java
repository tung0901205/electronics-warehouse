package com.tung.warehouse.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Lớp cha cho các test gọi API thật: chạy ứng dụng trên cổng ngẫu nhiên rồi gửi HTTP thật.
 * Các lớp test con chỉ cần kế thừa là dùng được các hàm gọi API ở đây.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractApiTest {

    @Value("${local.server.port}")
    protected int port;

    /** Mật khẩu của 5 tài khoản demo (admin, manager, staff, sales, viewer), cấu hình ở application.yml. */
    @Value("${app.bootstrap.password}")
    protected String demoPassword;

    private final HttpClient client = HttpClient.newHttpClient();

    /** Gọi API. jsonBody = null nếu không có body; accessToken = null nếu không đăng nhập. */
    protected HttpResponse<String> call(String method, String path, String jsonBody, String accessToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (jsonBody == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
            request.header("Content-Type", "application/json");
        }
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> login(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return call("POST", "/api/auth/login", body, null);
    }

    /** Đăng nhập một tài khoản demo và trả về access token. */
    protected String loginAndGetAccessToken(String demoUsername) throws Exception {
        HttpResponse<String> response = login(demoUsername, demoPassword);
        assertThat(response.statusCode()).isEqualTo(200);
        return field(response.body(), "accessToken");
    }

    /** Lấy giá trị chuỗi của một trường trong JSON phẳng. Đủ dùng cho test, khỏi cần thư viện JSON. */
    protected static String field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Không thấy trường " + name + " trong: " + json);
        }
        return matcher.group(1);
    }

    /** Lấy giá trị số nguyên của một trường trong JSON (ví dụ id, totalElements). Lấy lần xuất hiện đầu tiên. */
    protected static long number(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Không thấy trường số " + name + " trong: " + json);
        }
        return Long.parseLong(matcher.group(1));
    }
}

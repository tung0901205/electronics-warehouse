package com.tung.warehouse.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.tung.warehouse.support.AbstractApiTest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test kho, khu vực, vị trí và phạm vi kho của người dùng (UC2.4, UC1.5). */
class WarehouseApiTest extends AbstractApiTest {

    private static final String PASSWORD = "Test@12345";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Dữ liệu test: kho có mã TST..., người dùng có username tst... Dọn theo thứ tự khóa ngoại. */
    @AfterEach
    void cleanUp() {
        String testUsers = "(select id from users where username like 'tst%')";
        jdbcTemplate.update("delete from user_warehouse_access where user_id in " + testUsers);
        jdbcTemplate.update("delete from refresh_tokens where user_id in " + testUsers);
        jdbcTemplate.update("delete from user_roles where user_id in " + testUsers);
        jdbcTemplate.update("delete from users where username like 'tst%'");

        String testWarehouses = "(select id from warehouses where code like 'TST%')";
        jdbcTemplate.update("delete from user_warehouse_access where warehouse_id in " + testWarehouses);
        jdbcTemplate.update("delete from storage_locations where zone_id in "
                + "(select id from warehouse_zones where warehouse_id in " + testWarehouses + ")");
        jdbcTemplate.update("delete from warehouse_zones where warehouse_id in " + testWarehouses);
        jdbcTemplate.update("delete from warehouses where code like 'TST%'");
    }

    private static String random() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    // ---------- Hàm dựng dữ liệu ----------

    private HttpResponse<String> createWarehouse(String admin, String code) throws Exception {
        return call("POST", "/api/warehouses",
                "{\"code\":\"" + code + "\",\"name\":\"Kho thử " + code + "\",\"address\":\"Đà Nẵng\"}", admin);
    }

    private long createWarehouseAndGetId(String admin, String code) throws Exception {
        HttpResponse<String> response = createWarehouse(admin, code);
        assertThat(response.statusCode()).isEqualTo(201);
        return number(response.body(), "id");
    }

    private long createUserWithRole(String admin, String username, String role) throws Exception {
        String body = """
                {"username":"%s","email":"%s@test.local","fullName":"Người test","password":"%s","roleNames":["%s"]}
                """.formatted(username, username, PASSWORD, role);
        HttpResponse<String> response = call("POST", "/api/users", body, admin);
        assertThat(response.statusCode()).isEqualTo(201);
        return number(response.body(), "id");
    }

    private String loginToken(String username) throws Exception {
        HttpResponse<String> response = login(username, PASSWORD);
        assertThat(response.statusCode()).isEqualTo(200);
        return field(response.body(), "accessToken");
    }

    /** Tìm id của khu vực theo mã trong JSON danh sách khu vực. */
    private static long zoneIdByCode(String json, String code) {
        Matcher matcher = Pattern.compile("\\{\"id\":(\\d+),\"warehouseId\":\\d+,\"code\":\"" + code + "\"").matcher(json);
        assertThat(matcher.find()).as("không thấy khu vực " + code + " trong " + json).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    /** Tìm id của vị trí theo mã trong JSON danh sách vị trí. */
    private static long locationIdByCode(String json, String code) {
        Matcher matcher = Pattern.compile("\\{\"id\":(\\d+),\"zoneId\":\\d+,\"zoneCode\":\"[^\"]*\",\"code\":\"" + code + "\"").matcher(json);
        assertThat(matcher.find()).as("không thấy vị trí " + code + " trong " + json).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    /** Đọc phần payload của JWT (ai cũng đọc được, chỉ không sửa được). */
    private static String jwtPayload(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    // ---------- Các test ----------

    @Test
    @DisplayName("Admin tạo kho: mã in hoa, tự có khu hệ thống kèm vị trí RECEIVING và DEFECTIVE")
    void adminCreatesWarehouseWithSystemLocations() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String code = "tst" + random();

        HttpResponse<String> created = createWarehouse(admin, code);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(field(created.body(), "code")).isEqualTo(code.toUpperCase());
        long id = number(created.body(), "id");

        HttpResponse<String> zones = call("GET", "/api/warehouses/" + id + "/zones", null, admin);
        assertThat(zones.body()).contains("\"code\":\"SYS\"");

        HttpResponse<String> locations = call("GET", "/api/warehouses/" + id + "/locations", null, admin);
        assertThat(locations.body()).contains("\"locationType\":\"RECEIVING\"").contains("\"locationType\":\"DEFECTIVE\"");

        assertThat(createWarehouse(admin, code).statusCode()).isEqualTo(409);
        assertThat(createWarehouse(loginAndGetAccessToken("manager"), "tst" + random()).statusCode()).isEqualTo(403);

        HttpResponse<String> invalid = call("POST", "/api/warehouses", "{\"code\":\"co dau cach\",\"name\":\"X\"}", admin);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body()).contains("VALIDATION_ERROR").contains("code");
    }

    @Test
    @DisplayName("Quy tắc khu vực và vị trí: trùng mã, mã dành riêng, khu hệ thống và vị trí hệ thống được bảo vệ")
    void zoneAndLocationRules() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        long warehouseId = createWarehouseAndGetId(admin, "tst" + random());

        // Khu vực
        HttpResponse<String> zoneA = call("POST", "/api/warehouses/" + warehouseId + "/zones", "{\"code\":\"a\",\"name\":\"Khu A\"}", admin);
        assertThat(zoneA.statusCode()).isEqualTo(201);
        long zoneAId = number(zoneA.body(), "id");
        assertThat(field(zoneA.body(), "code")).isEqualTo("A");
        assertThat(call("POST", "/api/warehouses/" + warehouseId + "/zones", "{\"code\":\"A\",\"name\":\"Lại A\"}", admin).statusCode()).isEqualTo(409);
        assertThat(call("POST", "/api/warehouses/" + warehouseId + "/zones", "{\"code\":\"sys\",\"name\":\"Giả\"}", admin).statusCode()).isEqualTo(409);

        // Vị trí
        HttpResponse<String> location = call("POST", "/api/zones/" + zoneAId + "/locations", "{\"code\":\"a1-t2\"}", admin);
        assertThat(location.statusCode()).isEqualTo(201);
        assertThat(field(location.body(), "code")).isEqualTo("A1-T2");
        long locationId = number(location.body(), "id");
        assertThat(call("POST", "/api/zones/" + zoneAId + "/locations", "{\"code\":\"A1-T2\"}", admin).statusCode()).isEqualTo(409);
        assertThat(call("POST", "/api/zones/" + zoneAId + "/locations", "{\"code\":\"receiving\"}", admin).statusCode()).isEqualTo(409);

        // Khu hệ thống và vị trí hệ thống không bị sửa, xóa
        String zonesJson = call("GET", "/api/warehouses/" + warehouseId + "/zones", null, admin).body();
        long systemZoneId = zoneIdByCode(zonesJson, "SYS");
        assertThat(call("POST", "/api/zones/" + systemZoneId + "/locations", "{\"code\":\"X1\"}", admin).statusCode()).isEqualTo(409);
        assertThat(call("DELETE", "/api/zones/" + systemZoneId, null, admin).statusCode()).isEqualTo(409);

        String locationsJson = call("GET", "/api/warehouses/" + warehouseId + "/locations", null, admin).body();
        long receivingId = locationIdByCode(locationsJson, "RECEIVING");
        assertThat(call("DELETE", "/api/locations/" + receivingId, null, admin).statusCode()).isEqualTo(409);
        assertThat(call("PATCH", "/api/locations/" + receivingId + "/deactivate", null, admin).statusCode()).isEqualTo(409);

        // Xóa: khu vực còn vị trí thì bị chặn; xóa vị trí rồi mới xóa được khu vực
        assertThat(call("DELETE", "/api/zones/" + zoneAId, null, admin).statusCode()).isEqualTo(409);
        assertThat(call("PATCH", "/api/locations/" + locationId + "/deactivate", null, admin).statusCode()).isEqualTo(200);
        assertThat(call("DELETE", "/api/locations/" + locationId, null, admin).statusCode()).isEqualTo(204);
        assertThat(call("DELETE", "/api/zones/" + zoneAId, null, admin).statusCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("Phạm vi kho: chưa được gán thì bị 403, gán xong và đăng nhập lại thì vào được; JWT chứa danh sách kho")
    void warehouseScope() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String code = "tst" + random();
        long warehouseId = createWarehouseAndGetId(admin, code);
        String username = "tst" + random().toLowerCase();
        long userId = createUserWithRole(admin, username, "WAREHOUSE_MANAGER");

        // Chưa được gán kho nào
        String tokenBefore = loginToken(username);
        HttpResponse<String> denied = call("GET", "/api/warehouses/" + warehouseId, null, tokenBefore);
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(denied.body()).contains("WAREHOUSE_ACCESS_DENIED");
        assertThat(call("GET", "/api/warehouses/" + warehouseId + "/zones", null, tokenBefore).statusCode()).isEqualTo(403);
        assertThat(call("GET", "/api/warehouses", null, tokenBefore).body()).doesNotContain(code.toUpperCase());

        // Admin gán kho
        HttpResponse<String> assigned = call("PUT", "/api/users/" + userId + "/warehouses",
                "{\"warehouseIds\":[" + warehouseId + "]}", admin);
        assertThat(assigned.statusCode()).isEqualTo(200);
        assertThat(assigned.body()).contains("\"warehouseIds\":[" + warehouseId + "]");

        // Token cũ chưa đổi (danh sách kho được chốt lúc đăng nhập)
        assertThat(call("GET", "/api/warehouses/" + warehouseId, null, tokenBefore).statusCode()).isEqualTo(403);

        // Đăng nhập lại thì có kho
        String tokenAfter = loginToken(username);
        assertThat(jwtPayload(tokenAfter)).contains("\"warehouses\":[" + warehouseId + "]");
        assertThat(call("GET", "/api/warehouses/" + warehouseId, null, tokenAfter).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/api/warehouses", null, tokenAfter).body()).contains(code.toUpperCase());
        assertThat(call("GET", "/api/warehouses/" + warehouseId + "/locations", null, tokenAfter).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/api/auth/me", null, tokenAfter).body()).contains("\"warehouseIds\":[" + warehouseId + "]");

        // Admin gỡ hết kho, đăng nhập lại thì bị chặn trở lại
        assertThat(call("PUT", "/api/users/" + userId + "/warehouses", "{\"warehouseIds\":[]}", admin).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/api/warehouses/" + warehouseId, null, loginToken(username)).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Admin thấy mọi kho; tài khoản demo chỉ thấy kho mẫu KHO-HN, không thấy kho khác")
    void visibleWarehouses() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String code = "tst" + random();
        createWarehouseAndGetId(admin, code);

        HttpResponse<String> adminList = call("GET", "/api/warehouses", null, admin);
        assertThat(adminList.body()).contains("KHO-HN").contains(code.toUpperCase());

        HttpResponse<String> viewerList = call("GET", "/api/warehouses", null, loginAndGetAccessToken("viewer"));
        assertThat(viewerList.statusCode()).isEqualTo(200);
        assertThat(viewerList.body()).contains("KHO-HN").doesNotContain(code.toUpperCase());
    }

    @Test
    @DisplayName("Gán kho không tồn tại thì 404, thiếu danh sách thì 400")
    void assignInvalidWarehouses() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        long userId = createUserWithRole(admin, "tst" + random().toLowerCase(), "VIEWER");

        assertThat(call("PUT", "/api/users/" + userId + "/warehouses", "{\"warehouseIds\":[999999999]}", admin).statusCode()).isEqualTo(404);

        HttpResponse<String> missing = call("PUT", "/api/users/" + userId + "/warehouses", "{}", admin);
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(missing.body()).contains("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("Sửa tên kho (mã giữ nguyên), ngừng và kích hoạt lại kho")
    void updateAndDeactivate() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String code = "tst" + random();
        long id = createWarehouseAndGetId(admin, code);

        HttpResponse<String> updated = call("PUT", "/api/warehouses/" + id,
                "{\"code\":\"MA-KHAC-BI-BO-QUA\",\"name\":\"Tên mới\",\"address\":\"TP HCM\"}", admin);
        assertThat(field(updated.body(), "name")).isEqualTo("Tên mới");
        assertThat(field(updated.body(), "code")).isEqualTo(code.toUpperCase());

        assertThat(field(call("PATCH", "/api/warehouses/" + id + "/deactivate", null, admin).body(), "status")).isEqualTo("INACTIVE");
        assertThat(field(call("PATCH", "/api/warehouses/" + id + "/activate", null, admin).body(), "status")).isEqualTo("ACTIVE");
    }
}

package com.tung.warehouse.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.tung.warehouse.support.AbstractApiTest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test danh mục, thương hiệu, đơn vị tính (UC2.2, UC2.5). */
class CatalogApiTest extends AbstractApiTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Mọi dữ liệu test đều bắt đầu bằng TST nên dọn được mà không đụng dữ liệu mẫu. */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from product_variants where sku like 'TST%'");
        jdbcTemplate.update("delete from products where name like 'TST%'");
        jdbcTemplate.update("delete from categories where name like 'TST%' and parent_id is not null"); // xóa con trước
        jdbcTemplate.update("delete from categories where name like 'TST%'");
        jdbcTemplate.update("delete from brands where name like 'TST%'");
        jdbcTemplate.update("delete from units where code like 'TST%'");
    }

    private static String random() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private long createCategory(String admin, String name, Long parentId) throws Exception {
        String parent = parentId == null ? "null" : String.valueOf(parentId);
        HttpResponse<String> response = call("POST", "/api/categories",
                "{\"name\":\"" + name + "\",\"parentId\":" + parent + "}", admin);
        assertThat(response.statusCode()).isEqualTo(201);
        return number(response.body(), "id");
    }

    @Test
    @DisplayName("Admin tạo cây danh mục: danh mục gốc và danh mục con")
    void adminCreatesCategoryTree() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String r = random();

        long rootId = createCategory(admin, "TST" + r, null);
        HttpResponse<String> child = call("POST", "/api/categories",
                "{\"name\":\"TSTcon" + r + "\",\"parentId\":" + rootId + "}", admin);

        assertThat(child.statusCode()).isEqualTo(201);
        assertThat(number(child.body(), "parentId")).isEqualTo(rootId);

        HttpResponse<String> list = call("GET", "/api/categories", null, admin);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("TST" + r).contains("TSTcon" + r);
    }

    @Test
    @DisplayName("Quản lý kho không được sửa danh mục (403), Người xem được đọc (200), chưa đăng nhập bị 401")
    void permissions() throws Exception {
        String body = "{\"name\":\"TST" + random() + "\",\"parentId\":null}";

        assertThat(call("POST", "/api/categories", body, loginAndGetAccessToken("manager")).statusCode()).isEqualTo(403);
        assertThat(call("GET", "/api/categories", null, loginAndGetAccessToken("viewer")).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/api/categories", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Trùng tên trong cùng danh mục cha thì 409, khác danh mục cha thì được")
    void duplicateNameRules() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String r = random();
        long root1 = createCategory(admin, "TSTa" + r, null);
        long root2 = createCategory(admin, "TSTb" + r, null);

        createCategory(admin, "TSTphukien" + r, root1);

        HttpResponse<String> sameParent = call("POST", "/api/categories",
                "{\"name\":\"tstphukien" + r + "\",\"parentId\":" + root1 + "}", admin); // khác hoa/thường vẫn là trùng
        assertThat(sameParent.statusCode()).isEqualTo(409);

        createCategory(admin, "TSTphukien" + r, root2); // cùng tên nhưng dưới danh mục cha khác: hợp lệ
    }

    @Test
    @DisplayName("Không cho chọn chính nó hoặc danh mục con làm danh mục cha (tránh vòng tròn)")
    void categoryCycleIsRejected() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String r = random();
        long a = createCategory(admin, "TSTa" + r, null);
        long b = createCategory(admin, "TSTb" + r, a);

        HttpResponse<String> parentIsChild = call("PUT", "/api/categories/" + a,
                "{\"name\":\"TSTa" + r + "\",\"parentId\":" + b + "}", admin);
        assertThat(parentIsChild.statusCode()).isEqualTo(409);

        HttpResponse<String> parentIsSelf = call("PUT", "/api/categories/" + a,
                "{\"name\":\"TSTa" + r + "\",\"parentId\":" + a + "}", admin);
        assertThat(parentIsSelf.statusCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("Xóa danh mục: còn danh mục con thì 409, xóa con trước rồi xóa cha thì 204")
    void deleteRules() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String r = random();
        long root = createCategory(admin, "TSTgoc" + r, null);
        long child = createCategory(admin, "TSTcon" + r, root);

        assertThat(call("DELETE", "/api/categories/" + root, null, admin).statusCode()).isEqualTo(409);
        assertThat(call("DELETE", "/api/categories/" + child, null, admin).statusCode()).isEqualTo(204);
        assertThat(call("DELETE", "/api/categories/" + root, null, admin).statusCode()).isEqualTo(204);
        assertThat(call("GET", "/api/categories/" + root, null, admin).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Ngừng hoạt động rồi kích hoạt lại danh mục")
    void deactivateAndActivateCategory() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        long id = createCategory(admin, "TST" + random(), null);

        HttpResponse<String> off = call("PATCH", "/api/categories/" + id + "/deactivate", null, admin);
        assertThat(field(off.body(), "status")).isEqualTo("INACTIVE");

        HttpResponse<String> on = call("PATCH", "/api/categories/" + id + "/activate", null, admin);
        assertThat(field(on.body(), "status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Thương hiệu: tạo, trùng tên (không phân biệt hoa thường) bị 409, đổi tên, xóa")
    void brandFlow() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String name = "TST" + random();

        HttpResponse<String> created = call("POST", "/api/brands", "{\"name\":\"" + name + "\"}", admin);
        assertThat(created.statusCode()).isEqualTo(201);
        long id = number(created.body(), "id");

        assertThat(call("POST", "/api/brands", "{\"name\":\"" + name.toLowerCase() + "\"}", admin).statusCode()).isEqualTo(409);
        assertThat(call("POST", "/api/brands", "{\"name\":\"" + name + "\"}", loginAndGetAccessToken("manager")).statusCode()).isEqualTo(403);

        HttpResponse<String> renamed = call("PUT", "/api/brands/" + id, "{\"name\":\"" + name + "X\"}", admin);
        assertThat(field(renamed.body(), "name")).isEqualTo(name + "X");

        assertThat(call("DELETE", "/api/brands/" + id, null, admin).statusCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("Đơn vị tính: mã tự in hoa, trùng mã bị 409, mã sai định dạng bị 400, sửa chỉ đổi tên")
    void unitFlow() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String code = "tst" + random().substring(0, 5);

        HttpResponse<String> created = call("POST", "/api/units", "{\"code\":\"" + code + "\",\"name\":\"Đơn vị thử\"}", admin);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(field(created.body(), "code")).isEqualTo(code.toUpperCase());
        long id = number(created.body(), "id");

        assertThat(call("POST", "/api/units", "{\"code\":\"" + code + "\",\"name\":\"Lần hai\"}", admin).statusCode()).isEqualTo(409);

        HttpResponse<String> invalid = call("POST", "/api/units", "{\"code\":\"co dau cach\",\"name\":\"X\"}", admin);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body()).contains("VALIDATION_ERROR").contains("code");

        HttpResponse<String> updated = call("PUT", "/api/units/" + id, "{\"code\":\"BO-QUA\",\"name\":\"Tên mới\"}", admin);
        assertThat(field(updated.body(), "name")).isEqualTo("Tên mới");
        assertThat(field(updated.body(), "code")).isEqualTo(code.toUpperCase());
    }
}

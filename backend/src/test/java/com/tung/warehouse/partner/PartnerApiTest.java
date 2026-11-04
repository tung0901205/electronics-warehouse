package com.tung.warehouse.partner;

import static org.assertj.core.api.Assertions.assertThat;

import com.tung.warehouse.support.AbstractApiTest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test API nhà cung cấp và khách hàng: phân quyền, validate, trùng mã, tìm kiếm 3 điều kiện. */
class PartnerApiTest extends AbstractApiTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Mọi dữ liệu test đều có mã bắt đầu bằng TST nên dọn được mà không đụng dữ liệu khác. */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from suppliers where code like 'TST%'");
        jdbcTemplate.update("delete from customers where code like 'TST%'");
    }

    private static String random() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private static String partnerJson(String code, String name, String phone) {
        return """
                {"code":"%s","name":"%s","contactName":"Anh Nam","phone":"%s","email":"lienhe@example.com",
                 "address":"Hà Nội","taxCode":"0101234567"}
                """.formatted(code, name, phone);
    }

    @Test
    @DisplayName("Quản lý kho tạo được nhà cung cấp, mã được chuẩn hóa thành chữ in hoa")
    void managerCreatesSupplier() throws Exception {
        String manager = loginAndGetAccessToken("manager");
        String code = "tst" + random();   // gửi chữ thường

        HttpResponse<String> response = call("POST", "/api/suppliers", partnerJson(code, "Công ty ABC", "0911222333"), manager);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(field(response.body(), "code")).isEqualTo(code.toUpperCase());
        assertThat(field(response.body(), "status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Nhân viên kho và kinh doanh không được tạo (403), Người xem được đọc (200)")
    void permissions() throws Exception {
        String json = partnerJson("TST" + random(), "Công ty XYZ", "0900000001");

        assertThat(call("POST", "/api/suppliers", json, loginAndGetAccessToken("staff")).statusCode()).isEqualTo(403);
        assertThat(call("POST", "/api/suppliers", json, loginAndGetAccessToken("sales")).statusCode()).isEqualTo(403);
        assertThat(call("POST", "/api/suppliers", json, null).statusCode()).isEqualTo(401);

        HttpResponse<String> read = call("GET", "/api/suppliers", null, loginAndGetAccessToken("viewer"));
        assertThat(read.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Trùng mã thì trả 409, thiếu tên thì trả 400")
    void duplicateCodeAndValidation() throws Exception {
        String manager = loginAndGetAccessToken("manager");
        String code = "TST" + random();
        assertThat(call("POST", "/api/suppliers", partnerJson(code, "Lần một", "0900000002"), manager).statusCode()).isEqualTo(201);

        HttpResponse<String> duplicate = call("POST", "/api/suppliers", partnerJson(code, "Lần hai", "0900000003"), manager);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("CONFLICT");

        HttpResponse<String> noName = call("POST", "/api/suppliers", partnerJson("TST" + random(), "", "0900000004"), manager);
        assertThat(noName.statusCode()).isEqualTo(400);
        assertThat(noName.body()).contains("VALIDATION_ERROR").contains("name");
    }

    @Test
    @DisplayName("Sửa thông tin: tên đổi, mã giữ nguyên. Id không tồn tại trả 404")
    void updateAndNotFound() throws Exception {
        String manager = loginAndGetAccessToken("manager");
        String code = "TST" + random();
        HttpResponse<String> created = call("POST", "/api/suppliers", partnerJson(code, "Tên cũ", "0900000005"), manager);
        long id = number(created.body(), "id");

        HttpResponse<String> updated = call("PUT", "/api/suppliers/" + id,
                partnerJson("MA-KHAC-BI-BO-QUA", "Tên mới", "0900000005"), manager);

        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(field(updated.body(), "name")).isEqualTo("Tên mới");
        assertThat(field(updated.body(), "code")).isEqualTo(code);

        assertThat(call("GET", "/api/suppliers/99999999", null, manager).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Tìm kiếm kết hợp 3 điều kiện: từ khóa, số điện thoại, trạng thái")
    void searchWithThreeFilters() throws Exception {
        String manager = loginAndGetAccessToken("manager");
        String r = random();
        String phone1 = "091" + r;
        String phone2 = "092" + r;

        // Hai nhà cung cấp cùng chứa từ khóa r trong mã; nhà thứ hai bị ngừng hoạt động
        call("POST", "/api/suppliers", partnerJson("TST" + r + "A", "Alpha " + r, phone1), manager);
        HttpResponse<String> second = call("POST", "/api/suppliers", partnerJson("TST" + r + "B", "Beta " + r, phone2), manager);
        long secondId = number(second.body(), "id");
        call("PATCH", "/api/suppliers/" + secondId + "/deactivate", null, manager);

        String base = "/api/suppliers?keyword=" + r;
        assertThat(number(call("GET", base, null, manager).body(), "totalElements")).isEqualTo(2);
        assertThat(number(call("GET", base + "&phone=" + phone1, null, manager).body(), "totalElements")).isEqualTo(1);
        assertThat(number(call("GET", base + "&status=INACTIVE", null, manager).body(), "totalElements")).isEqualTo(1);
        assertThat(number(call("GET", base + "&status=ACTIVE", null, manager).body(), "totalElements")).isEqualTo(1);
        assertThat(number(call("GET", base + "&phone=" + phone1 + "&status=INACTIVE", null, manager).body(), "totalElements"))
                .isZero();

        // Phân trang: mỗi trang 1 dòng thì 2 kết quả thành 2 trang
        HttpResponse<String> paged = call("GET", base + "&page=0&size=1", null, manager);
        assertThat(number(paged.body(), "totalPages")).isEqualTo(2);
        assertThat(number(paged.body(), "size")).isEqualTo(1);

        // Kích hoạt lại thì cả hai cùng ACTIVE
        call("PATCH", "/api/suppliers/" + secondId + "/activate", null, manager);
        assertThat(number(call("GET", base + "&status=ACTIVE", null, manager).body(), "totalElements")).isEqualTo(2);
    }

    @Test
    @DisplayName("Khách hàng dùng cùng khuôn: tạo, xem, tìm kiếm")
    void customerBasicFlow() throws Exception {
        String manager = loginAndGetAccessToken("manager");
        String r = random();

        HttpResponse<String> created = call("POST", "/api/customers", partnerJson("TST" + r, "Khách " + r, "0933" + r), manager);
        assertThat(created.statusCode()).isEqualTo(201);
        long id = number(created.body(), "id");

        assertThat(call("GET", "/api/customers/" + id, null, manager).statusCode()).isEqualTo(200);
        assertThat(number(call("GET", "/api/customers?keyword=" + r, null, manager).body(), "totalElements")).isEqualTo(1);
    }
}

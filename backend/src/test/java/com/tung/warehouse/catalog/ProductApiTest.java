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

/** Test sản phẩm và biến thể (UC2.3, UC2.5). */
class ProductApiTest extends AbstractApiTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from product_variants where sku like 'TST%'");
        jdbcTemplate.update("delete from products where name like 'TST%'");
        jdbcTemplate.update("delete from categories where name like 'TST%' and parent_id is not null");
        jdbcTemplate.update("delete from categories where name like 'TST%'");
        jdbcTemplate.update("delete from brands where name like 'TST%'");
        jdbcTemplate.update("delete from units where code like 'TST%'");
    }

    private static String random() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    // ---------- Các hàm dựng dữ liệu cho test ----------

    private long createCategory(String admin, String name) throws Exception {
        HttpResponse<String> response = call("POST", "/api/categories", "{\"name\":\"" + name + "\",\"parentId\":null}", admin);
        assertThat(response.statusCode()).isEqualTo(201);
        return number(response.body(), "id");
    }

    private long createBrand(String admin, String name) throws Exception {
        HttpResponse<String> response = call("POST", "/api/brands", "{\"name\":\"" + name + "\"}", admin);
        assertThat(response.statusCode()).isEqualTo(201);
        return number(response.body(), "id");
    }

    /** Lấy id của đơn vị tính mẫu đầu tiên (V5 đã seed sẵn). */
    private long sampleUnitId(String token) throws Exception {
        return number(call("GET", "/api/units", null, token).body(), "id");
    }

    private HttpResponse<String> createProduct(String manager, String name, long categoryId, Long brandId) throws Exception {
        String brand = brandId == null ? "null" : String.valueOf(brandId);
        String body = """
                {"name":"%s","categoryId":%d,"brandId":%s,"description":"Mô tả thử"}
                """.formatted(name, categoryId, brand);
        return call("POST", "/api/products", body, manager);
    }

    private static String variantJson(String sku, String barcode, String trackingType, String price, int warranty, long unitId) {
        String barcodeJson = barcode == null ? "null" : "\"" + barcode + "\"";
        return """
                {"sku":"%s","barcode":%s,"attributes":"256GB, Đen","unitId":%d,"trackingType":"%s",
                 "listPrice":%s,"warrantyMonths":%d}
                """.formatted(sku, barcodeJson, unitId, trackingType, price, warranty);
    }

    // ---------- Các test ----------

    @Test
    @DisplayName("Quản lý kho tạo sản phẩm và biến thể; SKU tự in hoa; chi tiết sản phẩm có danh sách biến thể")
    void managerCreatesProductAndVariant() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String manager = loginAndGetAccessToken("manager");
        String r = random();
        long categoryId = createCategory(admin, "TSTdm" + r);
        long brandId = createBrand(admin, "TSTbrand" + r);
        long unitId = sampleUnitId(manager);

        HttpResponse<String> product = createProduct(manager, "TST iPhone " + r, categoryId, brandId);
        assertThat(product.statusCode()).isEqualTo(201);
        long productId = number(product.body(), "id");

        HttpResponse<String> variant = call("POST", "/api/products/" + productId + "/variants",
                variantJson("tst-" + r + "-256", "8930" + r, "SERIAL", "25990000.50", 12, unitId), manager);
        assertThat(variant.statusCode()).isEqualTo(201);
        assertThat(field(variant.body(), "sku")).isEqualTo("TST-" + r + "-256");
        assertThat(field(variant.body(), "trackingType")).isEqualTo("SERIAL");
        assertThat(variant.body()).contains("25990000.5");

        HttpResponse<String> detail = call("GET", "/api/products/" + productId, null, manager);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("TST-" + r + "-256").contains("TSTbrand" + r).contains("TSTdm" + r);
    }

    @Test
    @DisplayName("Nhân viên kho và kinh doanh không được tạo sản phẩm (403), Người xem được đọc (200)")
    void permissions() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        long categoryId = createCategory(admin, "TSTdm" + random());
        String name = "TST " + random();

        assertThat(createProduct(loginAndGetAccessToken("staff"), name, categoryId, null).statusCode()).isEqualTo(403);
        assertThat(createProduct(loginAndGetAccessToken("sales"), name, categoryId, null).statusCode()).isEqualTo(403);
        assertThat(call("GET", "/api/products", null, loginAndGetAccessToken("viewer")).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Dữ liệu biến thể sai: giá âm, thiếu kiểu theo dõi, kiểu theo dõi không có thật đều bị 400")
    void variantValidation() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String manager = loginAndGetAccessToken("manager");
        String r = random();
        long productId = number(createProduct(manager, "TST " + r, createCategory(admin, "TSTdm" + r), null).body(), "id");
        long unitId = sampleUnitId(manager);
        String url = "/api/products/" + productId + "/variants";

        HttpResponse<String> negativePrice = call("POST", url, variantJson("TST" + r + "A", null, "QUANTITY", "-1", 0, unitId), manager);
        assertThat(negativePrice.statusCode()).isEqualTo(400);
        assertThat(negativePrice.body()).contains("VALIDATION_ERROR").contains("listPrice");

        String noTracking = """
                {"sku":"TST%sB","unitId":%d,"listPrice":100,"warrantyMonths":0}
                """.formatted(r, unitId);
        HttpResponse<String> missing = call("POST", url, noTracking, manager);
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(missing.body()).contains("trackingType");

        HttpResponse<String> badEnum = call("POST", url, variantJson("TST" + r + "C", null, "KHONG_CO", "100", 0, unitId), manager);
        assertThat(badEnum.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Trùng SKU (khác hoa/thường) hoặc trùng barcode bị 409; nhiều biến thể bỏ trống barcode vẫn được")
    void duplicateSkuAndBarcode() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String manager = loginAndGetAccessToken("manager");
        String r = random();
        long productId = number(createProduct(manager, "TST " + r, createCategory(admin, "TSTdm" + r), null).body(), "id");
        long unitId = sampleUnitId(manager);
        String url = "/api/products/" + productId + "/variants";

        assertThat(call("POST", url, variantJson("TST" + r + "-1", "BC" + r, "QUANTITY", "100", 6, unitId), manager).statusCode()).isEqualTo(201);

        assertThat(call("POST", url, variantJson("tst" + r + "-1", null, "QUANTITY", "100", 6, unitId), manager).statusCode()).isEqualTo(409);
        assertThat(call("POST", url, variantJson("TST" + r + "-2", "BC" + r, "QUANTITY", "100", 6, unitId), manager).statusCode()).isEqualTo(409);

        // Hai biến thể cùng bỏ trống barcode: cả hai lưu NULL, không vi phạm UNIQUE
        assertThat(call("POST", url, variantJson("TST" + r + "-3", "", "QUANTITY", "100", 6, unitId), manager).statusCode()).isEqualTo(201);
        assertThat(call("POST", url, variantJson("TST" + r + "-4", null, "QUANTITY", "100", 6, unitId), manager).statusCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("Sửa biến thể: đổi giá và bảo hành được; đổi SKU hoặc kiểu theo dõi bị 409; sai sản phẩm bị 404")
    void updateVariantRules() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String manager = loginAndGetAccessToken("manager");
        String r = random();
        long categoryId = createCategory(admin, "TSTdm" + r);
        long productId = number(createProduct(manager, "TST A" + r, categoryId, null).body(), "id");
        long otherProductId = number(createProduct(manager, "TST B" + r, categoryId, null).body(), "id");
        long unitId = sampleUnitId(manager);
        String sku = "TST" + r;

        long variantId = number(call("POST", "/api/products/" + productId + "/variants",
                variantJson(sku, null, "SERIAL", "1000", 12, unitId), manager).body(), "id");
        String url = "/api/products/" + productId + "/variants/" + variantId;

        HttpResponse<String> changed = call("PUT", url, variantJson(sku, null, "SERIAL", "2000", 24, unitId), manager);
        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(changed.body()).contains("2000").contains("\"warrantyMonths\":24");

        assertThat(call("PUT", url, variantJson(sku + "X", null, "SERIAL", "2000", 24, unitId), manager).statusCode()).isEqualTo(409);
        assertThat(call("PUT", url, variantJson(sku, null, "QUANTITY", "2000", 24, unitId), manager).statusCode()).isEqualTo(409);

        // Dùng id sản phẩm khác trên đường dẫn: biến thể không thuộc sản phẩm đó
        String wrongUrl = "/api/products/" + otherProductId + "/variants/" + variantId;
        assertThat(call("PUT", wrongUrl, variantJson(sku, null, "SERIAL", "2000", 24, unitId), manager).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Ngừng và kích hoạt lại biến thể, sản phẩm; sản phẩm ngừng kinh doanh không thêm được biến thể")
    void activateAndDeactivate() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String manager = loginAndGetAccessToken("manager");
        String r = random();
        long productId = number(createProduct(manager, "TST " + r, createCategory(admin, "TSTdm" + r), null).body(), "id");
        long unitId = sampleUnitId(manager);
        long variantId = number(call("POST", "/api/products/" + productId + "/variants",
                variantJson("TST" + r, null, "QUANTITY", "100", 0, unitId), manager).body(), "id");

        HttpResponse<String> variantOff = call("PATCH", "/api/products/" + productId + "/variants/" + variantId + "/deactivate", null, manager);
        assertThat(field(variantOff.body(), "status")).isEqualTo("INACTIVE");

        HttpResponse<String> productOff = call("PATCH", "/api/products/" + productId + "/deactivate", null, manager);
        assertThat(field(productOff.body(), "status")).isEqualTo("INACTIVE");

        String url = "/api/products/" + productId + "/variants";
        assertThat(call("POST", url, variantJson("TST" + r + "N", null, "QUANTITY", "100", 0, unitId), manager).statusCode()).isEqualTo(409);

        call("PATCH", "/api/products/" + productId + "/activate", null, manager);
        assertThat(call("POST", url, variantJson("TST" + r + "N", null, "QUANTITY", "100", 0, unitId), manager).statusCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("Danh mục đã ngừng hoạt động thì không tạo được sản phẩm trong đó")
    void inactiveCategoryRejected() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String manager = loginAndGetAccessToken("manager");
        long categoryId = createCategory(admin, "TSTdm" + random());
        call("PATCH", "/api/categories/" + categoryId + "/deactivate", null, admin);

        HttpResponse<String> response = createProduct(manager, "TST " + random(), categoryId, null);

        assertThat(response.statusCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("Tìm kiếm kết hợp 3 điều kiện: từ khóa, danh mục, trạng thái")
    void searchWithThreeFilters() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String manager = loginAndGetAccessToken("manager");
        String r = random();
        long category1 = createCategory(admin, "TSTdm1" + r);
        long category2 = createCategory(admin, "TSTdm2" + r);

        createProduct(manager, "TST Alpha " + r, category1, null);
        long secondId = number(createProduct(manager, "TST Beta " + r, category2, null).body(), "id");
        call("PATCH", "/api/products/" + secondId + "/deactivate", null, manager);

        String base = "/api/products?keyword=" + r;
        assertThat(number(call("GET", base, null, manager).body(), "totalElements")).isEqualTo(2);
        assertThat(number(call("GET", base + "&categoryId=" + category1, null, manager).body(), "totalElements")).isEqualTo(1);
        assertThat(number(call("GET", base + "&status=INACTIVE", null, manager).body(), "totalElements")).isEqualTo(1);
        assertThat(number(call("GET", base + "&categoryId=" + category1 + "&status=INACTIVE", null, manager).body(), "totalElements")).isZero();

        HttpResponse<String> paged = call("GET", base + "&page=0&size=1", null, manager);
        assertThat(number(paged.body(), "totalPages")).isEqualTo(2);
    }

    @Test
    @DisplayName("Không xóa được danh mục, thương hiệu, đơn vị đang được sản phẩm hoặc biến thể sử dụng")
    void deleteProtection() throws Exception {
        String admin = loginAndGetAccessToken("admin");
        String manager = loginAndGetAccessToken("manager");
        String r = random();
        long categoryId = createCategory(admin, "TSTdm" + r);
        long brandId = createBrand(admin, "TSTbrand" + r);
        String unitCode = "TST" + r.substring(0, 5);
        long unitId = number(call("POST", "/api/units", "{\"code\":\"" + unitCode + "\",\"name\":\"Đơn vị thử\"}", admin).body(), "id");

        long productId = number(createProduct(manager, "TST " + r, categoryId, brandId).body(), "id");
        call("POST", "/api/products/" + productId + "/variants", variantJson("TST" + r, null, "QUANTITY", "100", 0, unitId), manager);

        assertThat(call("DELETE", "/api/categories/" + categoryId, null, admin).statusCode()).isEqualTo(409);
        assertThat(call("DELETE", "/api/brands/" + brandId, null, admin).statusCode()).isEqualTo(409);
        assertThat(call("DELETE", "/api/units/" + unitId, null, admin).statusCode()).isEqualTo(409);
    }
}

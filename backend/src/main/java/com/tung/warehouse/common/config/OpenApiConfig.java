package com.tung.warehouse.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình thông tin chung cho tài liệu API (Swagger / OpenAPI).
 * springdoc tự quét controller để sinh các endpoint; lớp này chỉ bổ sung phần mô tả chung
 * và khai báo cách xác thực để nút "Authorize" trên Swagger UI hoạt động.
 */
@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH = "basicAuth";

    @Bean
    public OpenAPI warehouseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Electronics Warehouse API")
                        .description("API hệ thống quản lý kho thiết bị điện tử: nhập, xuất FIFO, điều chuyển, kiểm kê, RMA")
                        .version("0.1.0"))
                // Khai báo kiểu xác thực. Hiện là Basic Auth, khi làm JWT sẽ đổi thành "bearer".
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                // Áp dụng yêu cầu xác thực cho tất cả endpoint trong tài liệu
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}

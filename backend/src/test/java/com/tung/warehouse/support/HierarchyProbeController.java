package com.tung.warehouse.support;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller CHỈ TỒN TẠI TRONG TEST (nằm ở thư mục src/test, không đi vào bản chạy thật).
 * Mỗi endpoint đòi một mức quyền, để test kiểm tra quan hệ kế thừa vai trò hoạt động đúng.
 */
@RestController
@RequestMapping("/test-probe")
public class HierarchyProbeController {

    @GetMapping("/viewer")
    @PreAuthorize("hasRole('VIEWER')")
    public String viewer() {
        return "ok";
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    public String staff() {
        return "ok";
    }

    @GetMapping("/manager")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public String manager() {
        return "ok";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String admin() {
        return "ok";
    }
}

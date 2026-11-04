package com.tung.warehouse.identity.service;

import com.tung.warehouse.identity.entity.Role;
import com.tung.warehouse.identity.entity.User;
import com.tung.warehouse.identity.repository.RoleRepository;
import com.tung.warehouse.identity.repository.UserRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo sẵn 5 tài khoản demo (mỗi vai trò một người) khi ứng dụng khởi động mà chưa có tài khoản đó.
 * ApplicationRunner: Spring gọi hàm run() một lần sau khi ứng dụng khởi động xong.
 * Mật khẩu băm bằng BCrypt ngay lúc tạo nên không có mật khẩu thật nào nằm trong file SQL.
 * [Ngày 7] Gán luôn kho mẫu KHO-HN cho các tài khoản không phải Admin (Admin không bị giới hạn kho).
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private record DemoUser(String username, String roleName, String fullName) {
    }

    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("admin", "ADMIN", "Quản trị viên"),
            new DemoUser("manager", "WAREHOUSE_MANAGER", "Quản lý kho"),
            new DemoUser("staff", "WAREHOUSE_STAFF", "Nhân viên kho"),
            new DemoUser("sales", "SALES", "Nhân viên kinh doanh"),
            new DemoUser("viewer", "VIEWER", "Người xem"));

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;
    private final String demoPassword;

    public DemoDataInitializer(UserRepository userRepository, RoleRepository roleRepository,
                               PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate,
                               @Value("${app.bootstrap.enabled}") boolean enabled,
                               @Value("${app.bootstrap.password}") String demoPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        // Id kho mẫu do migration V6 tạo. Lấy bằng SQL trực tiếp để không phụ thuộc vào module warehouse.
        List<Long> demoWarehouse = jdbcTemplate.queryForList("select id from warehouses where code = 'KHO-HN'", Long.class);

        for (DemoUser demo : DEMO_USERS) {
            User user = userRepository.findByUsername(demo.username()).orElse(null);
            if (user == null) {
                Role role = roleRepository.findByName(demo.roleName()).orElseThrow();
                user = new User(demo.username(), demo.username() + "@warehouse.local",
                        passwordEncoder.encode(demoPassword), demo.fullName());
                user.addRole(role);
            }
            // Gán kho cho mọi tài khoản demo (kể cả tài khoản đã tạo từ trước), trừ Admin
            if (!demo.roleName().equals("ADMIN") && !demoWarehouse.isEmpty()) {
                user.getWarehouseIds().add(demoWarehouse.get(0));
            }
            userRepository.save(user);
        }
    }
}

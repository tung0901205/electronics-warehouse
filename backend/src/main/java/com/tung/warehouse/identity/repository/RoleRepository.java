package com.tung.warehouse.identity.repository;

import com.tung.warehouse.identity.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Chỉ cần khai báo interface, Spring Data tự sinh code.
 * Tên hàm findByName được hiểu thành: SELECT ... FROM roles WHERE name = ?
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}

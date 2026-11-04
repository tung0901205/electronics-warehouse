package com.tung.warehouse.identity.repository;

import com.tung.warehouse.identity.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** [SỬA Ngày 5] Thêm JpaSpecificationExecutor (để tìm kiếm bằng Specification) và existsByEmailAndIdNot. */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Email này đã thuộc về một người KHÁC (id khác) chưa? Dùng khi sửa thông tin. */
    boolean existsByEmailAndIdNot(String email, Long id);
}

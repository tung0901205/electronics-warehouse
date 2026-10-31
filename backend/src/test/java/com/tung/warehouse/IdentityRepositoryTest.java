package com.tung.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tung.warehouse.identity.entity.Role;
import com.tung.warehouse.identity.entity.User;
import com.tung.warehouse.identity.repository.RoleRepository;
import com.tung.warehouse.identity.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test chạy với MySQL thật (cần Docker đang chạy và biến môi trường DB_PASSWORD).
 * @Transactional trên lớp test: mỗi test tự rollback, không để rác trong database.
 */
@SpringBootTest
@Transactional
class IdentityRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("V1 đã seed đủ 5 vai trò")
    void seededRolesExist() {
        assertThat(roleRepository.count()).isEqualTo(5);
        assertThat(roleRepository.findByName("ADMIN")).isPresent();
    }

    @Test
    @DisplayName("Lưu user kèm vai trò, các cột chung được tự điền")
    void saveUserWithRole() {
        Role viewer = roleRepository.findByName("VIEWER").orElseThrow();
        User user = new User("tung", "tung@example.com", "hash-gia", "Nguyễn Văn Tùng");
        user.addRole(viewer);

        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getRoles()).hasSize(1);
    }

    @Test
    @DisplayName("Database tự chặn username trùng nhờ ràng buộc UNIQUE")
    void duplicateUsernameIsRejectedByDatabase() {
        userRepository.saveAndFlush(new User("tung", "a@example.com", "hash-gia", "A"));

        User duplicate = new User("tung", "b@example.com", "hash-gia", "B");

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

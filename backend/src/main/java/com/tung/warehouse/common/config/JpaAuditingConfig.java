package com.tung.warehouse.common.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Bật tính năng tự điền createdAt, updatedAt, createdBy.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    /**
     * Trả về "ai đang thao tác" để điền vào created_by.
     * Ngày 2 chưa có đăng nhập nên trả về rỗng. Ngày 3 (JWT) sẽ đổi thành id người dùng hiện tại.
     */
    @Bean
    public AuditorAware<Long> auditorProvider() {
        return Optional::empty;
    }
}

package com.tung.warehouse.common.config;

import com.tung.warehouse.common.security.CurrentUser;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Bật tính năng tự điền createdAt, updatedAt, createdBy.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "utcDateTimeProvider")
public class JpaAuditingConfig {

    /** "Ai đang thao tác": lấy id người dùng từ JWT của request hiện tại (rỗng nếu chưa đăng nhập). */
    @Bean
    public AuditorAware<Long> auditorProvider() {
        return CurrentUser::id;
    }

    /**
     * Luôn lưu giờ UTC. Mặc định Spring dùng giờ của máy (Việt Nam là UTC+7) trong khi MySQL
     * dùng CURRENT_TIMESTAMP theo UTC, hai bên sẽ lệch nhau 7 tiếng. Thống nhất UTC để tránh lỗi này.
     */
    @Bean
    public DateTimeProvider utcDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(ZoneOffset.UTC));
    }
}

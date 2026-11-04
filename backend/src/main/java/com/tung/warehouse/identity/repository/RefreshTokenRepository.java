package com.tung.warehouse.identity.repository;

import com.tung.warehouse.identity.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Thu hồi tất cả refresh token còn hiệu lực của một người dùng bằng MỘT câu UPDATE.
     * @Modifying báo đây là câu ghi dữ liệu (UPDATE/DELETE), không phải SELECT.
     */
    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);
}

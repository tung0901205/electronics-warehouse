package com.tung.warehouse.identity.service;

import com.tung.warehouse.common.exception.InvalidTokenException;
import com.tung.warehouse.identity.entity.RefreshToken;
import com.tung.warehouse.identity.entity.User;
import com.tung.warehouse.identity.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Quản lý refresh token.
 * Refresh token là một chuỗi ngẫu nhiên dài (không phải JWT). Database chỉ lưu bản băm SHA-256:
 * nếu database bị lộ, kẻ xấu cũng không dùng được token vì không suy ngược được chuỗi gốc.
 * Các hàm ở đây được gọi từ AuthService (đã có @Transactional).
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenDays;
    private final SecureRandom secureRandom = new SecureRandom(); // bộ sinh số ngẫu nhiên an toàn cho mật mã

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               @Value("${app.jwt.refresh-token-days}") long refreshTokenDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenDays = refreshTokenDays;
    }

    /** Tạo refresh token mới. Trả về chuỗi GỐC để gửi cho client (chỉ thấy được một lần). */
    public String issue(User user) {
        byte[] bytes = new byte[32];                  // 32 byte = 256 bit ngẫu nhiên
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(refreshTokenDays);
        refreshTokenRepository.save(new RefreshToken(user, hash(rawToken), expiresAt));
        return rawToken;
    }

    /** Tìm token theo chuỗi gốc client gửi lên. Không thấy thì báo lỗi 401. */
    public RefreshToken find(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Refresh token không hợp lệ"));
    }

    /** Đăng xuất: thu hồi token này. Không thấy token thì bỏ qua (gọi lại nhiều lần vẫn an toàn). */
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    /** Thu hồi mọi phiên đăng nhập của một người dùng. */
    public void revokeAllOf(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest); // 32 byte -> 64 ký tự hex, khớp CHAR(64)
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", e);
        }
    }
}

package com.tung.warehouse.identity.service;

import com.tung.warehouse.identity.entity.Role;
import com.tung.warehouse.identity.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Tạo access token (JWT). */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final long accessTokenMinutes;

    public JwtService(JwtEncoder jwtEncoder, @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    /**
     * JWT gồm 3 phần ngăn bằng dấu chấm: header.payload.signature.
     * Payload chứa các "claim" (thông tin). Ai cũng ĐỌC được payload, nhưng không SỬA được vì có chữ ký,
     * nên tuyệt đối không đặt mật khẩu hay dữ liệu bí mật vào token.
     *
     * Hàm này phải được gọi trong transaction vì user.getRoles() là LAZY.
     */
    public String createAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roleNames = user.getRoles().stream().map(Role::getName).sorted().toList();
        List<Long> warehouseIds = user.getWarehouseIds().stream().sorted().toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("electronics-warehouse")
                .subject(String.valueOf(user.getId()))          // "sub": id người dùng
                .issuedAt(now)                                  // "iat": thời điểm tạo
                .expiresAt(now.plus(accessTokenMinutes, ChronoUnit.MINUTES)) // "exp": thời điểm hết hạn
                .claim("username", user.getUsername())
                .claim("roles", roleNames)                      // ví dụ ["ADMIN"]
                .claim("warehouses", warehouseIds)              // [MỚI Ngày 7] id các kho được gán, ví dụ [1, 3]
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getAccessTokenSeconds() {
        return accessTokenMinutes * 60;
    }
}

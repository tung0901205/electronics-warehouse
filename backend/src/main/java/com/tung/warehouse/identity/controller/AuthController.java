package com.tung.warehouse.identity.controller;

import com.tung.warehouse.identity.dto.ChangePasswordRequest;
import com.tung.warehouse.identity.dto.LoginRequest;
import com.tung.warehouse.identity.dto.RefreshTokenRequest;
import com.tung.warehouse.identity.dto.TokenResponse;
import com.tung.warehouse.identity.dto.UserInfoResponse;
import com.tung.warehouse.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Xác thực", description = "Đăng nhập, làm mới phiên, đăng xuất, đổi mật khẩu")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập (UC1.1)")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới phiên bằng refresh token (UC1.2)")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đăng xuất: thu hồi refresh token (UC1.2)")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đổi mật khẩu (UC1.3)")
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        // @AuthenticationPrincipal: Spring đưa JWT của người đang đăng nhập vào tham số này
        authService.changePassword(Long.valueOf(jwt.getSubject()), request);
    }

    @GetMapping("/me")
    @Operation(summary = "Thông tin người đang đăng nhập")
    public UserInfoResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(Long.valueOf(jwt.getSubject()));
    }
}

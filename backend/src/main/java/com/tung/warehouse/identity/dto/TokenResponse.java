package com.tung.warehouse.identity.dto;

/**
 * Kết quả đăng nhập hoặc làm mới phiên.
 * expiresIn: số giây còn lại của access token (frontend dùng để biết khi nào cần làm mới).
 */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}

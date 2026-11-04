package com.tung.warehouse.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Sai tên đăng nhập hoặc mật khẩu: HTTP 401.
 * Cố ý dùng MỘT thông báo chung cho cả hai trường hợp, để kẻ tấn công không dò được tên nào có thật.
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Sai tên đăng nhập hoặc mật khẩu");
    }
}

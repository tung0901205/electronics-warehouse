package com.tung.warehouse.common.exception;

import org.springframework.http.HttpStatus;

/** Người dùng không được gán vào kho này: HTTP 403. */
public class WarehouseAccessDeniedException extends BusinessException {

    public WarehouseAccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "WAREHOUSE_ACCESS_DENIED", "Bạn không được phân quyền làm việc ở kho này");
    }
}

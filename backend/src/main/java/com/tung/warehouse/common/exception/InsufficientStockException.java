package com.tung.warehouse.common.exception;

import org.springframework.http.HttpStatus;

/** Không đủ tồn kho để thực hiện thao tác: HTTP 409. */
public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(int requested, int available) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                "Không đủ tồn kho: cần " + requested + ", chỉ còn " + available);
    }
}

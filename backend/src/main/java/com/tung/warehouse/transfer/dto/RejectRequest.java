package com.tung.warehouse.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
        @NotBlank(message = "Phải ghi lý do từ chối")
        @Size(max = 255, message = "Lý do tối đa 255 ký tự") String reason) {
}

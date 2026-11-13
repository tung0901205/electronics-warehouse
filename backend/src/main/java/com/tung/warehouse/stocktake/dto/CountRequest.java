package com.tung.warehouse.stocktake.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CountRequest(@NotEmpty(message = "Phải có ít nhất một dòng số đếm") @Valid List<CountEntry> entries) {
}

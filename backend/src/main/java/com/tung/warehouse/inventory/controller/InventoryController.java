package com.tung.warehouse.inventory.controller;

import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.inventory.dto.BalanceRow;
import com.tung.warehouse.inventory.dto.SerialRow;
import com.tung.warehouse.inventory.dto.SummaryRow;
import com.tung.warehouse.inventory.dto.TransactionRow;
import com.tung.warehouse.inventory.entity.TransactionType;
import com.tung.warehouse.inventory.service.InventoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API tra cứu tồn kho (chỉ đọc). Việc THAY ĐỔI tồn không có API trực tiếp:
 * tồn chỉ thay đổi thông qua phiếu nhập, xuất, điều chuyển, kiểm kê ở các giai đoạn sau.
 * Người dùng chỉ thấy dữ liệu của các kho được gán (Admin thấy tất cả).
 */
@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Tồn kho")
public class InventoryController {

    private final InventoryQueryService queryService;

    public InventoryController(InventoryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/balances")
    @Operation(summary = "Tồn theo từng dòng (kho, vị trí, biến thể, lô): thực tế, giữ chỗ, khả dụng (UC8.1)")
    public PageResponse<BalanceRow> balances(
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "locationId", required = false) Long locationId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "includeZero", defaultValue = "false") boolean includeZero,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return queryService.searchBalances(warehouseId, locationId, keyword, includeZero, page, size);
    }

    @GetMapping("/summary")
    @Operation(summary = "Tồn cộng dồn theo biến thể trong từng kho (UC8.1)")
    public PageResponse<SummaryRow> summary(
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "includeZero", defaultValue = "false") boolean includeZero,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return queryService.searchSummary(warehouseId, keyword, includeZero, page, size);
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Lịch sử giao dịch tồn kho, mới nhất lên đầu (UC8.2)")
    public PageResponse<TransactionRow> transactions(
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "variantId", required = false) Long variantId,
            @RequestParam(name = "type", required = false) TransactionType type,
            @RequestParam(name = "referenceType", required = false) String referenceType,
            @RequestParam(name = "referenceId", required = false) Long referenceId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return queryService.searchTransactions(warehouseId, variantId, type, referenceType, referenceId, page, size);
    }

    @GetMapping("/serials")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Tra cứu trạng thái hiện tại của thiết bị theo số serial (UC8.3)")
    public List<SerialRow> serials(@RequestParam(name = "serial") String serial) {
        return queryService.findBySerial(serial);
    }
}

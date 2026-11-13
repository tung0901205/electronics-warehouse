package com.tung.warehouse.inventory.service;

import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.common.security.CurrentUser;
import com.tung.warehouse.common.util.PagingUtil;
import com.tung.warehouse.inventory.dto.BalanceRow;
import com.tung.warehouse.inventory.dto.SerialEventRow;
import com.tung.warehouse.inventory.dto.SerialRow;
import com.tung.warehouse.inventory.dto.StockFilter;
import com.tung.warehouse.inventory.dto.SummaryRow;
import com.tung.warehouse.inventory.dto.TransactionFilter;
import com.tung.warehouse.inventory.dto.TransactionRow;
import com.tung.warehouse.inventory.entity.TransactionType;
import com.tung.warehouse.inventory.repository.InventoryQueryRepository;
import com.tung.warehouse.warehouse.service.WarehouseAccessService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ UC8.1 Xem tồn kho, UC8.2 Xem lịch sử giao dịch, UC8.3 Tra cứu serial. Chỉ đọc, không sửa dữ liệu. */
@Service
public class InventoryQueryService {

    private final InventoryQueryRepository queryRepository;
    private final WarehouseAccessService accessService;

    public InventoryQueryService(InventoryQueryRepository queryRepository, WarehouseAccessService accessService) {
        this.queryRepository = queryRepository;
        this.accessService = accessService;
    }

    /** Tồn theo từng dòng (kho, vị trí, lô). Sắp xếp lô nhập trước lên đầu (đúng thứ tự FIFO). */
    @Transactional(readOnly = true)
    public PageResponse<BalanceRow> searchBalances(Long warehouseId, Long locationId, String keyword,
                                                   boolean includeZero, int page, int size) {
        Pageable pageable = PagingUtil.of(page, size);
        List<Long> restrictTo = restrictTo(warehouseId);
        if (restrictTo != null && restrictTo.isEmpty()) {
            return toPage(List.of(), 0, pageable);
        }
        StockFilter filter = new StockFilter(warehouseId, locationId, keyword, includeZero, restrictTo);
        long total = queryRepository.countBalances(filter);
        List<BalanceRow> rows = total == 0 ? List.of()
                : queryRepository.findBalances(filter, pageable.getOffset(), pageable.getPageSize());
        return toPage(rows, total, pageable);
    }

    /** Tồn cộng dồn theo biến thể trong từng kho. */
    @Transactional(readOnly = true)
    public PageResponse<SummaryRow> searchSummary(Long warehouseId, String keyword, boolean includeZero, int page, int size) {
        Pageable pageable = PagingUtil.of(page, size);
        List<Long> restrictTo = restrictTo(warehouseId);
        if (restrictTo != null && restrictTo.isEmpty()) {
            return toPage(List.of(), 0, pageable);
        }
        StockFilter filter = new StockFilter(warehouseId, null, keyword, includeZero, restrictTo);
        long total = queryRepository.countSummary(filter);
        List<SummaryRow> rows = total == 0 ? List.of()
                : queryRepository.findSummary(filter, pageable.getOffset(), pageable.getPageSize());
        return toPage(rows, total, pageable);
    }

    /** Lịch sử giao dịch, mới nhất lên đầu. */
    @Transactional(readOnly = true)
    public PageResponse<TransactionRow> searchTransactions(Long warehouseId, Long variantId, TransactionType type,
                                                           String referenceType, Long referenceId, int page, int size) {
        Pageable pageable = PagingUtil.of(page, size);
        List<Long> restrictTo = restrictTo(warehouseId);
        if (restrictTo != null && restrictTo.isEmpty()) {
            return toPage(List.of(), 0, pageable);
        }
        TransactionFilter filter = new TransactionFilter(warehouseId, variantId, type, referenceType, referenceId, restrictTo);
        long total = queryRepository.countTransactions(filter);
        List<TransactionRow> rows = total == 0 ? List.of()
                : queryRepository.findTransactions(filter, pageable.getOffset(), pageable.getPageSize());
        return toPage(rows, total, pageable);
    }

    /** Tra cứu trạng thái hiện tại của thiết bị theo serial (khớp chính xác). */
    @Transactional(readOnly = true)
    public List<SerialRow> findBySerial(String serial) {
        if (serial == null || serial.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SERIAL", "Phải nhập số serial cần tra cứu");
        }
        List<Long> restrictTo = CurrentUser.isAdmin() ? null : new ArrayList<>(CurrentUser.warehouseIds());
        return queryRepository.findBySerial(serial.trim(), restrictTo);
    }

    /**
     * [MỚI Giai đoạn 6] Vòng đời của một serial: nhập, chuyển đi, chuyển đến, bán, nhận hàng trả...
     * Người thường chỉ xem được nếu thiết bị hiện đang ở kho được gán hoặc đã rời kho (cùng quy tắc với tra cứu serial).
     */
    @Transactional(readOnly = true)
    public List<SerialEventRow> serialHistory(String serial) {
        if (findBySerial(serial).isEmpty()) {
            return List.of();
        }
        return queryRepository.findSerialHistory(serial.trim());
    }

    /**
     * Xác định phạm vi kho được xem:
     *   - có chọn một kho cụ thể: phải được phép vào kho đó (nếu không báo 403), không cần giới hạn thêm
     *   - Admin: không giới hạn (trả null)
     *   - người khác: chỉ các kho được gán (có thể là danh sách rỗng)
     */
    private List<Long> restrictTo(Long warehouseId) {
        if (warehouseId != null) {
            accessService.assertCanAccess(warehouseId);
            return null;
        }
        if (CurrentUser.isAdmin()) {
            return null;
        }
        return new ArrayList<>(CurrentUser.warehouseIds());
    }

    private static <T> PageResponse<T> toPage(List<T> rows, long total, Pageable pageable) {
        int totalPages = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
    }
}

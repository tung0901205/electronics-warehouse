package com.tung.warehouse.inventory.service;

import com.tung.warehouse.catalog.dto.VariantResponse;
import com.tung.warehouse.catalog.service.ProductService;
import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.common.security.CurrentUser;
import com.tung.warehouse.inventory.dto.ReceiveCommand;
import com.tung.warehouse.inventory.dto.ReceiveResult;
import com.tung.warehouse.inventory.dto.StockInCommand;
import com.tung.warehouse.inventory.dto.StockOutCommand;
import com.tung.warehouse.inventory.entity.DeviceUnit;
import com.tung.warehouse.inventory.entity.DeviceUnitStatus;
import com.tung.warehouse.inventory.entity.InventoryBalance;
import com.tung.warehouse.inventory.entity.InventoryBatch;
import com.tung.warehouse.inventory.entity.InventoryTransaction;
import com.tung.warehouse.inventory.entity.TransactionType;
import com.tung.warehouse.inventory.repository.DeviceUnitRepository;
import com.tung.warehouse.inventory.repository.InventoryBalanceRepository;
import com.tung.warehouse.inventory.repository.InventoryBatchRepository;
import com.tung.warehouse.inventory.repository.InventoryTransactionRepository;
import com.tung.warehouse.warehouse.dto.LocationResponse;
import com.tung.warehouse.warehouse.service.LocationService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CỬA DUY NHẤT để thay đổi tồn kho. Không module nào khác được sửa trực tiếp bảng tồn.
 * Nhập, xuất, điều chuyển, kiểm kê... (các giai đoạn sau) đều gọi vào các hàm ở đây.
 *
 * Mọi hàm ghi đều theo cùng một khuôn:
 *   1. kiểm tra dữ liệu đầu vào
 *   2. KHÓA dòng tồn (SELECT ... FOR UPDATE) để người khác phải chờ
 *   3. áp dụng quy tắc nghiệp vụ lên entity (entity tự từ chối nếu thiếu tồn)
 *   4. ghi một dòng vào sổ giao dịch
 * Tất cả nằm trong MỘT transaction: bước nào lỗi thì toàn bộ được hoàn tác, không để dữ liệu dở dang.
 */
@Service
public class InventoryService {

    private final InventoryBatchRepository batchRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final DeviceUnitRepository unitRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductService productService;
    private final LocationService locationService;

    public InventoryService(InventoryBatchRepository batchRepository, InventoryBalanceRepository balanceRepository,
                            DeviceUnitRepository unitRepository, InventoryTransactionRepository transactionRepository,
                            ProductService productService, LocationService locationService) {
        this.batchRepository = batchRepository;
        this.balanceRepository = balanceRepository;
        this.unitRepository = unitRepository;
        this.transactionRepository = transactionRepository;
        this.productService = productService;
        this.locationService = locationService;
    }

    // ==================== NHẬN HÀNG (nhập kho) ====================

    /** Tạo một lô mới, tạo dòng tồn, tạo từng thiết bị (nếu có serial) và ghi sổ INBOUND. */
    @Transactional
    public ReceiveResult receive(ReceiveCommand cmd) {
        checkQuantity(cmd.quantity());
        checkReference(cmd.referenceType(), cmd.referenceId());
        if (cmd.batchCode() == null || cmd.batchCode().isBlank()) {
            throw badRequest("INVALID_BATCH", "Mã lô không được để trống");
        }
        if (cmd.unitCost() == null || cmd.unitCost().signum() < 0) {
            throw badRequest("INVALID_COST", "Giá nhập không được âm");
        }
        if (cmd.warrantyMonths() < 0) {
            throw badRequest("INVALID_WARRANTY", "Số tháng bảo hành không được âm");
        }

        VariantResponse variant = productService.getVariant(cmd.variantId());
        if (!"ACTIVE".equals(variant.status())) {
            throw new ConflictException("Biến thể đã ngừng kinh doanh: " + variant.sku());
        }

        LocationResponse location = locationService.getById(cmd.locationId());
        if (!location.warehouseId().equals(cmd.warehouseId())) {
            throw new ConflictException("Vị trí " + location.code() + " không thuộc kho đã chọn");
        }
        if (!"ACTIVE".equals(location.status())) {
            throw new ConflictException("Vị trí " + location.code() + " đã ngừng hoạt động");
        }
        if ("DEFECTIVE".equals(location.locationType())) {
            throw new ConflictException("Không nhập hàng tốt vào vị trí hàng lỗi");
        }

        String batchCode = cmd.batchCode().trim();
        if (batchRepository.existsByVariantIdAndBatchCode(cmd.variantId(), batchCode)) {
            throw new ConflictException("Mã lô đã tồn tại cho biến thể này: " + batchCode);
        }
        List<String> serials = validateSerialsForReceive(variant, cmd);

        // Kiểm tra xong mới bắt đầu ghi
        LocalDateTime receivedAt = cmd.receivedAt() == null ? LocalDateTime.now(ZoneOffset.UTC) : cmd.receivedAt();
        InventoryBatch batch = batchRepository.save(new InventoryBatch(cmd.variantId(), batchCode, cmd.supplierId(),
                null, receivedAt, cmd.unitCost(), cmd.warrantyMonths()));
        InventoryBalance balance = balanceRepository.save(new InventoryBalance(cmd.warehouseId(), cmd.locationId(),
                cmd.variantId(), batch, cmd.quantity()));
        for (String serial : serials) {
            unitRepository.save(new DeviceUnit(cmd.variantId(), batch, serial, cmd.locationId()));
        }

        writeLedger(TransactionType.INBOUND, balance, cmd.quantity(), cmd.referenceType(), cmd.referenceId(), cmd.note());
        return new ReceiveResult(batch.getId(), balance.getId());
    }

    // ==================== GIẢM TỒN ====================

    /** Giảm tồn ở một dòng tồn: xuất kho, xuất điều chuyển, điều chỉnh giảm. */
    @Transactional
    public void decrease(StockOutCommand cmd) {
        checkQuantity(cmd.quantity());
        checkReference(cmd.referenceType(), cmd.referenceId());
        DeviceUnitStatus newUnitStatus = statusAfterLeaving(cmd.type()); // đồng thời kiểm tra loại giao dịch hợp lệ

        InventoryBalance balance = lockBalance(cmd.balanceId());
        VariantResponse variant = productService.getVariant(balance.getVariantId());
        List<String> serials = cmd.serials() == null ? List.of() : cmd.serials().stream().map(String::trim).toList();
        boolean serialTracked = "SERIAL".equals(variant.trackingType());

        if (serialTracked && serials.size() != cmd.quantity()) {
            throw badRequest("SERIAL_COUNT_MISMATCH",
                    "Hàng theo dõi serial cần đúng " + cmd.quantity() + " serial, đã nhập " + serials.size());
        }
        if (!serialTracked && !serials.isEmpty()) {
            throw badRequest("SERIAL_NOT_ALLOWED", "Hàng theo dõi theo số lượng không nhập serial");
        }

        balance.decrease(cmd.quantity(), cmd.fromReserved()); // ném InsufficientStockException nếu thiếu tồn
        if (serialTracked) {
            moveSerialsOut(balance, serials, newUnitStatus, cmd.fromReserved());
        }
        writeLedger(cmd.type(), balance, -cmd.quantity(), cmd.referenceType(), cmd.referenceId(), cmd.note());
    }

    // ==================== TĂNG TỒN Ở DÒNG ĐÃ CÓ ====================

    /** Tăng tồn ở một dòng tồn đã có: nhận hàng điều chuyển, điều chỉnh tăng, nhận lại hàng trả (chỉ hàng theo số lượng). */
    @Transactional
    public void increase(StockInCommand cmd) {
        checkQuantity(cmd.quantity());
        checkReference(cmd.referenceType(), cmd.referenceId());
        if (cmd.type() != TransactionType.ADJUST && cmd.type() != TransactionType.TRANSFER_IN
                && cmd.type() != TransactionType.RMA_IN) {
            throw badRequest("INVALID_TYPE", "Loại giao dịch " + cmd.type() + " không dùng để tăng tồn ở dòng đã có");
        }

        InventoryBalance balance = lockBalance(cmd.balanceId());
        VariantResponse variant = productService.getVariant(balance.getVariantId());
        if ("SERIAL".equals(variant.trackingType())) {
            // Hàng có serial phải đi kèm danh sách serial; luồng đó sẽ được làm ở giai đoạn điều chuyển và đổi trả
            throw new ConflictException("Hàng theo dõi serial chưa hỗ trợ tăng tồn theo số lượng");
        }

        balance.increase(cmd.quantity());
        writeLedger(cmd.type(), balance, cmd.quantity(), cmd.referenceType(), cmd.referenceId(), cmd.note());
    }

    // ==================== GIỮ CHỖ ====================

    /** Giữ chỗ một phần tồn khả dụng cho phiếu xuất. Tồn thực tế không đổi nên không ghi sổ. */
    @Transactional
    public void reserve(Long balanceId, int quantity) {
        checkQuantity(quantity);
        lockBalance(balanceId).reserve(quantity);
    }

    /** Hủy giữ chỗ (phiếu xuất bị hủy). */
    @Transactional
    public void release(Long balanceId, int quantity) {
        checkQuantity(quantity);
        lockBalance(balanceId).release(quantity);
    }

    // ==================== HÀM HỖ TRỢ ====================

    /** Đọc và KHÓA dòng tồn. Phải gọi bên trong transaction. */
    private InventoryBalance lockBalance(Long balanceId) {
        return balanceRepository.findByIdForUpdate(balanceId)
                .orElseThrow(() -> new NotFoundException("Dòng tồn kho", balanceId));
    }

    /** Ghi một dòng vào sổ giao dịch. quantityDelta có dấu: dương là tăng, âm là giảm. */
    private void writeLedger(TransactionType type, InventoryBalance balance, int quantityDelta,
                             String referenceType, Long referenceId, String note) {
        InventoryBatch batch = balance.getBatch();
        transactionRepository.save(new InventoryTransaction(type, balance.getWarehouseId(), balance.getLocationId(),
                balance.getVariantId(), batch.getId(), quantityDelta, batch.getUnitCost(),
                referenceType, referenceId, note, CurrentUser.id().orElse(null)));
    }

    /** Với biến thể SERIAL: số serial phải đủ, không trùng nhau, chưa tồn tại. Với biến thể QUANTITY: không được có serial. */
    private List<String> validateSerialsForReceive(VariantResponse variant, ReceiveCommand cmd) {
        List<String> serials = cmd.serials() == null ? List.of() : cmd.serials().stream().map(String::trim).toList();

        if (!"SERIAL".equals(variant.trackingType())) {
            if (!serials.isEmpty()) {
                throw badRequest("SERIAL_NOT_ALLOWED", "Hàng theo dõi theo số lượng không nhập serial");
            }
            return serials;
        }
        if (serials.size() != cmd.quantity()) {
            throw badRequest("SERIAL_COUNT_MISMATCH",
                    "Hàng theo dõi serial cần đúng " + cmd.quantity() + " serial, đã nhập " + serials.size());
        }
        if (serials.stream().anyMatch(String::isBlank)) {
            throw badRequest("INVALID_SERIAL", "Serial không được để trống");
        }
        if (new HashSet<>(serials).size() != serials.size()) {
            throw new ConflictException("Danh sách serial có số bị trùng nhau");
        }
        List<String> existing = unitRepository.findExistingSerials(cmd.variantId(), serials);
        if (!existing.isEmpty()) {
            throw new ConflictException("Serial đã tồn tại: " + existing);
        }
        return serials;
    }

    /** Chuyển các thiết bị ra khỏi kho: kiểm tra đúng lô, đúng vị trí, đúng trạng thái rồi đổi trạng thái. */
    private void moveSerialsOut(InventoryBalance balance, List<String> serials, DeviceUnitStatus newStatus, boolean fromReserved) {
        List<DeviceUnit> units = unitRepository.findByBatchIdAndLocationIdAndSerialNumberIn(
                balance.getBatch().getId(), balance.getLocationId(), serials);
        if (units.size() != serials.size()) {
            throw new ConflictException("Có serial không thuộc lô hoặc vị trí này, hoặc bị nhập trùng trong danh sách");
        }
        DeviceUnitStatus requiredStatus = fromReserved ? DeviceUnitStatus.RESERVED : DeviceUnitStatus.IN_STOCK;
        for (DeviceUnit unit : units) {
            if (unit.getStatus() != requiredStatus) {
                throw new ConflictException("Serial " + unit.getSerialNumber() + " đang ở trạng thái "
                        + unit.getStatus() + ", không thể xuất");
            }
            unit.leaveWarehouse(newStatus);
        }
    }

    /** Thiết bị sẽ ở trạng thái nào sau khi rời kho, tùy loại giao dịch. Loại không hợp lệ thì báo lỗi. */
    private DeviceUnitStatus statusAfterLeaving(TransactionType type) {
        return switch (type) {
            case OUTBOUND -> DeviceUnitStatus.SOLD;
            case TRANSFER_OUT -> DeviceUnitStatus.IN_TRANSIT;
            case ADJUST -> DeviceUnitStatus.SCRAPPED;
            default -> throw badRequest("INVALID_TYPE", "Loại giao dịch " + type + " không dùng để giảm tồn");
        };
    }

    private void checkQuantity(int quantity) {
        if (quantity <= 0) {
            throw badRequest("INVALID_QUANTITY", "Số lượng phải lớn hơn 0");
        }
    }

    private void checkReference(String referenceType, Long referenceId) {
        if (referenceType == null || referenceType.isBlank() || referenceId == null) {
            throw badRequest("INVALID_REFERENCE", "Phải ghi rõ phiếu gốc (loại và mã) cho mỗi giao dịch tồn kho");
        }
    }

    private BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }
}

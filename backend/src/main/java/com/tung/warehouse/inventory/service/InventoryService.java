package com.tung.warehouse.inventory.service;

import com.tung.warehouse.catalog.dto.VariantResponse;
import com.tung.warehouse.catalog.service.ProductService;
import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.InsufficientStockException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.common.security.CurrentUser;
import com.tung.warehouse.inventory.dto.BalanceInfo;
import com.tung.warehouse.inventory.dto.BalanceSnapshot;
import com.tung.warehouse.inventory.dto.CountableBalance;
import com.tung.warehouse.inventory.dto.IssuedPiece;
import com.tung.warehouse.inventory.dto.ReceiveCommand;
import com.tung.warehouse.inventory.dto.ReceiveResult;
import com.tung.warehouse.inventory.dto.StockInCommand;
import com.tung.warehouse.inventory.dto.StockOutCommand;
import com.tung.warehouse.inventory.dto.StockOutResult;
import com.tung.warehouse.inventory.entity.DeviceUnit;
import com.tung.warehouse.inventory.entity.DeviceUnitEvent;
import com.tung.warehouse.inventory.entity.DeviceUnitStatus;
import com.tung.warehouse.inventory.entity.InventoryBalance;
import com.tung.warehouse.inventory.entity.InventoryBatch;
import com.tung.warehouse.inventory.entity.InventoryReservation;
import com.tung.warehouse.inventory.entity.InventoryTransaction;
import com.tung.warehouse.inventory.entity.ReservationStatus;
import com.tung.warehouse.inventory.entity.TransactionType;
import com.tung.warehouse.inventory.repository.DeviceUnitEventRepository;
import com.tung.warehouse.inventory.repository.DeviceUnitRepository;
import com.tung.warehouse.inventory.repository.InventoryBalanceRepository;
import com.tung.warehouse.inventory.repository.InventoryBatchRepository;
import com.tung.warehouse.inventory.repository.InventoryQueryRepository;
import com.tung.warehouse.inventory.repository.InventoryReservationRepository;
import com.tung.warehouse.inventory.repository.InventoryTransactionRepository;
import com.tung.warehouse.warehouse.dto.LocationResponse;
import com.tung.warehouse.warehouse.service.LocationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
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
    private final InventoryReservationRepository reservationRepository;
    private final DeviceUnitEventRepository eventRepository;
    private final InventoryQueryRepository queryRepository;
    private final ProductService productService;
    private final LocationService locationService;

    public InventoryService(InventoryBatchRepository batchRepository, InventoryBalanceRepository balanceRepository,
                            DeviceUnitRepository unitRepository, InventoryTransactionRepository transactionRepository,
                            InventoryReservationRepository reservationRepository, DeviceUnitEventRepository eventRepository,
                            InventoryQueryRepository queryRepository,
                            ProductService productService, LocationService locationService) {
        this.batchRepository = batchRepository;
        this.balanceRepository = balanceRepository;
        this.unitRepository = unitRepository;
        this.transactionRepository = transactionRepository;
        this.reservationRepository = reservationRepository;
        this.eventRepository = eventRepository;
        this.queryRepository = queryRepository;
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
                cmd.inboundLineId(), receivedAt, cmd.unitCost(), cmd.warrantyMonths()));
        InventoryBalance balance = balanceRepository.save(new InventoryBalance(cmd.warehouseId(), cmd.locationId(),
                cmd.variantId(), batch, cmd.quantity()));
        for (String serial : serials) {
            DeviceUnit unit = unitRepository.save(new DeviceUnit(cmd.variantId(), batch, serial, cmd.locationId()));
            recordUnitEvent(unit, TransactionType.INBOUND, null, cmd.locationId(), cmd.referenceType(), cmd.referenceId());
        }

        writeLedger(TransactionType.INBOUND, balance, cmd.quantity(), cmd.referenceType(), cmd.referenceId(), cmd.note());
        return new ReceiveResult(batch.getId(), balance.getId());
    }

    // ==================== GIẢM TỒN ====================

    /** Giảm tồn ở một dòng tồn: xuất kho, xuất điều chuyển, điều chỉnh giảm. */
    @Transactional
    public StockOutResult decrease(StockOutCommand cmd) {
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
            moveSerialsOut(balance, serials, newUnitStatus, cmd);
        }
        writeLedger(cmd.type(), balance, -cmd.quantity(), cmd.referenceType(), cmd.referenceId(), cmd.note());

        InventoryBatch batch = balance.getBatch();
        return new StockOutResult(batch.getId(), batch.getBatchCode(), balance.getLocationId(), batch.getUnitCost(), warrantyEndDate(batch));
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

    // ==================== GIỮ CHỖ THEO FIFO (cho phiếu xuất) ====================

    /**
     * Giữ chỗ `quantity` sản phẩm của một biến thể trong một kho cho một dòng phiếu xuất, theo FIFO:
     * lô nhận trước được giữ trước, một dòng xuất có thể lấy từ nhiều lô.
     *
     * Chống "hai người cùng xuất số hàng cuối": mỗi dòng tồn được KHÓA (SELECT ... FOR UPDATE) trước khi đọc số liệu,
     * nên hai phiếu cùng tranh một dòng tồn sẽ lần lượt, không bao giờ cùng thấy một số hàng còn trống.
     * Thiếu hàng thì ném InsufficientStockException và cả transaction được hoàn tác: không giữ chỗ dở dang.
     */
    @Transactional
    public void reserveFifo(Long warehouseId, Long variantId, int quantity, Long outboundLineId) {
        checkQuantity(quantity);
        boolean serialTracked = "SERIAL".equals(productService.getVariant(variantId).trackingType());

        int remaining = quantity;
        for (Number idValue : balanceRepository.findFifoBalanceIds(warehouseId, variantId)) {
            Long balanceId = idValue.longValue();
            if (remaining == 0) {
                break;
            }
            InventoryBalance balance = lockBalance(balanceId);          // khóa rồi mới đọc số liệu mới nhất
            int take = Math.min(remaining, balance.getAvailable());
            if (take <= 0) {
                continue;                                               // dòng này vừa bị người khác lấy hết
            }
            balance.reserve(take);

            if (serialTracked) {
                // Chọn đúng các thiết bị còn IN_STOCK của lô và vị trí này, mỗi thiết bị một bản ghi giữ chỗ
                List<DeviceUnit> units = unitRepository.findByBatchIdAndLocationIdAndStatusOrderByIdAsc(
                        balance.getBatch().getId(), balance.getLocationId(), DeviceUnitStatus.IN_STOCK, PageRequest.of(0, take));
                if (units.size() < take) {
                    throw new ConflictException("Số serial còn sẵn không khớp với tồn kho của lô "
                            + balance.getBatch().getBatchCode());
                }
                for (DeviceUnit unit : units) {
                    unit.reserve();
                    reservationRepository.save(new InventoryReservation(balance, outboundLineId, 1, unit));
                }
            } else {
                reservationRepository.save(new InventoryReservation(balance, outboundLineId, take, null));
            }
            remaining -= take;
        }

        if (remaining > 0) {
            throw new InsufficientStockException(quantity, quantity - remaining);
        }
    }

    /** Hủy toàn bộ giữ chỗ còn hiệu lực của một dòng phiếu xuất (phiếu bị hủy): trả hàng về phần khả dụng. */
    @Transactional
    public void releaseReservations(Long outboundLineId) {
        for (InventoryReservation reservation : activeReservationsInFifoOrder(outboundLineId)) {
            InventoryBalance balance = lockBalance(reservation.getBalance().getId());
            balance.release(reservation.getQuantity());
            if (reservation.getDeviceUnit() != null) {
                reservation.getDeviceUnit().unreserve();
            }
            reservation.markReleased();
        }
    }

    /**
     * Xuất hàng đã giữ chỗ của một dòng phiếu xuất: trừ tồn thực tế và số giữ chỗ, ghi sổ OUTBOUND,
     * chuyển serial sang SOLD. Trả về các "mảnh" hàng đã xuất để module outbound ghi phân bổ.
     */
    @Transactional
    public List<IssuedPiece> issueReservations(Long outboundLineId, Long outboundOrderId, String note) {
        List<InventoryReservation> reservations = activeReservationsInFifoOrder(outboundLineId);
        if (reservations.isEmpty()) {
            throw new ConflictException("Dòng xuất chưa có hàng được giữ chỗ");
        }

        // Gom các giữ chỗ theo dòng tồn (giữ nguyên thứ tự FIFO để khóa đúng thứ tự)
        Map<Long, List<InventoryReservation>> byBalance = new LinkedHashMap<>();
        for (InventoryReservation reservation : reservations) {
            byBalance.computeIfAbsent(reservation.getBalance().getId(), key -> new ArrayList<>()).add(reservation);
        }

        List<IssuedPiece> pieces = new ArrayList<>();
        for (Map.Entry<Long, List<InventoryReservation>> entry : byBalance.entrySet()) {
            List<InventoryReservation> group = entry.getValue();
            int quantity = group.stream().mapToInt(InventoryReservation::getQuantity).sum();
            List<String> serials = group.stream().filter(r -> r.getDeviceUnit() != null)
                    .map(r -> r.getDeviceUnit().getSerialNumber()).toList();

            StockOutResult result = decrease(new StockOutCommand(entry.getKey(), quantity, TransactionType.OUTBOUND, true,
                    serials.isEmpty() ? null : serials, "OUTBOUND_ORDER", outboundOrderId, note));

            for (InventoryReservation reservation : group) {
                reservation.markConsumed();
                DeviceUnit unit = reservation.getDeviceUnit();
                pieces.add(new IssuedPiece(result.batchId(), result.batchCode(), result.locationId(),
                        unit == null ? null : unit.getId(), unit == null ? null : unit.getSerialNumber(),
                        reservation.getQuantity(), result.unitCost(), result.warrantyEndDate()));
            }
        }
        return pieces;
    }

    /** Các giữ chỗ còn hiệu lực của một dòng, sắp theo thứ tự FIFO của dòng tồn (cùng thứ tự khi khóa ở mọi nơi). */
    private List<InventoryReservation> activeReservationsInFifoOrder(Long outboundLineId) {
        List<InventoryReservation> reservations =
                new ArrayList<>(reservationRepository.findByOutboundLineIdAndStatus(outboundLineId, ReservationStatus.ACTIVE));
        reservations.sort(Comparator
                .comparing((InventoryReservation r) -> r.getBalance().getBatch().getReceivedAt())
                .thenComparing(r -> r.getBalance().getId()));
        return reservations;
    }

    /** Hạn bảo hành = ngày xuất (hôm nay, UTC) cộng số tháng bảo hành của lô. Lô không bảo hành thì để rỗng. */
    private LocalDate warrantyEndDate(InventoryBatch batch) {
        if (batch.getWarrantyMonths() <= 0) {
            return null;
        }
        return LocalDate.now(ZoneOffset.UTC).plusMonths(batch.getWarrantyMonths());
    }

    // ==================== ĐIỀU CHUYỂN KHO ====================

    /**
     * Xuất `quantity` sản phẩm của một biến thể khỏi một kho theo FIFO (dùng khi xuất kho nguồn của phiếu điều chuyển).
     * Có thể lấy từ nhiều lô. Trả về các "mảnh" hàng đã lấy: mỗi mảnh thuộc đúng một lô (hàng serial: mỗi thiết bị một mảnh).
     * Hàng serial chuyển sang IN_TRANSIT. Thiếu hàng thì ném InsufficientStockException và hoàn tác toàn bộ.
     */
    @Transactional
    public List<IssuedPiece> issueFifo(Long warehouseId, Long variantId, int quantity, TransactionType type,
                                       String referenceType, Long referenceId, String note) {
        checkQuantity(quantity);
        boolean serialTracked = "SERIAL".equals(productService.getVariant(variantId).trackingType());

        List<IssuedPiece> pieces = new ArrayList<>();
        int remaining = quantity;
        for (Number idValue : balanceRepository.findFifoBalanceIds(warehouseId, variantId)) {
            if (remaining == 0) {
                break;
            }
            Long balanceId = idValue.longValue();
            InventoryBalance balance = lockBalance(balanceId);
            int take = Math.min(remaining, balance.getAvailable());
            if (take <= 0) {
                continue;
            }

            List<DeviceUnit> units = List.of();
            if (serialTracked) {
                units = unitRepository.findByBatchIdAndLocationIdAndStatusOrderByIdAsc(
                        balance.getBatch().getId(), balance.getLocationId(), DeviceUnitStatus.IN_STOCK, PageRequest.of(0, take));
                if (units.size() < take) {
                    throw new ConflictException("Số serial còn sẵn không khớp với tồn kho của lô " + balance.getBatch().getBatchCode());
                }
            }
            List<String> serials = units.stream().map(DeviceUnit::getSerialNumber).toList();
            StockOutResult result = decrease(new StockOutCommand(balanceId, take, type, false,
                    serials.isEmpty() ? null : serials, referenceType, referenceId, note));

            if (serialTracked) {
                for (DeviceUnit unit : units) {
                    pieces.add(new IssuedPiece(result.batchId(), result.batchCode(), result.locationId(),
                            unit.getId(), unit.getSerialNumber(), 1, result.unitCost(), null));
                }
            } else {
                pieces.add(new IssuedPiece(result.batchId(), result.batchCode(), result.locationId(),
                        null, null, take, result.unitCost(), null));
            }
            remaining -= take;
        }
        if (remaining > 0) {
            throw new InsufficientStockException(quantity, quantity - remaining);
        }
        return pieces;
    }

    /**
     * Nhận hàng điều chuyển ở kho đích: cộng tồn vào đúng lô gốc (giữ nguyên giá vốn và ngày nhận để FIFO còn đúng),
     * ghi sổ TRANSFER_IN, và với hàng serial thì đưa các thiết bị từ IN_TRANSIT về IN_STOCK tại vị trí mới.
     */
    @Transactional
    public void receiveTransfer(Long warehouseId, Long locationId, Long variantId, Long batchId, int quantity,
                                List<String> serials, String referenceType, Long referenceId, String note) {
        checkQuantity(quantity);
        checkReference(referenceType, referenceId);
        boolean serialTracked = "SERIAL".equals(productService.getVariant(variantId).trackingType());
        checkTargetLocation(warehouseId, locationId, false);

        List<String> serialList = serials == null ? List.of() : serials.stream().map(String::trim).toList();
        if (serialTracked && serialList.size() != quantity) {
            throw badRequest("SERIAL_COUNT_MISMATCH", "Hàng theo dõi serial cần đúng " + quantity + " serial, đã nhập " + serialList.size());
        }
        if (!serialTracked && !serialList.isEmpty()) {
            throw badRequest("SERIAL_NOT_ALLOWED", "Hàng theo dõi theo số lượng không nhập serial");
        }

        InventoryBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new NotFoundException("Lô hàng", batchId));
        InventoryBalance balance = findOrCreateLockedBalance(warehouseId, locationId, variantId, batch);

        if (serialTracked) {
            List<DeviceUnit> units = unitRepository.findByVariantIdAndSerialNumberIn(variantId, serialList);
            if (units.size() != serialList.size()) {
                throw new ConflictException("Có serial không tồn tại trong hệ thống");
            }
            for (DeviceUnit unit : units) {
                if (!unit.getBatch().getId().equals(batchId)) {
                    throw new ConflictException("Serial " + unit.getSerialNumber() + " không thuộc lô đang nhận");
                }
                unit.arriveAt(locationId); // kiểm tra đang IN_TRANSIT, rồi đưa về IN_STOCK tại vị trí mới
                recordUnitEvent(unit, TransactionType.TRANSFER_IN, null, locationId, referenceType, referenceId);
            }
        }
        balance.increase(quantity);
        writeLedger(TransactionType.TRANSFER_IN, balance, quantity, referenceType, referenceId, note);
    }

    // ==================== NHẬN HÀNG KHÁCH TRẢ (RMA) ====================

    /**
     * Nhận lại một thiết bị khách trả: thiết bị phải đang ở trạng thái SOLD.
     * Hàng tốt (defective = false) thành IN_STOCK, hàng lỗi (defective = true) thành DEFECTIVE; cộng tồn vào lô gốc
     * của thiết bị tại vị trí chỉ định (hàng lỗi nằm ở vị trí DEFECTIVE nên không bao giờ được xuất lại) và ghi sổ RMA_IN.
     */
    @Transactional
    public void receiveReturn(Long warehouseId, Long locationId, Long variantId, String serial, boolean defective,
                              String referenceType, Long referenceId, String note) {
        checkReference(referenceType, referenceId);
        checkTargetLocation(warehouseId, locationId, defective);

        DeviceUnit unit = unitRepository.findByVariantIdAndSerialNumber(variantId, serial.trim())
                .orElseThrow(() -> new NotFoundException("Serial", serial));
        InventoryBalance balance = findOrCreateLockedBalance(warehouseId, locationId, variantId, unit.getBatch());

        unit.returnToStock(locationId, defective); // kiểm tra đang SOLD
        recordUnitEvent(unit, TransactionType.RMA_IN, null, locationId, referenceType, referenceId);
        balance.increase(1);
        writeLedger(TransactionType.RMA_IN, balance, 1, referenceType, referenceId, note);
    }

    /** Id của thiết bị theo biến thể và serial (NotFound nếu không có). */
    @Transactional(readOnly = true)
    public Long findDeviceUnitId(Long variantId, String serial) {
        return unitRepository.findByVariantIdAndSerialNumber(variantId, serial.trim())
                .map(DeviceUnit::getId)
                .orElseThrow(() -> new NotFoundException("Serial", serial));
    }

    // ==================== KIỂM KÊ ====================

    /** Các dòng tồn của một kho (có thể lọc theo khu vực hoặc biến thể) để đưa vào đợt kiểm kê. */
    @Transactional(readOnly = true)
    public List<CountableBalance> findCountableBalances(Long warehouseId, Long zoneId, Long variantId) {
        return queryRepository.findCountableBalances(warehouseId, zoneId, variantId);
    }

    /** Thông tin hiển thị (SKU, vị trí, lô) cho một nhóm dòng tồn. */
    @Transactional(readOnly = true)
    public Map<Long, BalanceInfo> describeBalances(List<Long> balanceIds) {
        return queryRepository.findBalanceInfos(balanceIds);
    }

    /** Số liệu hiện tại của một dòng tồn (dùng để chụp lại sau khi phát hiện tồn đã thay đổi). */
    @Transactional(readOnly = true)
    public BalanceSnapshot currentSnapshot(Long balanceId) {
        InventoryBalance balance = balanceRepository.findById(balanceId).orElseThrow(() -> new NotFoundException("Dòng tồn kho", balanceId));
        return new BalanceSnapshot(balance.getOnHand(), balance.getVersion());
    }

    /** Có thể điều chỉnh dòng tồn về số đếm không? Trả về lý do nếu không, null nếu được. Chỉ đọc, không khóa. */
    @Transactional(readOnly = true)
    public String adjustmentProblem(Long balanceId, long snapshotVersion, int countedQuantity) {
        InventoryBalance balance = balanceRepository.findById(balanceId).orElseThrow(() -> new NotFoundException("Dòng tồn kho", balanceId));
        return adjustmentProblem(balance, snapshotVersion, countedQuantity);
    }

    /**
     * Điều chỉnh tồn thực tế của một dòng về số đã đếm (sau kiểm kê), ghi sổ ADJUST. Trả về id dòng sổ,
     * hoặc null nếu không có chênh lệch. Khóa dòng tồn rồi kiểm tra lại phiên bản: nếu tồn đã đổi từ lúc chụp
     * thì từ chối thay vì ghi đè lên số liệu mới.
     */
    @Transactional
    public Long adjust(Long balanceId, long snapshotVersion, int countedQuantity, String referenceType, Long referenceId, String note) {
        InventoryBalance balance = lockBalance(balanceId);
        String problem = adjustmentProblem(balance, snapshotVersion, countedQuantity);
        if (problem != null) {
            throw new ConflictException(problem);
        }
        int delta = countedQuantity - balance.getOnHand();
        if (delta == 0) {
            return null;
        }
        if (delta > 0) {
            balance.increase(delta);
        } else {
            balance.decrease(-delta, false);
        }
        return writeLedger(TransactionType.ADJUST, balance, delta, referenceType, referenceId, note);
    }

    private String adjustmentProblem(InventoryBalance balance, long snapshotVersion, int countedQuantity) {
        if (!Long.valueOf(snapshotVersion).equals(balance.getVersion())) {
            return "Tồn kho đã thay đổi sau khi bắt đầu kiểm kê, cần đếm lại";
        }
        if (countedQuantity < balance.getReserved()) {
            return "Số đếm (" + countedQuantity + ") nhỏ hơn số đang giữ chỗ (" + balance.getReserved() + ")";
        }
        if (countedQuantity != balance.getOnHand() && "SERIAL".equals(productService.getVariant(balance.getVariantId()).trackingType())) {
            return "Hàng theo dõi serial không điều chỉnh số lượng tự động, cần xử lý từng thiết bị";
        }
        return null;
    }

    // ==================== HÀM DÙNG CHUNG ====================

    /** Tìm dòng tồn đúng tổ hợp và KHÓA nó; chưa có thì tạo mới với tồn bằng 0. */
    private InventoryBalance findOrCreateLockedBalance(Long warehouseId, Long locationId, Long variantId, InventoryBatch batch) {
        Optional<Long> existingId = balanceRepository.findIdByKey(warehouseId, locationId, variantId, batch.getId());
        if (existingId.isPresent()) {
            return lockBalance(existingId.get());
        }
        return balanceRepository.save(new InventoryBalance(warehouseId, locationId, variantId, batch, 0));
    }

    /** Vị trí nhận hàng phải thuộc đúng kho, còn hoạt động; vị trí hàng lỗi chỉ cho phép khi allowDefective. */
    private void checkTargetLocation(Long warehouseId, Long locationId, boolean allowDefective) {
        LocationResponse location = locationService.getById(locationId);
        if (!location.warehouseId().equals(warehouseId)) {
            throw new ConflictException("Vị trí " + location.code() + " không thuộc kho đã chọn");
        }
        if (!"ACTIVE".equals(location.status())) {
            throw new ConflictException("Vị trí " + location.code() + " đã ngừng hoạt động");
        }
        boolean defectiveLocation = "DEFECTIVE".equals(location.locationType());
        if (defectiveLocation && !allowDefective) {
            throw new ConflictException("Không nhập hàng tốt vào vị trí hàng lỗi");
        }
        if (!defectiveLocation && allowDefective) {
            throw new ConflictException("Hàng lỗi phải đặt ở vị trí hàng lỗi (DEFECTIVE)");
        }
    }

    /** Ghi một sự kiện vào vòng đời của thiết bị. */
    private void recordUnitEvent(DeviceUnit unit, TransactionType type, Long fromLocationId, Long toLocationId,
                                 String referenceType, Long referenceId) {
        eventRepository.save(new DeviceUnitEvent(unit, type, fromLocationId, toLocationId,
                referenceType, referenceId, CurrentUser.id().orElse(null)));
    }

    // ==================== HÀM HỖ TRỢ ====================

    /** Đọc và KHÓA dòng tồn. Phải gọi bên trong transaction. */
    private InventoryBalance lockBalance(Long balanceId) {
        return balanceRepository.findByIdForUpdate(balanceId)
                .orElseThrow(() -> new NotFoundException("Dòng tồn kho", balanceId));
    }

    /** Ghi một dòng vào sổ giao dịch. quantityDelta có dấu: dương là tăng, âm là giảm. */
    private Long writeLedger(TransactionType type, InventoryBalance balance, int quantityDelta,
                             String referenceType, Long referenceId, String note) {
        InventoryBatch batch = balance.getBatch();
        return transactionRepository.save(new InventoryTransaction(type, balance.getWarehouseId(), balance.getLocationId(),
                balance.getVariantId(), batch.getId(), quantityDelta, batch.getUnitCost(),
                referenceType, referenceId, note, CurrentUser.id().orElse(null))).getId();
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
    private void moveSerialsOut(InventoryBalance balance, List<String> serials, DeviceUnitStatus newStatus, StockOutCommand cmd) {
        List<DeviceUnit> units = unitRepository.findByBatchIdAndLocationIdAndSerialNumberIn(
                balance.getBatch().getId(), balance.getLocationId(), serials);
        if (units.size() != serials.size()) {
            throw new ConflictException("Có serial không thuộc lô hoặc vị trí này, hoặc bị nhập trùng trong danh sách");
        }
        DeviceUnitStatus requiredStatus = cmd.fromReserved() ? DeviceUnitStatus.RESERVED : DeviceUnitStatus.IN_STOCK;
        for (DeviceUnit unit : units) {
            if (unit.getStatus() != requiredStatus) {
                throw new ConflictException("Serial " + unit.getSerialNumber() + " đang ở trạng thái "
                        + unit.getStatus() + ", không thể xuất");
            }
            Long fromLocation = unit.getLocationId();
            unit.leaveWarehouse(newStatus);
            if (newStatus == DeviceUnitStatus.SOLD) {
                unit.setWarrantyEndDate(warrantyEndDate(balance.getBatch())); // hạn bảo hành tính từ ngày xuất
            }
            recordUnitEvent(unit, cmd.type(), fromLocation, null, cmd.referenceType(), cmd.referenceId());
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

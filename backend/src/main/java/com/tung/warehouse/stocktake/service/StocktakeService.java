package com.tung.warehouse.stocktake.service;

import com.tung.warehouse.audit.service.AuditService;
import com.tung.warehouse.catalog.service.ProductService;
import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.common.security.CurrentUser;
import com.tung.warehouse.inventory.dto.BalanceInfo;
import com.tung.warehouse.inventory.dto.CountableBalance;
import com.tung.warehouse.inventory.service.InventoryService;
import com.tung.warehouse.stocktake.dto.CountEntry;
import com.tung.warehouse.stocktake.dto.CountRequest;
import com.tung.warehouse.stocktake.dto.StocktakeLineResponse;
import com.tung.warehouse.stocktake.dto.StocktakeRequest;
import com.tung.warehouse.stocktake.dto.StocktakeResponse;
import com.tung.warehouse.stocktake.entity.StockAdjustment;
import com.tung.warehouse.stocktake.entity.StocktakeLine;
import com.tung.warehouse.stocktake.entity.StocktakeScope;
import com.tung.warehouse.stocktake.entity.StocktakeStatus;
import com.tung.warehouse.stocktake.entity.StocktakeSession;
import com.tung.warehouse.stocktake.repository.StockAdjustmentRepository;
import com.tung.warehouse.stocktake.repository.StocktakeSessionRepository;
import com.tung.warehouse.warehouse.dto.WarehouseResponse;
import com.tung.warehouse.warehouse.service.WarehouseAccessService;
import com.tung.warehouse.warehouse.service.WarehouseService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ kiểm kê: UC6.1 Tạo đợt kiểm kê, UC6.2 Nhập kết quả đếm, UC6.3 Duyệt và áp dụng điều chỉnh.
 *
 * Cách hoạt động: lúc bắt đầu, hệ thống "chụp" số tồn và phiên bản của từng dòng tồn. Khi áp dụng, nếu phiên bản
 * hiện tại khác phiên bản đã chụp (tồn đã bị thay đổi trong lúc đếm) thì KHÔNG ghi đè, phải đếm lại.
 * Tồn chỉ được sửa thông qua giao dịch điều chỉnh ADJUST của InventoryService, không bao giờ sửa trực tiếp.
 */
@Service
public class StocktakeService {

    private static final String ENTITY_TYPE = "STOCKTAKE";

    private final StocktakeSessionRepository sessionRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final InventoryService inventoryService;
    private final ProductService productService;
    private final WarehouseService warehouseService;
    private final WarehouseAccessService accessService;
    private final AuditService auditService;

    public StocktakeService(StocktakeSessionRepository sessionRepository, StockAdjustmentRepository adjustmentRepository,
                            InventoryService inventoryService, ProductService productService,
                            WarehouseService warehouseService, WarehouseAccessService accessService,
                            AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.inventoryService = inventoryService;
        this.productService = productService;
        this.warehouseService = warehouseService;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    // ==================== UC6.1 TẠO VÀ BẮT ĐẦU ====================

    @Transactional
    public StocktakeResponse create(StocktakeRequest request) {
        accessService.assertCanAccess(request.warehouseId());
        WarehouseResponse warehouse = warehouseService.getById(request.warehouseId());
        if (!"ACTIVE".equals(warehouse.status())) {
            throw new ConflictException("Kho " + warehouse.code() + " đã ngừng hoạt động");
        }
        if (request.scope() == StocktakeScope.ZONE && request.zoneId() == null) {
            throw badRequest("ZONE_REQUIRED", "Kiểm kê theo khu vực phải chọn khu vực");
        }
        if (request.scope() == StocktakeScope.PRODUCT) {
            if (request.variantId() == null) {
                throw badRequest("VARIANT_REQUIRED", "Kiểm kê theo sản phẩm phải chọn biến thể");
            }
            productService.getVariant(request.variantId()); // NotFound nếu không có
        }

        String temporaryCode = "TMP-" + UUID.randomUUID().toString().substring(0, 20);
        StocktakeSession session = new StocktakeSession(temporaryCode, request.warehouseId(), request.scope(),
                request.scope() == StocktakeScope.ZONE ? request.zoneId() : null,
                request.scope() == StocktakeScope.PRODUCT ? request.variantId() : null);
        sessionRepository.save(session);
        session.assignCode("KK-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format("%06d", session.getId()));

        auditService.log("STOCKTAKE_CREATE", ENTITY_TYPE, session.getId(), session.getCode() + ": tạo đợt kiểm kê");
        return toResponse(session);
    }

    /** Bắt đầu đếm: chụp số hệ thống và phiên bản của từng dòng tồn trong phạm vi. */
    @Transactional
    public StocktakeResponse start(Long id) {
        StocktakeSession session = findAndLock(id);
        session.assertStatus(StocktakeStatus.DRAFT);
        session.start(takeSnapshot(session));
        sessionRepository.flush(); // ghi xuống database để các dòng mới có id trước khi trả về

        auditService.log("STOCKTAKE_START", ENTITY_TYPE, session.getId(), session.getCode() + ": bắt đầu đếm, " + session.getLines().size() + " dòng");
        return toResponse(session);
    }

    // ==================== UC6.2 NHẬP KẾT QUẢ ĐẾM ====================

    @Transactional
    public StocktakeResponse recordCounts(Long id, CountRequest request) {
        StocktakeSession session = findAndLock(id);
        session.assertStatus(StocktakeStatus.COUNTING);

        Map<Long, StocktakeLine> linesById = new HashMap<>();
        session.getLines().forEach(line -> linesById.put(line.getId(), line));
        for (CountEntry entry : request.entries()) {
            StocktakeLine line = linesById.get(entry.lineId());
            if (line == null) {
                throw badRequest("UNKNOWN_LINE", "Dòng " + entry.lineId() + " không thuộc đợt kiểm kê này");
            }
            line.recordCount(entry.countedQuantity(), entry.reason());
        }
        return toResponse(session);
    }

    /** Nộp kết quả đếm để chờ duyệt. */
    @Transactional
    public StocktakeResponse submitCounts(Long id) {
        StocktakeSession session = findAndLock(id);
        session.submitCounts(currentUserId());

        auditService.log("STOCKTAKE_SUBMIT", ENTITY_TYPE, session.getId(), session.getCode() + ": đã nộp kết quả đếm");
        return toResponse(session);
    }

    // ==================== UC6.3 DUYỆT VÀ ÁP DỤNG ====================

    /**
     * Duyệt và áp dụng: với mỗi dòng có chênh lệch, đặt tồn thực tế bằng số đếm bằng một giao dịch ADJUST và lưu bản ghi
     * điều chỉnh. Làm tất cả hoặc không làm gì: có bất kỳ dòng nào không áp dụng được thì từ chối cả đợt (409).
     * Người duyệt không được là người đã đếm.
     */
    @Transactional
    public StocktakeResponse apply(Long id) {
        StocktakeSession session = findAndLock(id);
        session.assertStatus(StocktakeStatus.PENDING_APPROVAL);
        Long approverId = currentUserId();
        if (approverId.equals(session.getCountedBy())) {
            throw new ConflictException("Người duyệt không được là người đã đếm");
        }

        // Bước 1: kiểm tra mọi dòng trước khi sửa bất cứ thứ gì
        List<String> problems = new ArrayList<>();
        for (StocktakeLine line : session.getLines()) {
            if (line.getDifference() == 0) {
                continue;
            }
            String problem = inventoryService.adjustmentProblem(line.getBalanceId(), line.getSnapshotVersion(), line.getCountedQuantity());
            if (problem != null) {
                problems.add("dòng " + line.getId() + ": " + problem);
            }
        }
        if (!problems.isEmpty()) {
            throw new ConflictException("Không thể áp dụng, " + String.join("; ", problems) + ". Hãy chụp lại và đếm lại (restart)");
        }

        // Bước 2: áp dụng. adjust() khóa dòng tồn và kiểm tra lại một lần nữa trước khi ghi
        for (StocktakeLine line : session.getLines()) {
            if (line.getDifference() == 0) {
                continue;
            }
            Long transactionId = inventoryService.adjust(line.getBalanceId(), line.getSnapshotVersion(), line.getCountedQuantity(),
                    ENTITY_TYPE, session.getId(), "Kiểm kê " + session.getCode() + ": " + line.getReason());
            adjustmentRepository.save(new StockAdjustment(line.getId(), transactionId, line.getDifference(), line.getReason(), approverId));
        }
        session.markApplied(approverId);

        auditService.log("STOCKTAKE_APPLY", ENTITY_TYPE, session.getId(), session.getCode() + ": đã áp dụng điều chỉnh tồn");
        return toResponse(session);
    }

    /** Chụp lại số hệ thống để đếm lại, khi tồn đã thay đổi trong lúc đếm. Kết quả đếm cũ bị xóa. */
    @Transactional
    public StocktakeResponse restart(Long id) {
        StocktakeSession session = findAndLock(id);
        session.restart(takeSnapshot(session));
        sessionRepository.flush();

        auditService.log("STOCKTAKE_RESTART", ENTITY_TYPE, session.getId(), session.getCode() + ": chụp lại để đếm lại");
        return toResponse(session);
    }

    // ==================== TRA CỨU ====================

    @Transactional(readOnly = true)
    public StocktakeResponse getById(Long id) {
        StocktakeSession session = sessionRepository.findById(id).orElseThrow(() -> new NotFoundException("Đợt kiểm kê", id));
        accessService.assertCanAccess(session.getWarehouseId());
        return toResponse(session);
    }

    /** Danh sách các đợt kiểm kê trong các kho được phép xem (Admin xem tất cả). Không kèm các dòng. */
    @Transactional(readOnly = true)
    public List<StocktakeResponse> findVisible() {
        List<StocktakeSession> sessions;
        if (CurrentUser.isAdmin()) {
            sessions = sessionRepository.findAllByOrderByIdDesc();
        } else if (CurrentUser.warehouseIds().isEmpty()) {
            sessions = List.of();
        } else {
            sessions = sessionRepository.findByWarehouseIdInOrderByIdDesc(CurrentUser.warehouseIds());
        }
        return sessions.stream().map(s -> new StocktakeResponse(s.getId(), s.getCode(), s.getWarehouseId(), s.getScope().name(),
                s.getStatus().name(), s.getCountedBy(), s.getApprovedBy(), s.getStartedAt(), s.getAppliedAt(), s.getCreatedAt(), List.of())).toList();
    }

    // ==================== HÀM HỖ TRỢ ====================

    private StocktakeSession findAndLock(Long id) {
        StocktakeSession session = sessionRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Đợt kiểm kê", id));
        accessService.assertCanAccess(session.getWarehouseId());
        return session;
    }

    /** Lấy các dòng tồn trong phạm vi và chụp số liệu hiện tại thành các dòng kiểm kê. */
    private List<StocktakeLine> takeSnapshot(StocktakeSession session) {
        List<CountableBalance> balances = inventoryService.findCountableBalances(
                session.getWarehouseId(), session.getScopeZoneId(), session.getScopeVariantId());
        List<StocktakeLine> lines = new ArrayList<>();
        for (CountableBalance balance : balances) {
            lines.add(new StocktakeLine(session, balance.balanceId(), balance.onHand(), balance.version()));
        }
        return lines;
    }

    private Long currentUserId() {
        return CurrentUser.id().orElseThrow(() -> badRequest("NO_USER", "Không xác định được người thực hiện"));
    }

    private BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    private StocktakeResponse toResponse(StocktakeSession session) {
        List<Long> balanceIds = session.getLines().stream().map(StocktakeLine::getBalanceId).toList();
        Map<Long, BalanceInfo> infos = inventoryService.describeBalances(balanceIds);

        List<StocktakeLineResponse> lines = new ArrayList<>();
        for (StocktakeLine line : session.getLines()) {
            BalanceInfo info = infos.get(line.getBalanceId());
            lines.add(new StocktakeLineResponse(line.getId(), line.getBalanceId(),
                    info == null ? null : info.sku(), info == null ? null : info.productName(),
                    info == null ? null : info.locationCode(), info == null ? null : info.batchCode(),
                    line.getSystemQuantity(), line.getCountedQuantity(), line.getDifference(), line.getReason()));
        }
        return new StocktakeResponse(session.getId(), session.getCode(), session.getWarehouseId(), session.getScope().name(),
                session.getStatus().name(), session.getCountedBy(), session.getApprovedBy(), session.getStartedAt(),
                session.getAppliedAt(), session.getCreatedAt(), lines);
    }
}

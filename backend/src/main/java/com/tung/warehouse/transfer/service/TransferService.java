package com.tung.warehouse.transfer.service;

import com.tung.warehouse.audit.service.AuditService;
import com.tung.warehouse.catalog.dto.VariantResponse;
import com.tung.warehouse.catalog.service.ProductService;
import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.common.exception.WarehouseAccessDeniedException;
import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.common.security.CurrentUser;
import com.tung.warehouse.common.util.PagingUtil;
import com.tung.warehouse.inventory.dto.IssuedPiece;
import com.tung.warehouse.inventory.entity.TransactionType;
import com.tung.warehouse.inventory.service.InventoryService;
import com.tung.warehouse.transfer.dto.ReceiveLineRequest;
import com.tung.warehouse.transfer.dto.ReceiveTransferRequest;
import com.tung.warehouse.transfer.dto.TransferLineRequest;
import com.tung.warehouse.transfer.dto.TransferLineResponse;
import com.tung.warehouse.transfer.dto.TransferRequest;
import com.tung.warehouse.transfer.dto.TransferResponse;
import com.tung.warehouse.transfer.dto.TransferSummaryResponse;
import com.tung.warehouse.transfer.entity.TransferOrder;
import com.tung.warehouse.transfer.entity.TransferOrderLine;
import com.tung.warehouse.transfer.entity.TransferStatus;
import com.tung.warehouse.transfer.entity.TransferUnitItem;
import com.tung.warehouse.transfer.repository.TransferOrderRepository;
import com.tung.warehouse.transfer.repository.TransferSpecs;
import com.tung.warehouse.warehouse.dto.WarehouseResponse;
import com.tung.warehouse.warehouse.service.WarehouseAccessService;
import com.tung.warehouse.warehouse.service.WarehouseService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ điều chuyển kho: UC5.1 Tạo yêu cầu, UC5.2 Duyệt, UC5.3 Xuất kho nguồn, UC5.4 Nhận hàng kho đích, UC5.5 Tra cứu.
 *
 * Quy tắc chính: hàng đang vận chuyển KHÔNG nằm trong tồn của kho nguồn lẫn kho đích.
 * Tồn kho nguồn giảm lúc xuất (ship); tồn kho đích chỉ tăng lúc nhận (receive), giữ nguyên lô gốc.
 */
@Service
public class TransferService {

    private static final String ENTITY_TYPE = "TRANSFER_ORDER";

    private final TransferOrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final ProductService productService;
    private final WarehouseService warehouseService;
    private final WarehouseAccessService accessService;
    private final AuditService auditService;

    public TransferService(TransferOrderRepository orderRepository, InventoryService inventoryService,
                           ProductService productService, WarehouseService warehouseService,
                           WarehouseAccessService accessService, AuditService auditService) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.productService = productService;
        this.warehouseService = warehouseService;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    // ==================== UC5.1 TẠO, SỬA, NỘP, HỦY ====================

    @Transactional
    public TransferResponse create(TransferRequest request) {
        checkWarehouses(request.sourceWarehouseId(), request.destWarehouseId());
        accessService.assertCanAccess(request.sourceWarehouseId()); // chỉ người làm ở kho nguồn mới tạo phiếu

        String temporaryCode = "TMP-" + UUID.randomUUID().toString().substring(0, 20);
        TransferOrder order = new TransferOrder(temporaryCode, request.sourceWarehouseId(), request.destWarehouseId(), request.note());
        order.updateContent(request.sourceWarehouseId(), request.destWarehouseId(), request.note(), buildLines(order, request));
        orderRepository.save(order);
        order.assignCode(formatCode(order.getId()));

        auditService.log("TRANSFER_CREATE", ENTITY_TYPE, order.getId(),
                order.getCode() + ": " + order.getLines().size() + " dòng, " + order.getTotalQuantity() + " sản phẩm");
        return toResponse(order);
    }

    @Transactional
    public TransferResponse update(Long id, TransferRequest request) {
        TransferOrder order = findAndLock(id);
        order.assertStatus(TransferStatus.DRAFT);
        accessService.assertCanAccess(order.getSourceWarehouseId());
        checkWarehouses(request.sourceWarehouseId(), request.destWarehouseId());
        accessService.assertCanAccess(request.sourceWarehouseId());

        order.updateContent(request.sourceWarehouseId(), request.destWarehouseId(), request.note(), buildLines(order, request));
        orderRepository.flush();
        auditService.log("TRANSFER_UPDATE", ENTITY_TYPE, order.getId(), order.getCode() + ": sửa phiếu nháp");
        return toResponse(order);
    }

    @Transactional
    public TransferResponse submit(Long id) {
        TransferOrder order = findAndLock(id);
        accessService.assertCanAccess(order.getSourceWarehouseId());
        order.submit();
        auditService.log("TRANSFER_SUBMIT", ENTITY_TYPE, order.getId(), order.getCode() + ": đã nộp, chờ duyệt");
        return toResponse(order);
    }

    @Transactional
    public TransferResponse cancel(Long id) {
        TransferOrder order = findAndLock(id);
        accessService.assertCanAccess(order.getSourceWarehouseId());
        order.cancel();
        auditService.log("TRANSFER_CANCEL", ENTITY_TYPE, order.getId(), order.getCode() + ": đã hủy");
        return toResponse(order);
    }

    // ==================== UC5.2 DUYỆT ====================

    @Transactional
    public TransferResponse approve(Long id) {
        TransferOrder order = findAndLock(id);
        accessService.assertCanAccess(order.getSourceWarehouseId());
        order.approve(currentUserId());
        auditService.log("TRANSFER_APPROVE", ENTITY_TYPE, order.getId(), order.getCode() + ": đã duyệt");
        return toResponse(order);
    }

    @Transactional
    public TransferResponse reject(Long id, String reason) {
        TransferOrder order = findAndLock(id);
        accessService.assertCanAccess(order.getSourceWarehouseId());
        order.reject(currentUserId(), reason.trim());
        auditService.log("TRANSFER_REJECT", ENTITY_TYPE, order.getId(), order.getCode() + ": từ chối, lý do: " + reason.trim());
        return toResponse(order);
    }

    // ==================== UC5.3 XUẤT KHO NGUỒN ====================

    /**
     * Xuất hàng khỏi kho nguồn theo FIFO: trừ tồn kho nguồn, ghi sổ TRANSFER_OUT, hàng serial chuyển sang IN_TRANSIT.
     * Nếu một dòng phải lấy từ nhiều lô thì dòng được tách thành nhiều dòng, mỗi dòng đúng một lô (giữ lô gốc để truy vết giá vốn).
     * Thiếu hàng ở dòng nào thì hoàn tác toàn bộ, phiếu vẫn ở trạng thái APPROVED.
     */
    @Transactional
    public TransferResponse ship(Long id) {
        TransferOrder order = findAndLock(id);
        accessService.assertCanAccess(order.getSourceWarehouseId());
        order.assertStatus(TransferStatus.APPROVED);

        // Xử lý theo mã biến thể tăng dần: thứ tự cố định để tránh deadlock giữa các phiếu
        List<TransferOrderLine> originalLines = new ArrayList<>(order.getLines());
        originalLines.sort(Comparator.comparing(TransferOrderLine::getVariantId));

        for (TransferOrderLine line : originalLines) {
            List<IssuedPiece> pieces = inventoryService.issueFifo(order.getSourceWarehouseId(), line.getVariantId(),
                    line.getQuantity(), TransactionType.TRANSFER_OUT, ENTITY_TYPE, order.getId(), "Xuất điều chuyển " + order.getCode());

            // Gom các mảnh theo lô: mỗi lô một dòng
            Map<Long, List<IssuedPiece>> byBatch = new LinkedHashMap<>();
            for (IssuedPiece piece : pieces) {
                byBatch.computeIfAbsent(piece.batchId(), key -> new ArrayList<>()).add(piece);
            }
            boolean first = true;
            for (Map.Entry<Long, List<IssuedPiece>> entry : byBatch.entrySet()) {
                int quantity = entry.getValue().stream().mapToInt(IssuedPiece::quantity).sum();
                TransferOrderLine target = line; // lô đầu tiên dùng lại dòng gốc, các lô sau tạo dòng mới
                if (first) {
                    line.assignBatch(entry.getKey(), quantity);
                } else {
                    target = new TransferOrderLine(order, line.getVariantId(), entry.getKey(), quantity);
                    order.addLine(target);
                }
                for (IssuedPiece piece : entry.getValue()) {
                    if (piece.deviceUnitId() != null) {
                        target.addUnitItem(new TransferUnitItem(target, piece.deviceUnitId(), piece.serialNumber()));
                    }
                }
                first = false;
            }
        }
        order.markShipped();
        orderRepository.flush(); // ghi xuống database để các dòng mới tách ra có id trước khi trả về

        auditService.log("TRANSFER_SHIP", ENTITY_TYPE, order.getId(), order.getCode() + ": đã xuất khỏi kho nguồn");
        return toResponse(order);
    }

    // ==================== UC5.4 NHẬN HÀNG TẠI KHO ĐÍCH ====================

    /**
     * Nhận hàng ở kho đích. Phải gửi kết quả cho TẤT CẢ các dòng. Dòng nhận đủ thì tính đủ; nhận thiếu thì phiếu thành
     * PARTIALLY_RECEIVED. Chỉ nhận được một lần (nhận lần hai bị từ chối vì phiếu không còn IN_TRANSIT).
     */
    @Transactional
    public TransferResponse receive(Long id, ReceiveTransferRequest request) {
        TransferOrder order = findAndLock(id);
        accessService.assertCanAccess(order.getDestWarehouseId());
        order.assertStatus(TransferStatus.IN_TRANSIT);

        Map<Long, ReceiveLineRequest> results = new HashMap<>();
        for (ReceiveLineRequest lineResult : request.lines()) {
            results.put(lineResult.lineId(), lineResult);
        }
        Set<Long> lineIds = new HashSet<>();
        order.getLines().forEach(line -> lineIds.add(line.getId()));
        if (!lineIds.equals(results.keySet())) {
            throw badRequest("RECEIVE_LINES_MISMATCH", "Phải gửi kết quả nhận cho đúng tất cả các dòng của phiếu");
        }

        boolean complete = true;
        for (TransferOrderLine line : order.getLines()) {
            ReceiveLineRequest result = results.get(line.getId());
            int received = result.receivedQuantity();
            if (received > line.getQuantity()) {
                throw badRequest("RECEIVED_TOO_MANY", "Dòng " + line.getId() + ": nhận " + received + " nhiều hơn số đã xuất " + line.getQuantity());
            }
            if (received > 0) {
                if (result.destLocationId() == null) {
                    throw badRequest("LOCATION_REQUIRED", "Dòng " + line.getId() + ": phải chọn vị trí lưu ở kho đích");
                }
                List<String> serials = result.serials() == null ? List.of() : result.serials().stream().map(String::trim).toList();
                if (!line.getSerialNumbers().containsAll(serials)) {
                    throw new ConflictException("Dòng " + line.getId() + ": có serial không nằm trong danh sách đã xuất");
                }
                inventoryService.receiveTransfer(order.getDestWarehouseId(), result.destLocationId(), line.getVariantId(),
                        line.getBatchId(), received, serials, ENTITY_TYPE, order.getId(), "Nhận điều chuyển " + order.getCode());
                line.getUnitItems().stream().filter(item -> serials.contains(item.getSerialNumber())).forEach(TransferUnitItem::markReceived);
            }
            line.markReceived(received, received > 0 ? result.destLocationId() : null);
            if (received < line.getQuantity()) {
                complete = false;
            }
        }
        order.markReceived(complete);

        auditService.log("TRANSFER_RECEIVE", ENTITY_TYPE, order.getId(),
                order.getCode() + (complete ? ": nhận đủ" : ": nhận thiếu, cần xử lý chênh lệch"));
        return toResponse(order);
    }

    // ==================== UC5.5 TRA CỨU ====================

    @Transactional(readOnly = true)
    public TransferResponse getById(Long id) {
        TransferOrder order = orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Phiếu điều chuyển", id));
        if (!accessService.canAccess(order.getSourceWarehouseId()) && !accessService.canAccess(order.getDestWarehouseId())) {
            throw new WarehouseAccessDeniedException();
        }
        return toResponse(order);
    }

    /** Tìm kiếm với các điều kiện tùy chọn, ghép bằng where(...).and(...). Điều kiện nào rỗng (null) thì tự bỏ qua. */
    @Transactional(readOnly = true)
    public PageResponse<TransferSummaryResponse> search(String keyword, TransferStatus status, Long warehouseId, int page, int size) {
        List<Long> restrictTo = restrictTo(warehouseId);
        Specification<TransferOrder> spec = Specification.where(TransferSpecs.codeContains(keyword))
                .and(TransferSpecs.hasStatus(status))
                .and(TransferSpecs.involvesWarehouse(warehouseId))
                .and(TransferSpecs.involvesAnyOf(restrictTo));

        Page<TransferOrder> result = orderRepository.findAll(spec, PagingUtil.of(page, size));
        Map<Long, String> codes = new HashMap<>();
        return PageResponse.from(result.map(order -> new TransferSummaryResponse(order.getId(), order.getCode(),
                codes.computeIfAbsent(order.getSourceWarehouseId(), key -> warehouseService.lookup(key).code()),
                codes.computeIfAbsent(order.getDestWarehouseId(), key -> warehouseService.lookup(key).code()),
                order.getStatus().name(), order.getLines().size(), order.getTotalQuantity(), order.getCreatedAt())));
    }

    // ==================== HÀM HỖ TRỢ ====================

    private TransferOrder findAndLock(Long id) {
        return orderRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Phiếu điều chuyển", id));
    }

    private Long currentUserId() {
        return CurrentUser.id().orElseThrow(() -> badRequest("NO_USER", "Không xác định được người thực hiện"));
    }

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

    /** Hai kho phải khác nhau và đều đang hoạt động (kiểm tra bằng lookup vì người tạo có thể không được gán kho đích). */
    private void checkWarehouses(Long sourceId, Long destId) {
        if (sourceId.equals(destId)) {
            throw badRequest("SAME_WAREHOUSE", "Kho nguồn và kho đích phải khác nhau");
        }
        for (Long warehouseId : List.of(sourceId, destId)) {
            WarehouseResponse warehouse = warehouseService.lookup(warehouseId);
            if (!"ACTIVE".equals(warehouse.status())) {
                throw new ConflictException("Kho " + warehouse.code() + " đã ngừng hoạt động");
            }
        }
    }

    private List<TransferOrderLine> buildLines(TransferOrder order, TransferRequest request) {
        List<TransferOrderLine> result = new ArrayList<>();
        Set<Long> seenVariants = new HashSet<>();
        for (TransferLineRequest lineRequest : request.lines()) {
            VariantResponse variant = productService.getVariant(lineRequest.variantId());
            if (!"ACTIVE".equals(variant.status())) {
                throw new ConflictException("Biến thể đã ngừng kinh doanh: " + variant.sku());
            }
            if (!seenVariants.add(variant.id())) {
                throw new ConflictException("Biến thể " + variant.sku() + " xuất hiện nhiều lần trong phiếu, hãy gộp thành một dòng");
            }
            result.add(new TransferOrderLine(order, variant.id(), null, lineRequest.quantity()));
        }
        return result;
    }

    /** Mã phiếu dạng DC-20261105-000012: ngày tạo (UTC) và id phiếu. */
    private String formatCode(Long id) {
        return "DC-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", id);
    }

    private BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    private TransferResponse toResponse(TransferOrder order) {
        Map<Long, VariantResponse> variants = new HashMap<>();
        List<TransferLineResponse> lines = new ArrayList<>();
        for (TransferOrderLine line : order.getLines()) {
            VariantResponse variant = variants.computeIfAbsent(line.getVariantId(), productService::getVariant);
            lines.add(new TransferLineResponse(line.getId(), line.getVariantId(), variant.sku(), variant.trackingType(),
                    line.getBatchId(), line.getQuantity(), line.getReceivedQuantity(), line.getDestLocationId(),
                    line.getSerialNumbers(), line.getReceivedSerialNumbers()));
        }
        return new TransferResponse(order.getId(), order.getCode(),
                order.getSourceWarehouseId(), warehouseService.lookup(order.getSourceWarehouseId()).code(),
                order.getDestWarehouseId(), warehouseService.lookup(order.getDestWarehouseId()).code(),
                order.getStatus().name(), order.getNote(), order.getRejectReason(), order.getApprovedBy(),
                order.getShippedAt(), order.getReceivedAt(), order.getCreatedAt(), order.getTotalQuantity(), lines);
    }
}

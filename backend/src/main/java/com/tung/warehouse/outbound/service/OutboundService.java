package com.tung.warehouse.outbound.service;

import com.tung.warehouse.audit.service.AuditService;
import com.tung.warehouse.catalog.dto.VariantResponse;
import com.tung.warehouse.catalog.service.ProductService;
import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.common.security.CurrentUser;
import com.tung.warehouse.common.util.PagingUtil;
import com.tung.warehouse.inventory.dto.IssuedPiece;
import com.tung.warehouse.inventory.service.InventoryService;
import com.tung.warehouse.outbound.dto.OutboundAllocationResponse;
import com.tung.warehouse.outbound.dto.OutboundLineRequest;
import com.tung.warehouse.outbound.dto.OutboundLineResponse;
import com.tung.warehouse.outbound.dto.OutboundRequest;
import com.tung.warehouse.outbound.dto.OutboundResponse;
import com.tung.warehouse.outbound.dto.OutboundSummaryResponse;
import com.tung.warehouse.outbound.entity.OutboundAllocation;
import com.tung.warehouse.outbound.entity.OutboundOrder;
import com.tung.warehouse.outbound.entity.OutboundOrderLine;
import com.tung.warehouse.outbound.entity.OutboundPurpose;
import com.tung.warehouse.outbound.entity.OutboundStatus;
import com.tung.warehouse.outbound.repository.OutboundOrderRepository;
import com.tung.warehouse.outbound.repository.OutboundSpecs;
import com.tung.warehouse.partner.dto.PartnerResponse;
import com.tung.warehouse.partner.service.CustomerService;
import com.tung.warehouse.warehouse.dto.WarehouseResponse;
import com.tung.warehouse.warehouse.service.LocationService;
import com.tung.warehouse.warehouse.service.WarehouseAccessService;
import com.tung.warehouse.warehouse.service.WarehouseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
 * Nghiệp vụ xuất kho: UC4.1 Tạo phiếu, UC4.2 Giữ chỗ (khi nộp phiếu), UC4.3 Xác nhận xuất kho,
 * UC4.4 Hủy phiếu, UC4.5 Tra cứu.
 *
 * Việc động đến tồn kho (giữ chỗ FIFO, trừ tồn, ghi sổ) đều do InventoryService làm;
 * lớp này chỉ lo phiếu, trạng thái, phân quyền, nhật ký.
 *
 * Thứ tự khóa luôn cố định để tránh deadlock: khóa phiếu trước, rồi xử lý các dòng hàng theo mã biến thể tăng dần,
 * trong mỗi biến thể InventoryService khóa các dòng tồn theo thứ tự FIFO.
 */
@Service
public class OutboundService {

    private static final String ENTITY_TYPE = "OUTBOUND_ORDER";

    private final OutboundOrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final ProductService productService;
    private final LocationService locationService;
    private final CustomerService customerService;
    private final WarehouseService warehouseService;
    private final WarehouseAccessService accessService;
    private final AuditService auditService;

    public OutboundService(OutboundOrderRepository orderRepository, InventoryService inventoryService,
                           ProductService productService, LocationService locationService,
                           CustomerService customerService, WarehouseService warehouseService,
                           WarehouseAccessService accessService, AuditService auditService) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.productService = productService;
        this.locationService = locationService;
        this.customerService = customerService;
        this.warehouseService = warehouseService;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    // ==================== UC4.1 TẠO VÀ SỬA PHIẾU NHÁP ====================

    @Transactional
    public OutboundResponse create(OutboundRequest request) {
        checkWarehouseUsable(request.warehouseId());
        Long customerId = checkCustomer(request.purpose(), request.customerId());

        String temporaryCode = "TMP-" + UUID.randomUUID().toString().substring(0, 20);
        OutboundOrder order = new OutboundOrder(temporaryCode, request.warehouseId(), customerId, request.purpose(), request.note());
        order.replaceLines(buildLines(order, request));
        orderRepository.save(order);
        order.assignCode(formatCode(order.getId()));

        auditService.log("OUTBOUND_CREATE", ENTITY_TYPE, order.getId(),
                order.getCode() + ": " + order.getLines().size() + " dòng, " + order.getTotalQuantity() + " sản phẩm");
        return toResponse(order);
    }

    @Transactional
    public OutboundResponse update(Long id, OutboundRequest request) {
        OutboundOrder order = findForUserAndLock(id);
        order.assertStatus(OutboundStatus.DRAFT);
        checkWarehouseUsable(request.warehouseId());
        Long customerId = checkCustomer(request.purpose(), request.customerId());

        order.updateHeader(request.warehouseId(), customerId, request.purpose(), request.note());
        order.replaceLines(buildLines(order, request));

        auditService.log("OUTBOUND_UPDATE", ENTITY_TYPE, order.getId(), order.getCode() + ": sửa phiếu nháp");
        return toResponse(order);
    }

    // ==================== UC4.2 GIỮ CHỖ TỒN KHO (khi nộp phiếu) ====================

    /**
     * Nộp phiếu: giữ chỗ tồn cho từng dòng theo FIFO rồi chuyển phiếu sang RESERVED.
     * Thiếu hàng ở dòng nào thì cả transaction hoàn tác: phiếu vẫn là DRAFT và không có giữ chỗ nào còn lại.
     */
    @Transactional
    public OutboundResponse submit(Long id) {
        OutboundOrder order = findForUserAndLock(id);
        order.assertStatus(OutboundStatus.DRAFT);

        for (OutboundOrderLine line : linesByVariant(order)) {
            inventoryService.reserveFifo(order.getWarehouseId(), line.getVariantId(), line.getQuantity(), line.getId());
        }
        order.markReserved();

        auditService.log("OUTBOUND_SUBMIT", ENTITY_TYPE, order.getId(), order.getCode() + ": đã giữ chỗ tồn");
        return toResponse(order);
    }

    // ==================== UC4.3 XÁC NHẬN XUẤT KHO ====================

    /**
     * Xác nhận xuất kho: với từng dòng, InventoryService trừ tồn từ các giữ chỗ (đúng các lô đã chọn theo FIFO),
     * ghi sổ OUTBOUND và chuyển serial sang SOLD; lớp này ghi lại phân bổ rồi đánh dấu phiếu hoàn tất.
     * Tất cả trong một transaction.
     */
    @Transactional
    public OutboundResponse confirm(Long id) {
        OutboundOrder order = findForUserAndLock(id);
        order.assertStatus(OutboundStatus.RESERVED);
        Long userId = CurrentUser.id().orElseThrow(() -> badRequest("NO_USER", "Không xác định được người xác nhận"));

        for (OutboundOrderLine line : linesByVariant(order)) {
            List<IssuedPiece> pieces = inventoryService.issueReservations(line.getId(), order.getId(), "Xuất kho " + order.getCode());
            for (IssuedPiece piece : pieces) {
                line.addAllocation(new OutboundAllocation(line, piece.batchId(), piece.batchCode(), piece.deviceUnitId(),
                        piece.serialNumber(), piece.locationId(), piece.quantity(), piece.unitCost(), piece.warrantyEndDate()));
            }
        }
        order.markCompleted(userId);

        auditService.log("OUTBOUND_CONFIRM", ENTITY_TYPE, order.getId(),
                order.getCode() + ": xuất kho " + order.getTotalQuantity() + " sản phẩm");
        return toResponse(order);
    }

    // ==================== UC4.4 HỦY PHIẾU ====================

    /** Hủy phiếu chưa xuất. Nếu đã giữ chỗ thì trả giữ chỗ về phần khả dụng. */
    @Transactional
    public OutboundResponse cancel(Long id) {
        OutboundOrder order = findForUserAndLock(id);
        order.assertStatus(OutboundStatus.DRAFT, OutboundStatus.RESERVED);

        if (order.getStatus() == OutboundStatus.RESERVED) {
            for (OutboundOrderLine line : linesByVariant(order)) {
                inventoryService.releaseReservations(line.getId());
            }
        }
        order.cancel();

        auditService.log("OUTBOUND_CANCEL", ENTITY_TYPE, order.getId(), order.getCode() + ": đã hủy");
        return toResponse(order);
    }

    // ==================== UC4.5 TRA CỨU ====================

    @Transactional(readOnly = true)
    public OutboundResponse getById(Long id) {
        OutboundOrder order = orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Phiếu xuất", id));
        accessService.assertCanAccess(order.getWarehouseId());
        return toResponse(order);
    }

    /** Tìm kiếm với các điều kiện tùy chọn, ghép bằng where(...).and(...). Điều kiện nào rỗng (null) thì tự bỏ qua. */
    @Transactional(readOnly = true)
    public PageResponse<OutboundSummaryResponse> search(String keyword, OutboundStatus status, Long warehouseId,
                                                        int page, int size) {
        List<Long> restrictTo = restrictTo(warehouseId);
        Specification<OutboundOrder> spec = Specification.where(OutboundSpecs.codeContains(keyword))
                .and(OutboundSpecs.hasStatus(status))
                .and(OutboundSpecs.hasWarehouse(warehouseId))
                .and(OutboundSpecs.warehouseIn(restrictTo));

        Page<OutboundOrder> result = orderRepository.findAll(spec, PagingUtil.of(page, size));
        Map<Long, String> customerNames = new HashMap<>();
        Map<Long, String> warehouseCodes = new HashMap<>();
        return PageResponse.from(result.map(order -> toSummary(order, customerNames, warehouseCodes)));
    }

    // ==================== HÀM HỖ TRỢ ====================

    /** Lấy phiếu VÀ KHÓA dòng phiếu, rồi kiểm tra người dùng được vào kho của phiếu. */
    private OutboundOrder findForUserAndLock(Long id) {
        OutboundOrder order = orderRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Phiếu xuất", id));
        accessService.assertCanAccess(order.getWarehouseId());
        return order;
    }

    /** Các dòng hàng theo mã biến thể tăng dần: thứ tự cố định để các phiếu khác nhau khóa tồn cùng một thứ tự. */
    private List<OutboundOrderLine> linesByVariant(OutboundOrder order) {
        return order.getLines().stream().sorted(Comparator.comparing(OutboundOrderLine::getVariantId)).toList();
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

    private void checkWarehouseUsable(Long warehouseId) {
        accessService.assertCanAccess(warehouseId);
        WarehouseResponse warehouse = warehouseService.getById(warehouseId);
        if (!"ACTIVE".equals(warehouse.status())) {
            throw new ConflictException("Kho " + warehouse.code() + " đã ngừng hoạt động");
        }
    }

    /** Xuất bán phải có khách hàng đang hoạt động; xuất nội bộ thì không cần (bỏ qua nếu có gửi lên). */
    private Long checkCustomer(OutboundPurpose purpose, Long customerId) {
        if (purpose == OutboundPurpose.INTERNAL) {
            return null;
        }
        if (customerId == null) {
            throw badRequest("CUSTOMER_REQUIRED", "Xuất bán phải chọn khách hàng");
        }
        PartnerResponse customer = customerService.getById(customerId);
        if (!"ACTIVE".equals(customer.status())) {
            throw new ConflictException("Khách hàng " + customer.code() + " đã ngừng hoạt động");
        }
        return customerId;
    }

    /** Kiểm tra từng dòng rồi dựng entity: biến thể đang kinh doanh, mỗi biến thể chỉ một dòng, giá bán mặc định lấy từ giá niêm yết. */
    private List<OutboundOrderLine> buildLines(OutboundOrder order, OutboundRequest request) {
        List<OutboundOrderLine> result = new ArrayList<>();
        Set<Long> seenVariants = new HashSet<>();

        for (OutboundLineRequest lineRequest : request.lines()) {
            VariantResponse variant = productService.getVariant(lineRequest.variantId());
            if (!"ACTIVE".equals(variant.status())) {
                throw new ConflictException("Biến thể đã ngừng kinh doanh: " + variant.sku());
            }
            if (!seenVariants.add(variant.id())) {
                throw new ConflictException("Biến thể " + variant.sku() + " xuất hiện nhiều lần trong phiếu, hãy gộp thành một dòng");
            }
            BigDecimal unitPrice = lineRequest.unitPrice() == null ? variant.listPrice() : lineRequest.unitPrice();
            result.add(new OutboundOrderLine(order, variant.id(), lineRequest.quantity(), unitPrice));
        }
        return result;
    }

    /** Mã phiếu dạng XK-20261105-000012: ngày tạo (UTC) và id phiếu. */
    private String formatCode(Long id) {
        String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        return "XK-" + date + "-" + String.format("%06d", id);
    }

    private BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    // ---------- Đổi entity sang DTO ----------

    private OutboundResponse toResponse(OutboundOrder order) {
        Map<Long, VariantResponse> variants = new HashMap<>();
        Map<Long, String> locationCodes = new HashMap<>();

        List<OutboundLineResponse> lines = new ArrayList<>();
        for (OutboundOrderLine line : order.getLines()) {
            VariantResponse variant = variants.computeIfAbsent(line.getVariantId(), productService::getVariant);
            List<OutboundAllocationResponse> allocations = new ArrayList<>();
            for (OutboundAllocation a : line.getAllocations()) {
                String locationCode = locationCodes.computeIfAbsent(a.getLocationId(), key -> locationService.getById(key).code());
                allocations.add(new OutboundAllocationResponse(a.getBatchId(), a.getBatchCode(), a.getSerialNumber(),
                        a.getLocationId(), locationCode, a.getQuantity(), a.getUnitCost(), a.getWarrantyEndDate()));
            }
            lines.add(new OutboundLineResponse(line.getId(), line.getVariantId(), variant.sku(), variant.trackingType(),
                    line.getQuantity(), line.getUnitPrice(), line.getAmount(), line.getCostAmount(), allocations));
        }

        boolean completed = order.getStatus() == OutboundStatus.COMPLETED;
        BigDecimal totalCost = order.getTotalCost();
        BigDecimal grossProfit = completed ? order.getTotalAmount().subtract(totalCost) : null;
        String customerName = order.getCustomerId() == null ? null : customerService.getById(order.getCustomerId()).name();
        return new OutboundResponse(order.getId(), order.getCode(), order.getWarehouseId(), order.getCustomerId(),
                customerName, order.getPurpose().name(), order.getStatus().name(), order.getNote(),
                order.getConfirmedAt(), order.getConfirmedBy(), order.getCreatedAt(), order.getTotalQuantity(),
                order.getTotalAmount(), totalCost, grossProfit, lines);
    }

    private OutboundSummaryResponse toSummary(OutboundOrder order, Map<Long, String> customerNames, Map<Long, String> warehouseCodes) {
        String customerName = order.getCustomerId() == null ? null
                : customerNames.computeIfAbsent(order.getCustomerId(), key -> customerService.getById(key).name());
        String warehouseCode = warehouseCodes.computeIfAbsent(order.getWarehouseId(), key -> warehouseService.getById(key).code());
        return new OutboundSummaryResponse(order.getId(), order.getCode(), order.getWarehouseId(), warehouseCode,
                order.getCustomerId(), customerName, order.getPurpose().name(), order.getStatus().name(),
                order.getLines().size(), order.getTotalQuantity(), order.getTotalAmount(), order.getCreatedAt());
    }
}

package com.tung.warehouse.inbound.service;

import com.tung.warehouse.audit.service.AuditService;
import com.tung.warehouse.catalog.dto.VariantResponse;
import com.tung.warehouse.catalog.service.ProductService;
import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.common.security.CurrentUser;
import com.tung.warehouse.common.util.PagingUtil;
import com.tung.warehouse.inbound.dto.InboundLineRequest;
import com.tung.warehouse.inbound.dto.InboundLineResponse;
import com.tung.warehouse.inbound.dto.InboundRequest;
import com.tung.warehouse.inbound.dto.InboundResponse;
import com.tung.warehouse.inbound.dto.InboundSummaryResponse;
import com.tung.warehouse.inbound.entity.InboundOrder;
import com.tung.warehouse.inbound.entity.InboundOrderLine;
import com.tung.warehouse.inbound.entity.InboundReceipt;
import com.tung.warehouse.inbound.entity.InboundStatus;
import com.tung.warehouse.inbound.repository.InboundOrderRepository;
import com.tung.warehouse.inbound.repository.InboundReceiptRepository;
import com.tung.warehouse.inbound.repository.InboundSpecs;
import com.tung.warehouse.inventory.dto.ReceiveCommand;
import com.tung.warehouse.inventory.service.InventoryService;
import com.tung.warehouse.partner.dto.PartnerResponse;
import com.tung.warehouse.partner.service.SupplierService;
import com.tung.warehouse.warehouse.dto.LocationResponse;
import com.tung.warehouse.warehouse.dto.WarehouseResponse;
import com.tung.warehouse.warehouse.service.LocationService;
import com.tung.warehouse.warehouse.service.WarehouseAccessService;
import com.tung.warehouse.warehouse.service.WarehouseService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
 * Nghiệp vụ nhập kho: UC3.1 Tạo phiếu, UC3.2 Nộp phiếu, UC3.3 Xác nhận nhập kho, UC3.4 Hủy phiếu, UC3.5 Tra cứu.
 *
 * Điểm quan trọng nhất: tạo hoặc nộp phiếu KHÔNG làm tăng tồn. Chỉ hàm confirm mới gọi
 * InventoryService.receive để tạo lô và tăng tồn, và nó chạy trong MỘT transaction cùng việc đổi trạng thái phiếu.
 */
@Service
public class InboundService {

    private static final String ENTITY_TYPE = "INBOUND_ORDER";

    private final InboundOrderRepository orderRepository;
    private final InboundReceiptRepository receiptRepository;
    private final InventoryService inventoryService;
    private final ProductService productService;
    private final LocationService locationService;
    private final SupplierService supplierService;
    private final WarehouseService warehouseService;
    private final WarehouseAccessService accessService;
    private final AuditService auditService;

    public InboundService(InboundOrderRepository orderRepository, InboundReceiptRepository receiptRepository,
                          InventoryService inventoryService, ProductService productService,
                          LocationService locationService, SupplierService supplierService,
                          WarehouseService warehouseService, WarehouseAccessService accessService,
                          AuditService auditService) {
        this.orderRepository = orderRepository;
        this.receiptRepository = receiptRepository;
        this.inventoryService = inventoryService;
        this.productService = productService;
        this.locationService = locationService;
        this.supplierService = supplierService;
        this.warehouseService = warehouseService;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    // ==================== UC3.1 TẠO VÀ SỬA PHIẾU NHÁP ====================

    @Transactional
    public InboundResponse create(InboundRequest request) {
        checkWarehouseUsable(request.warehouseId());
        checkSupplierUsable(request.supplierId());

        // Mã phiếu dựa trên id nên chưa biết lúc này: lưu với mã tạm, có id rồi mới đặt mã thật
        String temporaryCode = "TMP-" + UUID.randomUUID().toString().substring(0, 20);
        InboundOrder order = new InboundOrder(temporaryCode, request.warehouseId(), request.supplierId(),
                request.expectedDate(), request.note());
        order.replaceLines(buildLines(order, request));
        orderRepository.save(order);
        order.assignCode(formatCode(order.getId()));

        auditService.log("INBOUND_CREATE", ENTITY_TYPE, order.getId(),
                order.getCode() + ": " + order.getLines().size() + " dòng, " + order.getTotalQuantity() + " sản phẩm");
        return toResponse(order);
    }

    @Transactional
    public InboundResponse update(Long id, InboundRequest request) {
        InboundOrder order = findForUser(id);
        order.assertStatus(InboundStatus.DRAFT); // báo lỗi trạng thái trước khi kiểm tra nội dung
        checkWarehouseUsable(request.warehouseId());
        checkSupplierUsable(request.supplierId());

        order.updateHeader(request.warehouseId(), request.supplierId(), request.expectedDate(), request.note());
        order.replaceLines(buildLines(order, request));

        auditService.log("INBOUND_UPDATE", ENTITY_TYPE, order.getId(), order.getCode() + ": sửa phiếu nháp");
        return toResponse(order);
    }

    // ==================== UC3.2 NỘP PHIẾU ====================

    @Transactional
    public InboundResponse submit(Long id) {
        InboundOrder order = findForUser(id);
        order.assertStatus(InboundStatus.DRAFT);

        // Phiếu nháp được thiếu serial; nộp phiếu thì hàng theo dõi serial phải đủ số serial
        for (InboundOrderLine line : order.getLines()) {
            VariantResponse variant = productService.getVariant(line.getVariantId());
            int entered = line.getSerialNumbers().size();
            if ("SERIAL".equals(variant.trackingType()) && entered != line.getQuantity()) {
                throw badRequest("SERIAL_COUNT_MISMATCH",
                        "Dòng " + variant.sku() + " cần " + line.getQuantity() + " serial, mới nhập " + entered);
            }
        }
        order.submit();

        auditService.log("INBOUND_SUBMIT", ENTITY_TYPE, order.getId(), order.getCode() + ": đã nộp");
        return toResponse(order);
    }

    // ==================== UC3.3 XÁC NHẬN NHẬP KHO ====================

    /**
     * Xác nhận nhập kho: với từng dòng, tạo lô, tạo serial, tăng tồn và ghi sổ giao dịch (việc này do
     * InventoryService.receive làm), rồi đánh dấu phiếu CONFIRMED và lưu biên bản nhận.
     * Mọi bước nằm trong một transaction: lỗi ở dòng nào (ví dụ serial trùng) thì toàn bộ được hoàn tác,
     * phiếu vẫn ở trạng thái SUBMITTED và không có lô hay tồn nào được tạo dở dang.
     */
    @Transactional
    public InboundResponse confirm(Long id) {
        // Khóa dòng phiếu: hai người cùng xác nhận thì người đến sau phải chờ, rồi thấy phiếu đã CONFIRMED
        InboundOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Phiếu nhập", id));
        accessService.assertCanAccess(order.getWarehouseId());
        order.assertStatus(InboundStatus.SUBMITTED);

        Long userId = CurrentUser.id()
                .orElseThrow(() -> badRequest("NO_USER", "Không xác định được người xác nhận"));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        for (InboundOrderLine line : order.getLines()) {
            inventoryService.receive(new ReceiveCommand(
                    order.getWarehouseId(), line.getLocationId(), line.getVariantId(), line.getBatchCode(),
                    order.getSupplierId(), now, line.getUnitCost(), line.getWarrantyMonths(), line.getQuantity(),
                    line.getSerialNumbers(), ENTITY_TYPE, order.getId(), "Nhập kho " + order.getCode(), line.getId()));
        }

        order.markConfirmed(userId);
        receiptRepository.save(new InboundReceipt(order.getId(), userId, now, null));

        auditService.log("INBOUND_CONFIRM", ENTITY_TYPE, order.getId(),
                order.getCode() + ": nhập kho " + order.getTotalQuantity() + " sản phẩm");
        return toResponse(order);
    }

    // ==================== UC3.4 HỦY PHIẾU ====================

    @Transactional
    public InboundResponse cancel(Long id) {
        InboundOrder order = findForUser(id);
        order.cancel(); // chỉ DRAFT hoặc SUBMITTED; phiếu đã xác nhận bị từ chối (409)

        auditService.log("INBOUND_CANCEL", ENTITY_TYPE, order.getId(), order.getCode() + ": đã hủy");
        return toResponse(order);
    }

    // ==================== UC3.5 TRA CỨU ====================

    @Transactional(readOnly = true)
    public InboundResponse getById(Long id) {
        return toResponse(findForUser(id));
    }

    /** Tìm kiếm với các điều kiện tùy chọn, ghép bằng where(...).and(...). Điều kiện nào rỗng (null) thì tự bỏ qua. */
    @Transactional(readOnly = true)
    public PageResponse<InboundSummaryResponse> search(String keyword, InboundStatus status, Long warehouseId,
                                                       int page, int size) {
        List<Long> restrictTo = restrictTo(warehouseId);
        Specification<InboundOrder> spec = Specification.where(InboundSpecs.codeContains(keyword))
                .and(InboundSpecs.hasStatus(status))
                .and(InboundSpecs.hasWarehouse(warehouseId))
                .and(InboundSpecs.warehouseIn(restrictTo));

        Page<InboundOrder> result = orderRepository.findAll(spec, PagingUtil.of(page, size));
        Map<Long, String> supplierNames = new HashMap<>();
        Map<Long, String> warehouseCodes = new HashMap<>();
        return PageResponse.from(result.map(order -> toSummary(order, supplierNames, warehouseCodes)));
    }

    // ==================== HÀM HỖ TRỢ ====================

    /** Lấy phiếu và kiểm tra người dùng được phép vào kho của phiếu. */
    private InboundOrder findForUser(Long id) {
        InboundOrder order = orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Phiếu nhập", id));
        accessService.assertCanAccess(order.getWarehouseId());
        return order;
    }

    /** Có chọn một kho thì phải được phép vào kho đó; Admin không giới hạn; người khác chỉ thấy các kho được gán. */
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

    private void checkSupplierUsable(Long supplierId) {
        PartnerResponse supplier = supplierService.getById(supplierId);
        if (!"ACTIVE".equals(supplier.status())) {
            throw new ConflictException("Nhà cung cấp " + supplier.code() + " đã ngừng hoạt động");
        }
    }

    /**
     * Kiểm tra từng dòng của yêu cầu rồi dựng các entity dòng hàng.
     * Quy tắc: biến thể phải đang kinh doanh; vị trí phải thuộc kho nhận, còn hoạt động, không phải vị trí hàng lỗi;
     * mã lô không lặp trong phiếu; serial không trùng nhau trong phiếu.
     */
    private List<InboundOrderLine> buildLines(InboundOrder order, InboundRequest request) {
        List<InboundOrderLine> result = new ArrayList<>();
        Set<String> batchKeys = new HashSet<>();
        Map<Long, Set<String>> serialsByVariant = new HashMap<>();

        for (InboundLineRequest lineRequest : request.lines()) {
            VariantResponse variant = productService.getVariant(lineRequest.variantId());
            if (!"ACTIVE".equals(variant.status())) {
                throw new ConflictException("Biến thể đã ngừng kinh doanh: " + variant.sku());
            }

            LocationResponse location = locationService.getById(lineRequest.locationId());
            if (!location.warehouseId().equals(request.warehouseId())) {
                throw new ConflictException("Vị trí " + location.code() + " không thuộc kho đã chọn");
            }
            if (!"ACTIVE".equals(location.status())) {
                throw new ConflictException("Vị trí " + location.code() + " đã ngừng hoạt động");
            }
            if ("DEFECTIVE".equals(location.locationType())) {
                throw new ConflictException("Không nhập hàng tốt vào vị trí hàng lỗi");
            }

            String batchCode = lineRequest.batchCode().trim();
            if (!batchKeys.add(variant.id() + "|" + batchCode.toUpperCase())) {
                throw new ConflictException("Mã lô " + batchCode + " bị lặp trong phiếu cho cùng một biến thể");
            }

            List<String> serials = lineRequest.serials() == null ? List.of()
                    : lineRequest.serials().stream().map(String::trim).toList();
            checkSerials(variant, lineRequest.quantity(), serials, serialsByVariant);

            int warrantyMonths = lineRequest.warrantyMonths() == null ? variant.warrantyMonths() : lineRequest.warrantyMonths();
            InboundOrderLine line = new InboundOrderLine(order, lineRequest.variantId(), lineRequest.locationId(),
                    lineRequest.quantity(), lineRequest.unitCost(), batchCode, warrantyMonths);
            serials.forEach(line::addSerial);
            result.add(line);
        }
        return result;
    }

    private void checkSerials(VariantResponse variant, int quantity, List<String> serials,
                              Map<Long, Set<String>> serialsByVariant) {
        if (!"SERIAL".equals(variant.trackingType())) {
            if (!serials.isEmpty()) {
                throw badRequest("SERIAL_NOT_ALLOWED", "Hàng theo dõi theo số lượng (" + variant.sku() + ") không nhập serial");
            }
            return;
        }
        if (serials.size() > quantity) {
            throw badRequest("TOO_MANY_SERIALS", "Dòng " + variant.sku() + " có " + serials.size()
                    + " serial, nhiều hơn số lượng " + quantity);
        }
        if (serials.stream().anyMatch(String::isBlank)) {
            throw badRequest("INVALID_SERIAL", "Serial không được để trống");
        }
        Set<String> used = serialsByVariant.computeIfAbsent(variant.id(), key -> new HashSet<>());
        for (String serial : serials) {
            if (!used.add(serial)) {
                throw new ConflictException("Serial " + serial + " bị trùng trong phiếu");
            }
        }
    }

    /** Mã phiếu dạng NK-20261105-000012: ngày tạo (UTC) và id phiếu. */
    private String formatCode(Long id) {
        String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        return "NK-" + date + "-" + String.format("%06d", id);
    }

    private BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    // ---------- Đổi entity sang DTO (kèm tên, mã lấy từ các module khác) ----------

    private InboundResponse toResponse(InboundOrder order) {
        Map<Long, VariantResponse> variants = new HashMap<>();
        Map<Long, String> locationCodes = new HashMap<>();

        List<InboundLineResponse> lines = new ArrayList<>();
        for (InboundOrderLine line : order.getLines()) {
            VariantResponse variant = variants.computeIfAbsent(line.getVariantId(), productService::getVariant);
            String locationCode = locationCodes.computeIfAbsent(line.getLocationId(), key -> locationService.getById(key).code());
            lines.add(new InboundLineResponse(line.getId(), line.getVariantId(), variant.sku(), variant.trackingType(),
                    line.getLocationId(), locationCode, line.getQuantity(), line.getUnitCost(), line.getBatchCode(),
                    line.getWarrantyMonths(), line.getSerialNumbers()));
        }
        String supplierName = supplierService.getById(order.getSupplierId()).name();
        return new InboundResponse(order.getId(), order.getCode(), order.getWarehouseId(), order.getSupplierId(),
                supplierName, order.getStatus().name(), order.getExpectedDate(), order.getNote(),
                order.getSubmittedAt(), order.getConfirmedAt(), order.getConfirmedBy(), order.getCreatedAt(),
                order.getTotalQuantity(), order.getTotalAmount(), lines);
    }

    private InboundSummaryResponse toSummary(InboundOrder order, Map<Long, String> supplierNames,
                                             Map<Long, String> warehouseCodes) {
        String supplierName = supplierNames.computeIfAbsent(order.getSupplierId(),
                key -> supplierService.getById(key).name());
        String warehouseCode = warehouseCodes.computeIfAbsent(order.getWarehouseId(),
                key -> warehouseService.getById(key).code());
        return new InboundSummaryResponse(order.getId(), order.getCode(), order.getWarehouseId(), warehouseCode,
                order.getSupplierId(), supplierName, order.getStatus().name(), order.getExpectedDate(),
                order.getLines().size(), order.getTotalQuantity(), order.getTotalAmount(), order.getCreatedAt());
    }
}

package com.tung.warehouse.rma.service;

import com.tung.warehouse.audit.service.AuditService;
import com.tung.warehouse.catalog.service.ProductService;
import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.common.security.CurrentUser;
import com.tung.warehouse.inventory.service.InventoryService;
import com.tung.warehouse.outbound.dto.OutboundAllocationResponse;
import com.tung.warehouse.outbound.dto.OutboundLineResponse;
import com.tung.warehouse.outbound.dto.OutboundResponse;
import com.tung.warehouse.outbound.service.OutboundService;
import com.tung.warehouse.rma.dto.RmaLineResponse;
import com.tung.warehouse.rma.dto.RmaProcessLine;
import com.tung.warehouse.rma.dto.RmaProcessRequest;
import com.tung.warehouse.rma.dto.RmaRequest;
import com.tung.warehouse.rma.dto.RmaResponse;
import com.tung.warehouse.rma.entity.RmaCondition;
import com.tung.warehouse.rma.entity.RmaLine;
import com.tung.warehouse.rma.entity.RmaOrder;
import com.tung.warehouse.rma.entity.RmaResolution;
import com.tung.warehouse.rma.entity.RmaStatus;
import com.tung.warehouse.rma.repository.RmaOrderRepository;
import com.tung.warehouse.warehouse.dto.LocationResponse;
import com.tung.warehouse.warehouse.service.LocationService;
import com.tung.warehouse.warehouse.service.WarehouseAccessService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ đổi trả (RMA) theo serial: UC7.1 Tạo yêu cầu đổi trả, UC7.2 Kiểm tra và xử lý hàng trả.
 * Chỉ nhận serial thuộc đơn xuất bán gốc. Hàng tốt nhập lại kho (IN_STOCK), hàng lỗi vào vị trí hàng lỗi (DEFECTIVE)
 * và không bao giờ được xuất lại. Serial hết bảo hành cần Quản lý kho xử lý.
 */
@Service
public class RmaService {

    private static final String ENTITY_TYPE = "RMA_ORDER";

    private final RmaOrderRepository orderRepository;
    private final OutboundService outboundService;
    private final InventoryService inventoryService;
    private final LocationService locationService;
    private final ProductService productService;
    private final WarehouseAccessService accessService;
    private final AuditService auditService;

    public RmaService(RmaOrderRepository orderRepository, OutboundService outboundService, InventoryService inventoryService,
                      LocationService locationService, ProductService productService,
                      WarehouseAccessService accessService, AuditService auditService) {
        this.orderRepository = orderRepository;
        this.outboundService = outboundService;
        this.inventoryService = inventoryService;
        this.locationService = locationService;
        this.productService = productService;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    // ==================== UC7.1 TẠO YÊU CẦU ĐỔI TRẢ ====================

    @Transactional
    public RmaResponse create(RmaRequest request) {
        OutboundResponse outbound = outboundService.getById(request.outboundOrderId()); // kiểm tra tồn tại và quyền vào kho
        if (!"COMPLETED".equals(outbound.status())) {
            throw new ConflictException("Chỉ nhận đổi trả cho đơn xuất đã hoàn tất");
        }
        if (outbound.customerId() == null) {
            throw new ConflictException("Chỉ nhận đổi trả cho đơn xuất bán (có khách hàng)");
        }

        // Serial nào thuộc biến thể nào trong đơn xuất gốc
        Map<String, Long> variantBySerial = new HashMap<>();
        for (OutboundLineResponse line : outbound.lines()) {
            for (OutboundAllocationResponse allocation : line.allocations()) {
                if (allocation.serialNumber() != null) {
                    variantBySerial.put(allocation.serialNumber(), line.variantId());
                }
            }
        }

        String temporaryCode = "TMP-" + UUID.randomUUID().toString().substring(0, 20);
        RmaOrder order = new RmaOrder(temporaryCode, outbound.warehouseId(), outbound.customerId(), outbound.id(), request.reason().trim());
        Set<String> seen = new HashSet<>();
        for (String rawSerial : request.serials()) {
            String serial = rawSerial.trim();
            Long variantId = variantBySerial.get(serial);
            if (variantId == null) {
                throw new ConflictException("Serial " + serial + " không thuộc đơn xuất gốc " + outbound.code());
            }
            if (!seen.add(serial)) {
                throw new ConflictException("Serial " + serial + " bị lặp trong yêu cầu");
            }
            order.addLine(new RmaLine(order, variantId, inventoryService.findDeviceUnitId(variantId, serial), serial));
        }
        orderRepository.save(order);
        order.assignCode("RMA-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format("%06d", order.getId()));

        auditService.log("RMA_CREATE", ENTITY_TYPE, order.getId(),
                order.getCode() + ": " + order.getLines().size() + " thiết bị, đơn xuất " + outbound.code());
        return toResponse(order);
    }

    /** Hàng trả đã về kho: REQUESTED sang RECEIVED. */
    @Transactional
    public RmaResponse receive(Long id) {
        RmaOrder order = findAndLock(id);
        order.markReceived();
        auditService.log("RMA_RECEIVE", ENTITY_TYPE, order.getId(), order.getCode() + ": hàng đã về kho");
        return toResponse(order);
    }

    // ==================== UC7.2 KIỂM TRA VÀ XỬ LÝ ====================

    @Transactional
    public RmaResponse process(Long id, RmaProcessRequest request) {
        RmaOrder order = findAndLock(id);
        order.assertStatus(RmaStatus.RECEIVED);

        Map<Long, RmaProcessLine> results = new HashMap<>();
        request.lines().forEach(result -> results.put(result.lineId(), result));
        Set<Long> lineIds = new HashSet<>();
        order.getLines().forEach(line -> lineIds.add(line.getId()));
        if (!lineIds.equals(results.keySet())) {
            throw badRequest("PROCESS_LINES_MISMATCH", "Phải gửi kết quả cho đúng tất cả các dòng của phiếu");
        }

        Map<String, LocalDate> warrantyBySerial = warrantyBySerial(order.getOutboundOrderId());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        for (RmaLine line : order.getLines()) {
            RmaProcessLine result = results.get(line.getId());
            boolean defective = result.condition() == RmaCondition.DEFECTIVE;
            checkResolution(result, defective);

            // Hết bảo hành thì chỉ Quản lý kho trở lên được xử lý
            LocalDate warrantyEnd = warrantyBySerial.get(line.getSerialNumber());
            if (warrantyEnd != null && warrantyEnd.isBefore(today) && !CurrentUser.isManagerOrAbove()) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "MANAGER_APPROVAL_REQUIRED",
                        "Serial " + line.getSerialNumber() + " đã hết bảo hành, cần Quản lý kho xử lý");
            }

            Long locationId = defective ? locationService.findDefectiveLocation(order.getWarehouseId()).id() : result.locationId();
            inventoryService.receiveReturn(order.getWarehouseId(), locationId, line.getVariantId(), line.getSerialNumber(),
                    defective, ENTITY_TYPE, order.getId(), "Đổi trả " + order.getCode());
            line.recordResult(result.condition(), result.resolution(), result.note());
        }
        order.markClosed();

        auditService.log("RMA_PROCESS", ENTITY_TYPE, order.getId(), order.getCode() + ": đã kiểm tra và xử lý xong");
        return toResponse(order);
    }

    // ==================== TRA CỨU ====================

    @Transactional(readOnly = true)
    public RmaResponse getById(Long id) {
        RmaOrder order = orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Phiếu đổi trả", id));
        accessService.assertCanAccess(order.getWarehouseId());
        return toResponse(order);
    }

    /** Danh sách phiếu đổi trả trong các kho được phép xem (Admin xem tất cả). */
    @Transactional(readOnly = true)
    public List<RmaResponse> findVisible() {
        List<RmaOrder> orders;
        if (CurrentUser.isAdmin()) {
            orders = orderRepository.findAllByOrderByIdDesc();
        } else if (CurrentUser.warehouseIds().isEmpty()) {
            orders = List.of();
        } else {
            orders = orderRepository.findByWarehouseIdInOrderByIdDesc(CurrentUser.warehouseIds());
        }
        return orders.stream().map(this::toResponse).toList();
    }

    // ==================== HÀM HỖ TRỢ ====================

    private RmaOrder findAndLock(Long id) {
        RmaOrder order = orderRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Phiếu đổi trả", id));
        accessService.assertCanAccess(order.getWarehouseId());
        return order;
    }

    /** Hàng tốt: RESTOCKED và phải chọn vị trí thường. Hàng lỗi: RETURNED_TO_SUPPLIER hoặc SCRAPPED. */
    private void checkResolution(RmaProcessLine result, boolean defective) {
        if (!defective) {
            if (result.resolution() != RmaResolution.RESTOCKED) {
                throw badRequest("INVALID_RESOLUTION", "Hàng tốt chỉ được nhập lại kho (RESTOCKED)");
            }
            if (result.locationId() == null) {
                throw badRequest("LOCATION_REQUIRED", "Hàng tốt phải chọn vị trí nhập lại kho");
            }
            LocationResponse location = locationService.getById(result.locationId());
            if ("DEFECTIVE".equals(location.locationType())) {
                throw new ConflictException("Hàng tốt không được đặt ở vị trí hàng lỗi");
            }
        } else if (result.resolution() == RmaResolution.RESTOCKED) {
            throw badRequest("INVALID_RESOLUTION", "Hàng lỗi không được nhập lại kho, chọn RETURNED_TO_SUPPLIER hoặc SCRAPPED");
        }
    }

    /** Hạn bảo hành của từng serial, lấy từ phân bổ của đơn xuất gốc. */
    private Map<String, LocalDate> warrantyBySerial(Long outboundOrderId) {
        Map<String, LocalDate> result = new HashMap<>();
        OutboundResponse outbound = outboundService.getById(outboundOrderId);
        for (OutboundLineResponse line : outbound.lines()) {
            for (OutboundAllocationResponse allocation : line.allocations()) {
                if (allocation.serialNumber() != null && allocation.warrantyEndDate() != null) {
                    result.put(allocation.serialNumber(), allocation.warrantyEndDate());
                }
            }
        }
        return result;
    }

    private BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    private RmaResponse toResponse(RmaOrder order) {
        Map<Long, String> skus = new HashMap<>();
        List<RmaLineResponse> lines = new ArrayList<>();
        for (RmaLine line : order.getLines()) {
            String sku = skus.computeIfAbsent(line.getVariantId(), key -> productService.getVariant(key).sku());
            lines.add(new RmaLineResponse(line.getId(), line.getVariantId(), sku, line.getSerialNumber(),
                    line.getCondition() == null ? null : line.getCondition().name(),
                    line.getResolution() == null ? null : line.getResolution().name(), line.getNote()));
        }
        return new RmaResponse(order.getId(), order.getCode(), order.getWarehouseId(), order.getCustomerId(),
                order.getOutboundOrderId(), order.getStatus().name(), order.getReason(), order.getCreatedAt(), lines);
    }
}

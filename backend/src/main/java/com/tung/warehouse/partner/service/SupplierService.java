package com.tung.warehouse.partner.service;

import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.common.util.PagingUtil;
import com.tung.warehouse.partner.dto.PartnerRequest;
import com.tung.warehouse.partner.dto.PartnerResponse;
import com.tung.warehouse.partner.entity.PartnerStatus;
import com.tung.warehouse.partner.entity.Supplier;
import com.tung.warehouse.partner.repository.PartnerSpecs;
import com.tung.warehouse.partner.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ UC2.1 (phần nhà cung cấp) và UC2.5 (tra cứu). */
@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    /** Tìm kiếm với 3 điều kiện tùy chọn, ghép bằng where(...).and(...).and(...). Điều kiện nào rỗng (null) thì tự bỏ qua. */
    @Transactional(readOnly = true)
    public PageResponse<PartnerResponse> search(String keyword, String phone, PartnerStatus status, int page, int size) {
        // Điều kiện đầu cần ghi rõ <Supplier> để Java biết đang tìm trong bảng nào; các điều kiện sau tự suy ra
        Specification<Supplier> spec = Specification.where(PartnerSpecs.<Supplier>keywordContains(keyword))
                .and(PartnerSpecs.phoneContains(phone))
                .and(PartnerSpecs.hasStatus(status));

        Page<Supplier> result = supplierRepository.findAll(spec, PagingUtil.of(page, size));
        return PageResponse.from(result.map(PartnerResponse::from));
    }

    @Transactional(readOnly = true)
    public PartnerResponse getById(Long id) {
        return PartnerResponse.from(findOrThrow(id));
    }

    @Transactional
    public PartnerResponse create(PartnerRequest request) {
        String code = request.code().trim().toUpperCase();
        if (supplierRepository.existsByCode(code)) {
            throw new ConflictException("Mã nhà cung cấp đã tồn tại: " + code);
        }
        Supplier supplier = new Supplier(code, request.name().trim());
        supplier.updateInfo(request.name().trim(), request.contactName(), request.phone(),
                request.email(), request.address(), request.taxCode());
        return PartnerResponse.from(supplierRepository.save(supplier));
    }

    @Transactional
    public PartnerResponse update(Long id, PartnerRequest request) {
        Supplier supplier = findOrThrow(id);
        supplier.updateInfo(request.name().trim(), request.contactName(), request.phone(),
                request.email(), request.address(), request.taxCode());
        // Không cần gọi save(): Hibernate tự UPDATE khi transaction kết thúc (dirty checking)
        return PartnerResponse.from(supplier);
    }

    @Transactional
    public PartnerResponse setActive(Long id, boolean active) {
        Supplier supplier = findOrThrow(id);
        if (active) {
            supplier.activate();
        } else {
            supplier.deactivate();
        }
        return PartnerResponse.from(supplier);
    }

    private Supplier findOrThrow(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Nhà cung cấp", id));
    }
}

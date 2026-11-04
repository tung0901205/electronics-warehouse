package com.tung.warehouse.catalog.service;

import com.tung.warehouse.catalog.dto.UnitRequest;
import com.tung.warehouse.catalog.dto.UnitResponse;
import com.tung.warehouse.catalog.entity.Unit;
import com.tung.warehouse.catalog.repository.ProductVariantRepository;
import com.tung.warehouse.catalog.repository.UnitRepository;
import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ UC2.2 (phần đơn vị tính) và UC2.5 (tra cứu). */
@Service
public class UnitService {

    private final UnitRepository unitRepository;
    private final ProductVariantRepository variantRepository;

    public UnitService(UnitRepository unitRepository, ProductVariantRepository variantRepository) {
        this.unitRepository = unitRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> findAll() {
        return unitRepository.findAllByOrderByCodeAsc().stream().map(UnitResponse::from).toList();
    }

    @Transactional
    public UnitResponse create(UnitRequest request) {
        String code = request.code().trim().toUpperCase();
        if (unitRepository.existsByCode(code)) {
            throw new ConflictException("Mã đơn vị đã tồn tại: " + code);
        }
        return UnitResponse.from(unitRepository.save(new Unit(code, request.name().trim())));
    }

    /** Chỉ sửa tên; mã đơn vị không đổi được. */
    @Transactional
    public UnitResponse update(Long id, UnitRequest request) {
        Unit unit = findOrThrow(id);
        unit.update(request.name().trim());
        return UnitResponse.from(unit);
    }

    @Transactional
    public void delete(Long id) {
        Unit unit = findOrThrow(id);
        if (variantRepository.existsByUnitId(id)) {
            throw new ConflictException("Không thể xóa: đơn vị tính đang được biến thể sử dụng");
        }
        unitRepository.delete(unit);
    }

    private Unit findOrThrow(Long id) {
        return unitRepository.findById(id).orElseThrow(() -> new NotFoundException("Đơn vị tính", id));
    }
}

package com.tung.warehouse.catalog.service;

import com.tung.warehouse.catalog.dto.BrandRequest;
import com.tung.warehouse.catalog.dto.BrandResponse;
import com.tung.warehouse.catalog.entity.Brand;
import com.tung.warehouse.catalog.repository.BrandRepository;
import com.tung.warehouse.catalog.repository.ProductRepository;
import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ UC2.2 (phần thương hiệu) và UC2.5 (tra cứu). */
@Service
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public BrandService(BrandRepository brandRepository, ProductRepository productRepository) {
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> findAll() {
        return brandRepository.findAllByOrderByNameAsc().stream().map(BrandResponse::from).toList();
    }

    @Transactional
    public BrandResponse create(BrandRequest request) {
        String name = request.name().trim();
        if (brandRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Thương hiệu đã tồn tại: " + name);
        }
        return BrandResponse.from(brandRepository.save(new Brand(name)));
    }

    @Transactional
    public BrandResponse update(Long id, BrandRequest request) {
        Brand brand = findOrThrow(id);
        String name = request.name().trim();
        if (brandRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Thương hiệu đã tồn tại: " + name);
        }
        brand.rename(name);
        return BrandResponse.from(brand);
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = findOrThrow(id);
        if (productRepository.existsByBrandId(id)) {
            throw new ConflictException("Không thể xóa: thương hiệu đang có sản phẩm");
        }
        brandRepository.delete(brand);
    }

    private Brand findOrThrow(Long id) {
        return brandRepository.findById(id).orElseThrow(() -> new NotFoundException("Thương hiệu", id));
    }
}

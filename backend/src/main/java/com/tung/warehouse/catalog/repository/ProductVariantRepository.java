package com.tung.warehouse.catalog.repository;

import com.tung.warehouse.catalog.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    boolean existsBySku(String sku);

    boolean existsByBarcode(String barcode);

    boolean existsByBarcodeAndIdNot(String barcode, Long id);

    /** Đơn vị tính này có đang được biến thể nào dùng không? */
    boolean existsByUnitId(Long unitId);
}

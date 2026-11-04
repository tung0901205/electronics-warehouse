package com.tung.warehouse.partner.repository;

import com.tung.warehouse.partner.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * JpaSpecificationExecutor thêm hàm findAll(Specification, Pageable): tìm kiếm với điều kiện ghép động.
 */
public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

    boolean existsByCode(String code);
}

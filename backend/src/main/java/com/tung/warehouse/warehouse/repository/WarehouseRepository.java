package com.tung.warehouse.warehouse.repository;

import com.tung.warehouse.warehouse.entity.Warehouse;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    boolean existsByCode(String code);

    List<Warehouse> findAllByOrderByNameAsc();

    /** Các kho nằm trong danh sách id (dùng cho người dùng bị giới hạn kho). */
    List<Warehouse> findByIdInOrderByNameAsc(Collection<Long> ids);
}

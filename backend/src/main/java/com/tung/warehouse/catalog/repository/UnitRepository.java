package com.tung.warehouse.catalog.repository;

import com.tung.warehouse.catalog.entity.Unit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findAllByOrderByCodeAsc();

    boolean existsByCode(String code);
}

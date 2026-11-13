package com.tung.warehouse.rma.repository;

import com.tung.warehouse.rma.entity.RmaOrder;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RmaOrderRepository extends JpaRepository<RmaOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RmaOrder o where o.id = :id")
    Optional<RmaOrder> findByIdForUpdate(@Param("id") Long id);

    List<RmaOrder> findAllByOrderByIdDesc();

    List<RmaOrder> findByWarehouseIdInOrderByIdDesc(Collection<Long> warehouseIds);
}

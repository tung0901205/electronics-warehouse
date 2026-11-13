package com.tung.warehouse.stocktake.repository;

import com.tung.warehouse.stocktake.entity.StocktakeSession;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StocktakeSessionRepository extends JpaRepository<StocktakeSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StocktakeSession s where s.id = :id")
    Optional<StocktakeSession> findByIdForUpdate(@Param("id") Long id);

    List<StocktakeSession> findAllByOrderByIdDesc();

    List<StocktakeSession> findByWarehouseIdInOrderByIdDesc(Collection<Long> warehouseIds);
}

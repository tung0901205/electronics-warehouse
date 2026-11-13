package com.tung.warehouse.transfer.repository;

import com.tung.warehouse.transfer.entity.TransferOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferOrderRepository extends JpaRepository<TransferOrder, Long>, JpaSpecificationExecutor<TransferOrder> {

    /** Đọc phiếu VÀ KHÓA dòng phiếu: hai người cùng thao tác trên một phiếu sẽ lần lượt, không đồng thời. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from TransferOrder o where o.id = :id")
    Optional<TransferOrder> findByIdForUpdate(@Param("id") Long id);
}

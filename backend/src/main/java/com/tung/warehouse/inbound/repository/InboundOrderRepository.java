package com.tung.warehouse.inbound.repository;

import com.tung.warehouse.inbound.entity.InboundOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboundOrderRepository extends JpaRepository<InboundOrder, Long>, JpaSpecificationExecutor<InboundOrder> {

    /**
     * Đọc phiếu VÀ KHÓA dòng phiếu (SELECT ... FOR UPDATE). Dùng khi xác nhận nhập kho:
     * hai người cùng bấm xác nhận một phiếu thì người đến sau phải chờ, rồi thấy phiếu đã CONFIRMED và bị từ chối.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from InboundOrder o where o.id = :id")
    Optional<InboundOrder> findByIdForUpdate(@Param("id") Long id);
}

package com.tung.warehouse.inventory.repository;

import com.tung.warehouse.inventory.entity.DeviceUnit;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceUnitRepository extends JpaRepository<DeviceUnit, Long> {

    /** Trong danh sách serial cho trước, những số nào ĐÃ tồn tại cho biến thể này? */
    @Query("select d.serialNumber from DeviceUnit d where d.variantId = :variantId and d.serialNumber in :serials")
    List<String> findExistingSerials(@Param("variantId") Long variantId, @Param("serials") Collection<String> serials);

    /** Các thiết bị thuộc đúng lô và vị trí đó, có số serial nằm trong danh sách. "BatchId" nghĩa là batch.id. */
    List<DeviceUnit> findByBatchIdAndLocationIdAndSerialNumberIn(Long batchId, Long locationId, Collection<String> serials);
}

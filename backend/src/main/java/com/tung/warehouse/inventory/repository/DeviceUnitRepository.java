package com.tung.warehouse.inventory.repository;

import com.tung.warehouse.inventory.entity.DeviceUnit;
import com.tung.warehouse.inventory.entity.DeviceUnitStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceUnitRepository extends JpaRepository<DeviceUnit, Long> {

    /** Trong danh sách serial cho trước, những số nào ĐÃ tồn tại cho biến thể này? */
    @Query("select d.serialNumber from DeviceUnit d where d.variantId = :variantId and d.serialNumber in :serials")
    List<String> findExistingSerials(@Param("variantId") Long variantId, @Param("serials") Collection<String> serials);

    /** [MỚI Giai đoạn 5] Lấy `pageable.size` thiết bị còn sẵn (đúng trạng thái) của một lô tại một vị trí, theo thứ tự id. */
    List<DeviceUnit> findByBatchIdAndLocationIdAndStatusOrderByIdAsc(Long batchId, Long locationId,
                                                                     DeviceUnitStatus status, Pageable pageable);

    /** [MỚI Giai đoạn 6] Tìm thiết bị theo biến thể và số serial (serial chỉ duy nhất trong từng biến thể). */
    Optional<DeviceUnit> findByVariantIdAndSerialNumber(Long variantId, String serialNumber);

    List<DeviceUnit> findByVariantIdAndSerialNumberIn(Long variantId, Collection<String> serials);

    /** Các thiết bị thuộc đúng lô và vị trí đó, có số serial nằm trong danh sách. "BatchId" nghĩa là batch.id. */
    List<DeviceUnit> findByBatchIdAndLocationIdAndSerialNumberIn(Long batchId, Long locationId, Collection<String> serials);
}

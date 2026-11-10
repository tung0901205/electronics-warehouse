package com.tung.warehouse.inventory.repository;

import com.tung.warehouse.inventory.entity.InventoryReservation;
import com.tung.warehouse.inventory.entity.ReservationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    List<InventoryReservation> findByOutboundLineIdAndStatus(Long outboundLineId, ReservationStatus status);
}

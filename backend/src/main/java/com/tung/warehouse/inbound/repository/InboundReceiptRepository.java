package com.tung.warehouse.inbound.repository;

import com.tung.warehouse.inbound.entity.InboundReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, Long> {
}

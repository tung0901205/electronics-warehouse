package com.tung.warehouse.inbound.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Một số serial của một dòng phiếu nhập (chỉ có với biến thể theo dõi SERIAL). */
@Entity
@Table(name = "inbound_line_serials")
public class InboundLineSerial extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inbound_line_id", nullable = false)
    private InboundOrderLine line;

    @Column(name = "serial_number", nullable = false, length = 50)
    private String serialNumber;

    protected InboundLineSerial() {
    }

    public InboundLineSerial(InboundOrderLine line, String serialNumber) {
        this.line = line;
        this.serialNumber = serialNumber;
    }

    public String getSerialNumber() { return serialNumber; }
}

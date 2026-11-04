package com.tung.warehouse.partner.dto;

import com.tung.warehouse.partner.entity.Partner;
import java.time.LocalDateTime;

public record PartnerResponse(
        Long id,
        String code,
        String name,
        String contactName,
        String phone,
        String email,
        String address,
        String taxCode,
        String status,
        LocalDateTime createdAt) {

    public static PartnerResponse from(Partner partner) {
        return new PartnerResponse(
                partner.getId(),
                partner.getCode(),
                partner.getName(),
                partner.getContactName(),
                partner.getPhone(),
                partner.getEmail(),
                partner.getAddress(),
                partner.getTaxCode(),
                partner.getStatus().name(),
                partner.getCreatedAt());
    }
}

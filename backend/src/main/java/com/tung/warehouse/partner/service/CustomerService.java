package com.tung.warehouse.partner.service;

import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.common.util.PagingUtil;
import com.tung.warehouse.partner.dto.PartnerRequest;
import com.tung.warehouse.partner.dto.PartnerResponse;
import com.tung.warehouse.partner.entity.PartnerStatus;
import com.tung.warehouse.partner.entity.Customer;
import com.tung.warehouse.partner.repository.PartnerSpecs;
import com.tung.warehouse.partner.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ UC2.1 (phần khách hàng) và UC2.5 (tra cứu). */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /** Tìm kiếm với 3 điều kiện tùy chọn, ghép bằng where(...).and(...).and(...). Điều kiện nào rỗng (null) thì tự bỏ qua. */
    @Transactional(readOnly = true)
    public PageResponse<PartnerResponse> search(String keyword, String phone, PartnerStatus status, int page, int size) {
        // Điều kiện đầu cần ghi rõ <Customer> để Java biết đang tìm trong bảng nào; các điều kiện sau tự suy ra
        Specification<Customer> spec = Specification.where(PartnerSpecs.<Customer>keywordContains(keyword))
                .and(PartnerSpecs.phoneContains(phone))
                .and(PartnerSpecs.hasStatus(status));

        Page<Customer> result = customerRepository.findAll(spec, PagingUtil.of(page, size));
        return PageResponse.from(result.map(PartnerResponse::from));
    }

    @Transactional(readOnly = true)
    public PartnerResponse getById(Long id) {
        return PartnerResponse.from(findOrThrow(id));
    }

    @Transactional
    public PartnerResponse create(PartnerRequest request) {
        String code = request.code().trim().toUpperCase();
        if (customerRepository.existsByCode(code)) {
            throw new ConflictException("Mã khách hàng đã tồn tại: " + code);
        }
        Customer customer = new Customer(code, request.name().trim());
        customer.updateInfo(request.name().trim(), request.contactName(), request.phone(),
                request.email(), request.address(), request.taxCode());
        return PartnerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public PartnerResponse update(Long id, PartnerRequest request) {
        Customer customer = findOrThrow(id);
        customer.updateInfo(request.name().trim(), request.contactName(), request.phone(),
                request.email(), request.address(), request.taxCode());
        // Không cần gọi save(): Hibernate tự UPDATE khi transaction kết thúc (dirty checking)
        return PartnerResponse.from(customer);
    }

    @Transactional
    public PartnerResponse setActive(Long id, boolean active) {
        Customer customer = findOrThrow(id);
        if (active) {
            customer.activate();
        } else {
            customer.deactivate();
        }
        return PartnerResponse.from(customer);
    }

    private Customer findOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Khách hàng", id));
    }
}

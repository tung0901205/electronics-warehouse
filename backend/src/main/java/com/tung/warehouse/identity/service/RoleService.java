package com.tung.warehouse.identity.service;

import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.identity.dto.RoleResponse;
import com.tung.warehouse.identity.entity.Role;
import com.tung.warehouse.identity.repository.RoleRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private final RoleRepository roleRepository;

    // Spring tự truyền RoleRepository vào đây (constructor injection)
    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true) // chỉ đọc: nhanh hơn và an toàn hơn
    public List<RoleResponse> findAll() {
        return roleRepository.findAll().stream()
                .map(RoleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse getById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Vai trò", id));
        return RoleResponse.from(role);
    }
}

package com.tung.warehouse.identity.service;

import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.common.security.CurrentUser;
import com.tung.warehouse.common.util.PagingUtil;
import com.tung.warehouse.identity.dto.AssignRolesRequest;
import com.tung.warehouse.identity.dto.AssignWarehousesRequest;
import com.tung.warehouse.identity.dto.UserCreateRequest;
import com.tung.warehouse.identity.dto.UserResponse;
import com.tung.warehouse.identity.dto.UserUpdateRequest;
import com.tung.warehouse.identity.entity.Role;
import com.tung.warehouse.identity.entity.User;
import com.tung.warehouse.identity.repository.RoleRepository;
import com.tung.warehouse.identity.repository.UserRepository;
import com.tung.warehouse.identity.repository.UserSpecs;
import com.tung.warehouse.warehouse.service.WarehouseService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ UC1.4 Quản lý người dùng và UC1.5 Gán vai trò (chỉ Admin). */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final WarehouseService warehouseService; // [MỚI Ngày 7] chỉ gọi qua service của module warehouse

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                       WarehouseService warehouseService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.warehouseService = warehouseService;
    }

    /** Tìm kiếm với 3 điều kiện tùy chọn, ghép bằng where(...).and(...).and(...). Điều kiện nào rỗng (null) thì tự bỏ qua. */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String keyword, String roleName, Boolean enabled, int page, int size) {
        Specification<User> spec = Specification.where(UserSpecs.keywordContains(keyword))
                .and(UserSpecs.hasRole(roleName))
                .and(UserSpecs.hasEnabled(enabled));

        Page<User> result = userRepository.findAll(spec, PagingUtil.of(page, size));
        return PageResponse.from(result.map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Tên đăng nhập đã tồn tại: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email đã được sử dụng: " + email);
        }

        User user = new User(username, email, passwordEncoder.encode(request.password()), request.fullName().trim());
        user.setPhone(request.phone());
        user.replaceRoles(findRoles(request.roleNames()));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = findOrThrow(id);
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new ConflictException("Email đã được sử dụng: " + email);
        }
        user.updateProfile(email, request.fullName().trim(), request.phone());
        return UserResponse.from(user);
    }

    /** UC1.5: thay toàn bộ vai trò của người dùng. */
    @Transactional
    public UserResponse assignRoles(Long id, AssignRolesRequest request) {
        User user = findOrThrow(id);
        Set<Role> newRoles = findRoles(request.roleNames());

        // Chặn Admin tự gỡ quyền Admin của chính mình, tránh cả hệ thống không còn ai quản trị
        boolean removesOwnAdmin = isCurrentUser(id) && newRoles.stream().noneMatch(r -> r.getName().equals("ADMIN"));
        if (removesOwnAdmin) {
            throw new ConflictException("Không thể tự gỡ vai trò ADMIN của chính mình");
        }
        user.replaceRoles(newRoles);
        return UserResponse.from(user);
    }

    /**
     * [MỚI Ngày 7] Gán phạm vi kho cho người dùng (thay toàn bộ danh sách cũ).
     * Có hiệu lực ở lần đăng nhập hoặc làm mới token kế tiếp, vì danh sách kho nằm trong JWT.
     */
    @Transactional
    public UserResponse assignWarehouses(Long id, AssignWarehousesRequest request) {
        User user = findOrThrow(id);
        Set<Long> warehouseIds = new HashSet<>(request.warehouseIds());
        warehouseService.checkAllExist(warehouseIds);
        user.replaceWarehouseIds(warehouseIds);
        return UserResponse.from(user);
    }

    /** Kích hoạt hoặc vô hiệu hóa tài khoản. Vô hiệu hóa thì đá luôn khỏi mọi thiết bị đang đăng nhập. */
    @Transactional
    public UserResponse setEnabled(Long id, boolean enabled) {
        User user = findOrThrow(id);
        if (!enabled && isCurrentUser(id)) {
            throw new ConflictException("Không thể tự vô hiệu hóa tài khoản của chính mình");
        }
        user.setEnabled(enabled);
        if (!enabled) {
            // Lưu ý: access token đang còn hạn (tối đa 15 phút) vẫn dùng được cho đến khi hết hạn
            refreshTokenService.revokeAllOf(id);
        }
        return UserResponse.from(user);
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("Người dùng", id));
    }

    /** Đổi danh sách tên vai trò thành danh sách entity Role. Tên không tồn tại thì báo 404. */
    private Set<Role> findRoles(List<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        for (String name : roleNames) {
            Role role = roleRepository.findByName(name.trim().toUpperCase())
                    .orElseThrow(() -> new NotFoundException("Vai trò", name));
            roles.add(role);
        }
        return roles;
    }

    private boolean isCurrentUser(Long id) {
        return CurrentUser.id().map(id::equals).orElse(false);
    }
}

package com.tung.warehouse.identity.service;

import com.tung.warehouse.common.exception.AccountDisabledException;
import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.exception.InvalidCredentialsException;
import com.tung.warehouse.common.exception.InvalidTokenException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.identity.dto.ChangePasswordRequest;
import com.tung.warehouse.identity.dto.LoginRequest;
import com.tung.warehouse.identity.dto.TokenResponse;
import com.tung.warehouse.identity.dto.UserInfoResponse;
import com.tung.warehouse.identity.entity.RefreshToken;
import com.tung.warehouse.identity.entity.User;
import com.tung.warehouse.identity.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ UC1.1 Đăng nhập, UC1.2 Làm mới phiên và đăng xuất, UC1.3 Đổi mật khẩu. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /** UC1.1: đăng nhập bằng tên đăng nhập và mật khẩu. */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        // Sai tên đăng nhập hay sai mật khẩu đều báo cùng một lỗi
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isEnabled()) {
            throw new AccountDisabledException();
        }
        return issueTokens(user);
    }

    /**
     * UC1.2: đổi refresh token cũ lấy cặp token mới ("refresh token rotation": mỗi refresh token chỉ dùng được MỘT lần).
     *
     * noRollbackFor: bình thường khi ném RuntimeException thì @Transactional ROLLBACK mọi thay đổi.
     * Ở nhánh "token đã thu hồi bị dùng lại" ta vừa thu hồi mọi phiên (một câu UPDATE) vừa ném lỗi.
     * Nếu không có noRollbackFor, câu UPDATE đó bị hủy theo, và việc thu hồi vô nghĩa.
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken oldToken = refreshTokenService.find(rawRefreshToken);
        User user = oldToken.getUser();

        if (oldToken.isRevoked()) {
            // Token đã dùng rồi mà vẫn có người gửi lại: có thể đã bị đánh cắp. Thu hồi toàn bộ phiên cho an toàn.
            refreshTokenService.revokeAllOf(user.getId());
            throw new InvalidTokenException("Refresh token đã bị thu hồi, vui lòng đăng nhập lại");
        }
        if (oldToken.isExpired()) {
            throw new InvalidTokenException("Refresh token đã hết hạn, vui lòng đăng nhập lại");
        }
        if (!user.isEnabled()) {
            throw new AccountDisabledException();
        }

        oldToken.revoke();          // dùng một lần rồi bỏ
        return issueTokens(user);   // cấp cặp token mới
    }

    /** UC1.2: đăng xuất bằng cách thu hồi refresh token. Access token còn sống đến khi hết hạn (tối đa 15 phút). */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    /** UC1.3: đổi mật khẩu. */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Người dùng", userId));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WRONG_OLD_PASSWORD", "Mật khẩu cũ không đúng");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword())); // Hibernate tự UPDATE khi kết thúc transaction
        refreshTokenService.revokeAllOf(userId); // buộc mọi thiết bị khác đăng nhập lại
    }

    /** Thông tin người đang đăng nhập (frontend gọi để hiện tên và quyết định hiện menu nào). */
    @Transactional(readOnly = true)
    public UserInfoResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Người dùng", userId));
        return UserInfoResponse.from(user);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.createAccessToken(user);
        String refreshToken = refreshTokenService.issue(user);
        return new TokenResponse(accessToken, refreshToken, "Bearer", jwtService.getAccessTokenSeconds());
    }
}

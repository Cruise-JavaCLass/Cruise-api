package com.cruise.auth.security;

import com.cruise.auth.entity.User;
import com.cruise.auth.entity.UserPassengerBinding;
import com.cruise.auth.enums.VerificationStatus;
import com.cruise.auth.repository.UserPassengerBindingRepository;
import com.cruise.auth.repository.UserRepository;
import com.cruise.common.enums.Role;
import com.cruise.common.security.VerifiedCaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Spring Bean xác thực quyền sở hữu của hành khách đối với hồ sơ mục tiêu hoặc nhân viên được phép (KAN-720).
 * Được gọi thông qua SpEL @PreAuthorize("@passengerOwnershipValidator.isOwnerOrStaff(authentication, #id)").
 */
@Component("passengerOwnershipValidator")
@RequiredArgsConstructor
@Slf4j
public class PassengerOwnershipValidator {

    private final UserPassengerBindingRepository bindingRepository;
    private final UserRepository userRepository;

    /**
     * Kiểm tra quyền sở hữu hoặc quyền nhân viên điều hành / tài chính.
     *
     * @param auth Authentication context của caller hiện tại
     * @param targetPassengerId ID hồ sơ hành khách mục tiêu cần truy cập
     * @return true nếu caller có quyền, ngược lại false
     */
    public boolean isOwnerOrStaff(Authentication auth, Long targetPassengerId) {
        if (auth == null || !auth.isAuthenticated() || targetPassengerId == null || targetPassengerId <= 0) {
            return false;
        }

        ExtractedIdentity identity = extractIdentity(auth);
        Role role = identity.role();
        Long userId = identity.userId();

        if (role == null) {
            return false;
        }

        // 1. Quản trị hệ thống, Quản lý vận hành và Lễ tân / Tài chính được phép truy cập
        if (role == Role.SYSTEM_ADMIN || role == Role.OPERATIONS_MANAGER || role == Role.FINANCE_FRONT_DESK) {
            return true;
        }

        // 2. Hành khách chỉ được phép truy cập hồ sơ chính mình thông qua liên kết hợp lệ
        if (role == Role.PASSENGER) {
            if (userId == null || userId <= 0) {
                return false;
            }
            return isPassengerOwner(userId, targetPassengerId);
        }

        // 3. Các vai trò khác (POS_STAFF, ITINERARY_COORDINATOR,...) không có quyền sở hữu hành khách
        return false;
    }

    private boolean isPassengerOwner(Long userId, Long targetPassengerId) {
        // Tra cứu trực tiếp theo cặp (userId, targetPassengerId)
        Optional<UserPassengerBinding> directBinding = bindingRepository.findByUserIdAndPassengerId(userId, targetPassengerId);
        if (directBinding.isPresent()) {
            return isValidBinding(directBinding.get(), targetPassengerId);
        }

        // Tra cứu fallback theo danh sách liên kết của userId
        return bindingRepository.findByUserId(userId).stream()
                .anyMatch(b -> isValidBinding(b, targetPassengerId));
    }

    private boolean isValidBinding(UserPassengerBinding binding, Long targetPassengerId) {
        return targetPassengerId.equals(binding.getPassengerId())
                && !Boolean.FALSE.equals(binding.getActive())
                && binding.getVerificationStatus() != VerificationStatus.REVOKED;
    }

    private ExtractedIdentity extractIdentity(Authentication auth) {
        Long userId = null;
        Role role = null;

        if (auth instanceof PassengerSecurityContext psc) {
            userId = psc.getUserId();
            role = psc.getRole();
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof VerifiedCaller vc) {
            if (userId == null) userId = vc.userId();
            if (role == null) role = vc.role();
        } else if (principal instanceof User user) {
            if (userId == null) userId = user.getId();
            if (role == null) role = user.getRole();
        } else if (principal instanceof PassengerSecurityContext psc) {
            if (userId == null) userId = psc.getUserId();
            if (role == null) role = psc.getRole();
        } else if (principal instanceof Long id) {
            if (userId == null) userId = id;
        } else if (principal instanceof Number num) {
            if (userId == null) userId = num.longValue();
        }

        // Nếu role chưa có, đọc từ Authorities có tiền tố ROLE_
        if (role == null && auth.getAuthorities() != null) {
            for (GrantedAuthority ga : auth.getAuthorities()) {
                String authority = ga.getAuthority();
                if (authority != null && authority.startsWith("ROLE_")) {
                    try {
                        role = Role.valueOf(authority.substring(5).toUpperCase());
                        break;
                    } catch (IllegalArgumentException ignored) {
                        // Bỏ qua giá trị authority không tương thích với enum Role
                    }
                }
            }
        }

        // Nếu userId hoặc role vẫn chưa lấy được, tra cứu User qua username trong UserRepository
        if ((userId == null || role == null) && auth.getName() != null && userRepository != null) {
            Optional<User> userOpt = userRepository.findByUsername(auth.getName());
            if (userOpt.isPresent()) {
                User u = userOpt.get();
                if (userId == null) userId = u.getId();
                if (role == null) role = u.getRole();
            }
        }

        return new ExtractedIdentity(userId, role);
    }

    private record ExtractedIdentity(Long userId, Role role) {}
}

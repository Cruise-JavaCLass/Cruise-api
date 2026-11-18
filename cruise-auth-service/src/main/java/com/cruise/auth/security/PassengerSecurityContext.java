package com.cruise.auth.security;

import com.cruise.common.enums.Role;
import com.cruise.common.security.VerifiedCaller;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Authentication context dành cho hành khách và người dùng hệ sinh thái Cruise (KAN-720).
 * Cho phép tích hợp cơ chế bảo mật cấp phương thức Spring Security (@PreAuthorize).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerSecurityContext implements Authentication {

    private Long userId;
    private String username;
    private Role role;
    private Long passengerId;

    @Builder.Default
    private boolean authenticated = true;

    /**
     * Constructor tiện lợi khởi tạo từ VerifiedCaller (chưa gắn target passengerId).
     */
    public PassengerSecurityContext(VerifiedCaller caller) {
        this(caller, null);
    }

    /**
     * Constructor tiện lợi khởi tạo từ VerifiedCaller và passengerId cụ thể.
     */
    public PassengerSecurityContext(VerifiedCaller caller, Long passengerId) {
        this(
                caller != null ? caller.userId() : null,
                caller != null ? caller.username() : null,
                caller != null ? caller.role() : null,
                passengerId,
                caller != null
        );
    }

    public static PassengerSecurityContext from(VerifiedCaller caller) {
        return new PassengerSecurityContext(caller);
    }

    public static PassengerSecurityContext from(VerifiedCaller caller, Long passengerId) {
        return new PassengerSecurityContext(caller, passengerId);
    }

    public static PassengerSecurityContext of(Long userId, String username, Role role, Long passengerId) {
        return PassengerSecurityContext.builder()
                .userId(userId)
                .username(username)
                .role(role)
                .passengerId(passengerId)
                .authenticated(true)
                .build();
    }

    public VerifiedCaller toVerifiedCaller() {
        return (userId != null && username != null && role != null)
                ? new VerifiedCaller(userId, username, role)
                : null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return Collections.emptyList();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return this;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return username;
    }
}

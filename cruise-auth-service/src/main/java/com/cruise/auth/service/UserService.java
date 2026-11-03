package com.cruise.auth.service;

import com.cruise.auth.entity.User;
import com.cruise.auth.repository.UserRepository;
import com.cruise.common.enums.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service xử lý nghiệp vụ người dùng và hành khách
 */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.cruise.auth.repository.IdentityAuditRepository audit;

    @org.springframework.beans.factory.annotation.Autowired
    public UserService(UserRepository userRepository, org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                       JwtService jwtService, com.cruise.auth.repository.IdentityAuditRepository audit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    public UserService(UserRepository userRepository, org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this(userRepository, passwordEncoder, jwtService, null);
    }

    public UserService(UserRepository userRepository) {
        this(userRepository, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(), null);
    }

    public User registerUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + user.getUsername());
        }
        if (user.getPassword() != null && !user.getPassword().isBlank() && !user.getPassword().startsWith("$2a$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        User saved = userRepository.save(user);
        recordIdentityChange("USER_CREATED", saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserByRfid(String rfidTag) {
        return userRepository.findByRfidTag(rfidTag);
    }

    @Transactional(readOnly = true)
    public List<User> getUsersByRole(Role role) {
        return userRepository.findByRole(role);
    }

    public void deleteUserByUsername(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (jwtService != null) {
                jwtService.revokeAllUserTokens(username);
            }
            userRepository.delete(user);
            recordIdentityChange("USER_DELETED", user);
        });
    }

    private void recordIdentityChange(String action, User user) {
        // Compatibility constructors are used by isolated unit tests; production injects the repository.
        if (audit == null) return;
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        Object caller = attributes == null ? null : attributes.getAttribute(
                com.cruise.common.security.VerifiedCaller.ATTRIBUTE,
                org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        Long actor = caller instanceof com.cruise.common.security.VerifiedCaller verified ? verified.userId() : null;
        Role role = user.getRole();
        String targetRole = role == null ? "UNKNOWN" : role.name();
        audit.save(new com.cruise.auth.entity.IdentityAuditEvent(action, actor, user.getUsername(), targetRole));
    }
}

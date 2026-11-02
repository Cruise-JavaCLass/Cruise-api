package com.cruise.auth.service;

import com.cruise.auth.entity.User;
import com.cruise.auth.repository.UserRepository;
import com.cruise.common.enums.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
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

    @org.springframework.beans.factory.annotation.Autowired
    public UserService(UserRepository userRepository, org.springframework.security.crypto.password.PasswordEncoder passwordEncoder, @org.springframework.beans.factory.annotation.Autowired(required = false) JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        return userRepository.save(user);
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
        });
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Cập nhật họ tên, email và vai trò. Username và mật khẩu không đổi ở đây.
     *
     * @throws NoSuchElementException khi không có người dùng với id này
     * @throws IllegalArgumentException khi dữ liệu không hợp lệ hoặc email đã được tài khoản khác dùng
     */
    public User updateUser(Long id, String fullName, String email, Role role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + id));

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role must not be null");
        }

        String normalizedEmail = email.trim();
        userRepository.findByEmail(normalizedEmail)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Email already exists: " + normalizedEmail);
                });

        user.setFullName(fullName.trim());
        user.setEmail(normalizedEmail);
        user.setRole(role);
        return userRepository.save(user);
    }

    /**
     * Bật hoặc tắt tài khoản.
     *
     * @throws NoSuchElementException khi không có người dùng với id này
     */
    public User setUserActive(Long id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
        user.setActive(active);
        return userRepository.save(user);
    }
}

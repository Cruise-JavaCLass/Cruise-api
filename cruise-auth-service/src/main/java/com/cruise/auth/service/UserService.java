package com.cruise.auth.service;

import com.cruise.auth.entity.User;
import com.cruise.auth.repository.UserRepository;
import com.cruise.common.enums.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service xử lý nghiệp vụ người dùng, nhân viên và hành khách trên tàu du lịch.
 */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, @Nullable JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserService(UserRepository userRepository) {
        this(userRepository, new BCryptPasswordEncoder(), null);
    }

    /**
     * Đăng ký tài khoản người dùng mới vào cơ sở dữ liệu.
     * Mật khẩu dạng plain text sẽ được mã hóa BCrypt tự động trước khi lưu.
     *
     * @param user Thực thể người dùng cần đăng ký
     * @return Thực thể User đã được lưu với ID định danh
     * @throws IllegalArgumentException khi tên đăng nhập đã tồn tại trong hệ thống
     */
    public User registerUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + user.getUsername());
        }
        if (user.getPassword() != null && !user.getPassword().isBlank() && !user.getPassword().startsWith("$2a$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        return userRepository.save(user);
    }

    /**
     * Tra cứu thông tin người dùng theo tên đăng nhập.
     *
     * @param username Tên đăng nhập
     * @return Optional chứa User nếu tìm thấy
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Tra cứu thông tin người dùng theo mã định danh RFID/NFC thẻ phòng hoặc vòng tay.
     *
     * @param rfidTag Chuỗi mã RFID
     * @return Optional chứa User nếu tìm thấy
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByRfid(String rfidTag) {
        return userRepository.findByRfidTag(rfidTag);
    }

    /**
     * Lấy danh sách toàn bộ người dùng theo vai trò tác nghiệp.
     *
     * @param role Vai trò người dùng (Role)
     * @return Danh sách các User tương ứng
     */
    @Transactional(readOnly = true)
    public List<User> getUsersByRole(Role role) {
        return userRepository.findByRole(role);
    }

    /**
     * Xóa người dùng theo tên đăng nhập và thu hồi toàn bộ token JWT đang hoạt động.
     *
     * @param username Tên đăng nhập của người dùng cần xóa
     */
    public void deleteUserByUsername(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (jwtService != null) {
                jwtService.revokeAllUserTokens(username);
            }
            userRepository.delete(user);
        });
    }
}

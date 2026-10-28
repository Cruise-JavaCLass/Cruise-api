package com.cruise.auth.controller;

import com.cruise.auth.dto.UserDto;
import com.cruise.auth.dto.UserResponseDto;
import com.cruise.auth.entity.User;
import com.cruise.auth.service.JwtService;
import com.cruise.auth.service.UserService;
import com.cruise.common.dto.ApiResponse;
import com.cruise.common.enums.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller quản lý tài khoản người dùng và hành khách trong hệ thống hàng hải.
 */
@RestController
@RequestMapping("/api/auth/users")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    @Autowired
    public UserController(UserService userService, @Nullable JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    public UserController(UserService userService) {
        this(userService, (JwtService) null);
    }

    /**
     * Tạo tài khoản người dùng hoặc hành khách mới.
     * Người dùng ẩn danh chỉ có thể đăng ký vai trò PASSENGER.
     * Các vai trò đặc quyền khác yêu cầu quyền SYSTEM_ADMIN hoặc ADMIN.
     *
     * @param userDto DTO thông tin tài khoản người dùng
     * @param authHeader Bearer token xác thực của caller (nếu có)
     * @return ApiResponse chứa UserResponseDto vừa tạo
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponseDto>> createUser(
            @RequestBody UserDto userDto,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            User user = new User();
            user.setUsername(userDto.getUsername());
            user.setPassword(userDto.getPassword());
            user.setFullName(userDto.getFullName());
            user.setEmail(userDto.getEmail());

            Role assignedRole = Role.PASSENGER;
            if (userDto.getRole() != null) {
                if (userDto.getRole() == Role.PASSENGER || isCallerAdmin(authHeader)) {
                    assignedRole = userDto.getRole();
                }
            }
            user.setRole(assignedRole);

            user.setCabinNumber(userDto.getCabinNumber());
            user.setRfidTag(userDto.getRfidTag());
            if (userDto.getActive() != null) {
                user.setActive(userDto.getActive());
            }

            User created = userService.registerUser(user);
            UserResponseDto responseDto = mapToResponseDto(created);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok("User created successfully", responseDto));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * Tra cứu thông tin hồ sơ người dùng theo tên đăng nhập.
     *
     * @param username Tên đăng nhập người dùng cần tra cứu
     * @return ApiResponse chứa UserResponseDto hoặc 404 NOT_FOUND
     */
    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<UserResponseDto>> getUserByUsername(@PathVariable String username) {
        Optional<User> user = userService.getUserByUsername(username);
        return user.map(u -> ResponseEntity.ok(ApiResponse.ok("User found", mapToResponseDto(u))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("User not found: " + username)));
    }

    /**
     * Xóa tài khoản người dùng và thu hồi token phiên làm việc.
     * Yêu cầu caller có quyền quản trị viên (SYSTEM_ADMIN hoặc ADMIN).
     *
     * @param username Tên đăng nhập người dùng cần xóa
     * @param authHeader Bearer token của quản trị viên
     * @return ApiResponse thông báo trạng thái xóa thành công
     */
    @DeleteMapping("/{username}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable String username,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Missing or invalid Authorization header"));
        }
        String token = authHeader.substring(7).trim();
        if (jwtService == null || !jwtService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid or expired authentication token"));
        }
        String role = jwtService.extractRole(token);
        if (role == null || (!role.equals("SYSTEM_ADMIN") && !role.equals("ADMIN"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied: Administrator privileges required"));
        }
        userService.deleteUserByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok("User deleted successfully", null));
    }

    /**
     * Kiểm tra xem caller hiện tại có quyền quản trị viên (SYSTEM_ADMIN hoặc ADMIN) hay không.
     *
     * @param authHeader Tiêu đề Authorization chứa Bearer token
     * @return true nếu caller có quyền quản trị viên, ngược lại false
     */
    private boolean isCallerAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring(7).trim();
        if (jwtService == null || !jwtService.validateToken(token)) {
            return false;
        }
        String role = jwtService.extractRole(token);
        return role != null && (role.equals("SYSTEM_ADMIN") || role.equals("ADMIN"));
    }

    private UserResponseDto mapToResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .cabinNumber(user.getCabinNumber())
                .rfidTag(user.getRfidTag())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Bộ xử lý ngoại lệ cho các lỗi đối số không hợp lệ trong yêu cầu.
     *
     * @param ex Ngoại lệ IllegalArgumentException
     * @return ApiResponse với mã 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }
}

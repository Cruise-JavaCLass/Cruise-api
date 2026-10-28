package com.cruise.auth.controller;

import com.cruise.auth.entity.User;
import com.cruise.auth.service.UserService;
import com.cruise.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller quản lý tài khoản người dùng và hành khách
 */
@RestController
@RequestMapping("/api/auth/users")
public class UserController {

    private final UserService userService;
    private final com.cruise.auth.service.JwtService jwtService;

    @org.springframework.beans.factory.annotation.Autowired
    public UserController(UserService userService, @org.springframework.lang.Nullable com.cruise.auth.service.JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    public UserController(UserService userService) {
        this(userService, (com.cruise.auth.service.JwtService) null);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<com.cruise.auth.dto.UserResponseDto>> createUser(@RequestBody com.cruise.auth.dto.UserDto userDto) {
        try {
            User user = new User();
            user.setUsername(userDto.getUsername());
            user.setPassword(userDto.getPassword());
            user.setFullName(userDto.getFullName());
            user.setEmail(userDto.getEmail());
            user.setRole(userDto.getRole());
            user.setCabinNumber(userDto.getCabinNumber());
            user.setRfidTag(userDto.getRfidTag());
            if (userDto.getActive() != null) {
                user.setActive(userDto.getActive());
            }

            User created = userService.registerUser(user);
            com.cruise.auth.dto.UserResponseDto responseDto = mapToResponseDto(created);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok("User created successfully", responseDto));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<com.cruise.auth.dto.UserResponseDto>> getUserByUsername(@PathVariable String username) {
        Optional<User> user = userService.getUserByUsername(username);
        return user.map(u -> ResponseEntity.ok(ApiResponse.ok("User found", mapToResponseDto(u))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("User not found: " + username)));
    }

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

    private com.cruise.auth.dto.UserResponseDto mapToResponseDto(User user) {
        return com.cruise.auth.dto.UserResponseDto.builder()
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }
}

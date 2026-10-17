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

    public UserController(UserService userService) {
        this.userService = userService;
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

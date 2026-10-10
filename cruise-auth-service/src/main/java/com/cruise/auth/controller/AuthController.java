package com.cruise.auth.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.common.enums.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok("Cruise Auth Service is running", Map.of(
                "service", "cruise-auth-service",
                "status", "UP",
                "rolesSupported", List.of(Role.values())
        ));
    }
}

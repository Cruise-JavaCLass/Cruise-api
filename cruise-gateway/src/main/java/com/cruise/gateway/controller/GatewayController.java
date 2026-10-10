package com.cruise.gateway.controller;

import com.cruise.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class GatewayController {

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getSystemStatus() {
        return ApiResponse.ok("Cruise Management System Gateway is running", Map.of(
                "service", "cruise-gateway",
                "version", "1.0.0-SNAPSHOT",
                "status", "UP",
                "services", Map.of(
                        "auth-service", "http://localhost:8081",
                        "itinerary-service", "http://localhost:8082",
                        "activity-service", "http://localhost:8083",
                        "pos-service", "http://localhost:8084"
                )
        ));
    }
}

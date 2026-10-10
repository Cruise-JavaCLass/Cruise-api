package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/pos")
public class PosController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok("Cruise POS and Cashless Account Service is running", Map.of(
                "service", "cruise-pos-service",
                "status", "UP",
                "supportsOfflineSync", true
        ));
    }
}

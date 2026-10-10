package com.cruise.activity.controller;

import com.cruise.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok("Cruise Activity and Shore Excursion Service is running", Map.of(
                "service", "cruise-activity-service",
                "status", "UP"
        ));
    }
}

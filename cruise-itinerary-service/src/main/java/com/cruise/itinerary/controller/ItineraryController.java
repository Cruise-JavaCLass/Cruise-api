package com.cruise.itinerary.controller;

import com.cruise.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/itinerary")
public class ItineraryController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok("Cruise Itinerary Service is running", Map.of(
                "service", "cruise-itinerary-service",
                "status", "UP"
        ));
    }
}

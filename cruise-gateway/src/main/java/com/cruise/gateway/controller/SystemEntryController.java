package com.cruise.gateway.controller;

import com.cruise.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller điểm khởi chạy (Entry Point), cung cấp thông tin kiểm tra sức khỏe và trạng thái hệ thống
 */
@RestController
@RequestMapping("/api")
public class SystemEntryController {

    private final long startTime = System.currentTimeMillis();

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("projectName", "Cruise Activity and Service Management System");
        info.put("module", "Backend Gateway Entry Point");
        info.put("version", "1.0.0-SNAPSHOT");
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("javaVendor", System.getProperty("java.vendor"));
        info.put("jiraStory", "KAN-37");
        info.put("jiraTask", "KAN-39");
        return ApiResponse.ok("System information retrieved successfully", info);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> getHealth() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        long uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000;

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("serverTime", Instant.now().toString());
        health.put("uptimeSeconds", uptimeSeconds);
        health.put("jvmUptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("totalMB", totalMemory);
        memory.put("usedMB", usedMemory);
        memory.put("freeMB", freeMemory);
        health.put("memory", memory);

        return ApiResponse.ok("Application is healthy and running", health);
    }

    @GetMapping("/services")
    public ApiResponse<Map<String, Object>> getServicesMap() {
        Map<String, Object> services = new LinkedHashMap<>();
        services.put("cruise-gateway", "http://localhost:8080");
        services.put("cruise-auth-service", "http://localhost:8081");
        services.put("cruise-itinerary-service", "http://localhost:8082");
        services.put("cruise-activity-service", "http://localhost:8083");
        services.put("cruise-pos-service", "http://localhost:8084");
        return ApiResponse.ok("Registered microservices topology", services);
    }
}

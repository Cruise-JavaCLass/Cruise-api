package com.cruise.activity.controller;

import com.cruise.activity.dto.CreateTourProviderRequest;
import com.cruise.activity.dto.TourProviderResponse;
import com.cruise.activity.dto.UpdateTourProviderRequest;
import com.cruise.activity.exception.AccessDeniedException;
import com.cruise.activity.service.TourProviderService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tour-providers")
@RequiredArgsConstructor
public class TourProviderController {

    private final TourProviderService tourProviderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TourProviderResponse> createTourProvider(
            @RequestHeader(value = "X-Caller-Role", required = false) String callerRole,
            @Valid @RequestBody CreateTourProviderRequest request
    ) {
        verifyProviderManagementAuthorization(callerRole);
        return ApiResponse.ok("Tạo nhà cung cấp thành công",
                tourProviderService.createTourProvider(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TourProviderResponse> updateTourProvider(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTourProviderRequest request
    ) {
        return ApiResponse.ok("Cập nhật nhà cung cấp thành công",
                tourProviderService.updateTourProvider(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<TourProviderResponse> getTourProviderById(@PathVariable UUID id) {
        return ApiResponse.ok("Lấy thông tin nhà cung cấp thành công",
                tourProviderService.getTourProviderById(id));
    }

    @GetMapping
    public ApiResponse<Page<TourProviderResponse>> getTourProviders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String portId
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return ApiResponse.ok("Lấy danh sách nhà cung cấp thành công",
                tourProviderService.getTourProviders(pageable, portId));
    }

    private void verifyProviderManagementAuthorization(String callerRole) {
        if (callerRole == null || !"SHORE_EXCURSION_MANAGER".equalsIgnoreCase(callerRole.trim())) {
            throw new AccessDeniedException("Yêu cầu quyền SHORE_EXCURSION_MANAGER");
        }
    }
}

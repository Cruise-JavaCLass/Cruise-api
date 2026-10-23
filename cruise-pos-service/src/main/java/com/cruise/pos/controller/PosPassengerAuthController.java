package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.IdentifyPassengerRequest;
import com.cruise.pos.dto.PassengerIdentificationResponse;
import com.cruise.pos.service.PosPassengerAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pos/auth")
@RequiredArgsConstructor
@Slf4j
public class PosPassengerAuthController {

    private final PosPassengerAuthService posPassengerAuthService;
    private final com.cruise.pos.service.PosCredentialAuditAndManifestService auditAndManifestService;

    @PostMapping("/identify")
    public ApiResponse<PassengerIdentificationResponse> identify(@Valid @RequestBody IdentifyPassengerRequest request) {
        log.info("Processing POS passenger identification, stationId: {}, cashierId: {}", request.getStationId(), request.getCashierId());
        PassengerIdentificationResponse response = posPassengerAuthService.identify(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/manifest/cache")
    public ApiResponse<com.cruise.pos.dto.CredentialManifestCacheResponse> getManifestCache() {
        log.info("Generating offline credential verification manifest cache");
        return ApiResponse.ok(auditAndManifestService.getActiveManifestCache());
    }
}

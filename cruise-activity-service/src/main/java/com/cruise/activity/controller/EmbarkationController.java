package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.service.EmbarkationService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activities/passengers")
@RequiredArgsConstructor
public class EmbarkationController {

    private final EmbarkationService embarkationService;

    @GetMapping("/embarkation/lookup")
    public ApiResponse<PassengerEmbarkationProfileDto> lookupPassenger(@RequestParam String keyword) {
        return ApiResponse.ok("Xác thực thông tin hành khách thành công",
                embarkationService.lookupPassenger(keyword));
    }

    @PostMapping("/{id}/embarkation/check-in")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<EmbarkationCheckInResponse> checkIn(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) EmbarkationCheckInRequest request
    ) {
        return ApiResponse.ok("Làm thủ tục lên tàu thành công",
                embarkationService.processCheckIn(id, request));
    }

    @PostMapping("/{id}/embarkation/bind-rfid")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<BindRfidResponse> bindRfid(
            @PathVariable Long id,
            @Valid @RequestBody BindRfidRequest request
    ) {
        return ApiResponse.ok("Gán thẻ RFID thành công",
                embarkationService.bindRfid(id, request));
    }

    @GetMapping("/embarkation/summary")
    public ApiResponse<EmbarkationSummaryDto> getEmbarkationSummary(@RequestParam Long tourId) {
        return ApiResponse.ok("Lấy dữ liệu tổng quan tiến độ lên tàu thành công",
                embarkationService.getEmbarkationSummary(tourId));
    }

    @GetMapping(value = "/embarkation/manifest/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportBorderControlManifest(@RequestParam Long tourId) {
        byte[] csvData = embarkationService.exportBorderControlManifestCsv(tourId);
        String fileName = String.format("border_control_manifest_tour_%d.csv", tourId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csvData);
    }
}

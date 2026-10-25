package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.enums.VoyageFinancialStatus;
import com.cruise.pos.service.VoyageAnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/voyages")
@PreAuthorize("hasAnyRole('OPERATIONS_MANAGER', 'SYSTEM_ADMIN', 'EXECUTIVE_CONTROLLER', 'EXECUTIVE_DIRECTOR', 'ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class VoyageAnalyticsController {

    private final VoyageAnalyticsService voyageAnalyticsService;

    @PostMapping("/closure")
    public ResponseEntity<ApiResponse<VoyageFinancialClosureDto>> initiateClosure(
            @Valid @RequestBody InitiateVoyageClosureRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "EXECUTIVE_CONTROLLER";
        VoyageFinancialClosureDto dto = voyageAnalyticsService.initiateVoyageClosure(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Voyage financial closure initiated", dto));
    }

    @PostMapping("/{voyageNumber}/finalize")
    public ResponseEntity<ApiResponse<VoyageFinancialClosureDto>> finalizeClosure(
            @PathVariable String voyageNumber,
            @Valid @RequestBody FinalizeVoyageClosureRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "EXECUTIVE_DIRECTOR";
        VoyageFinancialClosureDto dto = voyageAnalyticsService.finalizeVoyageClosure(voyageNumber, request, actor);
        return ResponseEntity.ok(ApiResponse.ok("Voyage financially closed", dto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<VoyageFinancialClosureDto>>> searchVoyages(
            @RequestParam(required = false) VoyageFinancialStatus status,
            @RequestParam(required = false) String shipId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<VoyageFinancialClosureDto> page = voyageAnalyticsService.searchVoyages(status, shipId, search, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{voyageNumber}")
    public ResponseEntity<ApiResponse<VoyageFinancialClosureDto>> getVoyage(@PathVariable String voyageNumber) {
        VoyageFinancialClosureDto dto = voyageAnalyticsService.getVoyageClosure(voyageNumber);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping("/executive/summary")
    public ResponseEntity<ApiResponse<ExecutiveVoyageAnalyticsSummary>> getExecutiveSummary() {
        ExecutiveVoyageAnalyticsSummary summary = voyageAnalyticsService.getExecutiveSummary();
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/{voyageNumber}/booklet.pdf")
    public ResponseEntity<byte[]> downloadPdfBooklet(@PathVariable String voyageNumber) {
        byte[] pdfBytes = voyageAnalyticsService.generateExecutivePdfBooklet(voyageNumber);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"voyage-executive-summary-" + voyageNumber + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @PostMapping("/{voyageNumber}/dispatch-email")
    public ResponseEntity<ApiResponse<String>> dispatchEmail(
            @PathVariable String voyageNumber,
            @Valid @RequestBody DispatchExecutiveSummaryEmailRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "EXECUTIVE_CONTROLLER";
        voyageAnalyticsService.dispatchExecutiveEmail(voyageNumber, request, actor);
        return ResponseEntity.ok(ApiResponse.ok("Executive booklet successfully queued for delivery to " + request.recipientEmail(), voyageNumber));
    }
}

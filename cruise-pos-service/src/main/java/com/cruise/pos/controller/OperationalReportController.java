package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.CreateExportJobRequest;
import com.cruise.pos.dto.OperationalReportQuery;
import com.cruise.pos.dto.OperationalReportResponse;
import com.cruise.pos.entity.ReportExportJob;
import com.cruise.pos.enums.ExportFormat;
import com.cruise.pos.enums.ExportJobStatus;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.ReportDimension;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.service.OperationalReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/pos/reports")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'SYSTEM_ADMIN')")
public class OperationalReportController {

    private final OperationalReportService reportService;

    @GetMapping("/revenue")
    public ApiResponse<OperationalReportResponse> getRevenueReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) ReportDimension dimension
    ) {
        OperationalReportQuery query = OperationalReportQuery.builder()
                .startDate(startDate)
                .endDate(endDate)
                .outletId(outletId)
                .paymentMethod(paymentMethod)
                .dimension(dimension)
                .build();

        log.info("Generating revenue operational report: {}", query);
        OperationalReportResponse response = reportService.generateOperationalReport(query);
        return ApiResponse.ok(response);
    }

    @PostMapping("/exports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportExportJob> createExportJob(@Valid @RequestBody CreateExportJobRequest request) {
        log.info("Requesting report export job: format={}, title={}", request.getFormat(), request.getReportTitle());
        ReportExportJob job = reportService.createExportJob(request);
        return ApiResponse.ok("Tạo tác vụ xuất báo cáo thành công", job);
    }

    @GetMapping("/exports")
    public ApiResponse<Page<ReportExportJob>> getExportJobs(Pageable pageable) {
        return ApiResponse.ok(reportService.getExportJobs(pageable));
    }

    @GetMapping("/exports/{jobId}/download**")
    public ResponseEntity<byte[]> downloadExport(@PathVariable String jobId) {
        ReportExportJob job = reportService.getExportJobById(jobId);
        if (job.getStatus() != ExportJobStatus.COMPLETED || job.getFileData() == null) {
            throw new ResourceNotFoundException("Báo cáo chưa sẵn sàng để tải hoặc không tìm thấy file");
        }
        String contentType = switch (job.getFormat()) {
            case CSV -> "text/csv; charset=UTF-8";
            case EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case PDF -> "application/pdf";
        };
        String ext = job.getFormat() == ExportFormat.CSV ? ".csv" : (job.getFormat() == ExportFormat.EXCEL ? ".xlsx" : ".pdf");
        String filename = "report-" + job.getJobId() + ext;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(job.getFileData());
    }
}

package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.CreateExportJobRequest;
import com.cruise.pos.dto.OperationalReportQuery;
import com.cruise.pos.dto.OperationalReportResponse;
import com.cruise.pos.entity.ReportExportJob;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.ReportDimension;
import com.cruise.pos.service.OperationalReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/pos/reports")
@RequiredArgsConstructor
@Slf4j
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
}

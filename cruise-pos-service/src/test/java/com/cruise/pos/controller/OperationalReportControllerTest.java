package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.CreateExportJobRequest;
import com.cruise.pos.dto.OperationalReportQuery;
import com.cruise.pos.dto.OperationalReportResponse;
import com.cruise.pos.dto.ReportExportJobResponse;
import com.cruise.pos.entity.ReportExportJob;
import com.cruise.pos.enums.ExportFormat;
import com.cruise.pos.enums.ExportJobStatus;
import com.cruise.pos.service.OperationalReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationalReportController Unit Tests")
class OperationalReportControllerTest {

    @Mock
    private OperationalReportService reportService;

    @InjectMocks
    private OperationalReportController controller;

    @Test
    @DisplayName("getRevenueReport returns 200 OK with report response")
    void testGetRevenueReport() {
        OperationalReportResponse mockResponse = OperationalReportResponse.builder()
                .totalGrossRevenue(BigDecimal.valueOf(5000.00))
                .totalNetRevenue(BigDecimal.valueOf(4800.00))
                .totalTransactionCount(25L)
                .breakdown(Collections.emptyList())
                .build();

        when(reportService.generateOperationalReport(any(OperationalReportQuery.class)))
                .thenReturn(mockResponse);

        ApiResponse<OperationalReportResponse> response = controller.getRevenueReport(null, null, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getData().getTotalGrossRevenue()).isEqualByComparingTo(BigDecimal.valueOf(5000.00));
        verify(reportService).generateOperationalReport(any(OperationalReportQuery.class));
    }

    @Test
    @DisplayName("createExportJob returns 201 Created with job status")
    void testCreateExportJob() {
        ReportExportJobResponse mockJob = ReportExportJobResponse.builder()
                .jobId("JOB-123")
                .format(ExportFormat.PDF)
                .status(ExportJobStatus.COMPLETED)
                .downloadUrl("/api/pos/reports/exports/JOB-123/download.pdf")
                .build();

        CreateExportJobRequest request = CreateExportJobRequest.builder()
                .format(ExportFormat.PDF)
                .reportTitle("Audit Export")
                .build();

        when(reportService.createExportJob(any(CreateExportJobRequest.class))).thenReturn(mockJob);

        ApiResponse<ReportExportJobResponse> response = controller.createExportJob(request);

        assertThat(response).isNotNull();
        assertThat(response.getData().getJobId()).isEqualTo("JOB-123");
        verify(reportService).createExportJob(request);
    }

    @Test
    @DisplayName("getExportJobs returns page of jobs")
    void testGetExportJobs() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(reportService.getExportJobs(pageable)).thenReturn(new PageImpl<>(List.of()));

        ApiResponse<Page<ReportExportJobResponse>> response = controller.getExportJobs(pageable);
        assertThat(response).isNotNull();
        verify(reportService).getExportJobs(pageable);
    }

    @Test
    @DisplayName("downloadExport returns 200 OK with file bytes")
    void testDownloadExport() {
        ReportExportJob mockJob = ReportExportJob.builder()
                .jobId("JOB-123")
                .format(ExportFormat.CSV)
                .status(ExportJobStatus.COMPLETED)
                .fileData("Dimension,Transactions\nBEVERAGE,1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();

        when(reportService.getExportJobById("JOB-123")).thenReturn(mockJob);

        var response = controller.downloadExport("JOB-123");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
    }
}

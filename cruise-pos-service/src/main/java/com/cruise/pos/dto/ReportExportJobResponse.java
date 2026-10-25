package com.cruise.pos.dto;

import com.cruise.pos.enums.ExportFormat;
import com.cruise.pos.enums.ExportJobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportExportJobResponse {
    private Long id;
    private String jobId;
    private ExportFormat format;
    private ExportJobStatus status;
    private String reportTitle;
    private String downloadUrl;
    private Long fileSizeBytes;
    private String errorMessage;
    private Instant createdAt;
    private Instant completedAt;
}

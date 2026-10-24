package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.ExportFormat;
import com.cruise.pos.enums.ExportJobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "pos_report_export_jobs", indexes = {
        @Index(name = "idx_job_status", columnList = "status"),
        @Index(name = "idx_job_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportExportJob extends BaseEntity {

    @Column(name = "job_id", nullable = false, unique = true, length = 64)
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 32)
    private ExportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private ExportJobStatus status = ExportJobStatus.PENDING;

    @Column(name = "report_title", nullable = false, length = 150)
    private String reportTitle;

    @Column(name = "download_url", length = 255)
    private String downloadUrl;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "file_data")
    private byte[] fileData;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "completed_at")
    private Instant completedAt;
}

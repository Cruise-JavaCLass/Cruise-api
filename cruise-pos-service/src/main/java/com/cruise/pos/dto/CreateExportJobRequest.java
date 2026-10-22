package com.cruise.pos.dto;

import com.cruise.pos.enums.ExportFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExportJobRequest {

    @NotNull(message = "Export format must not be null")
    private ExportFormat format;

    private String reportTitle;

    private OperationalReportQuery query;
}

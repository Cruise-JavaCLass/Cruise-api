package com.cruise.pos.dto;

import com.cruise.pos.enums.DiscrepancyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDiscrepancyRequest {
    @NotNull(message = "Trạng thái xử lý không được để trống")
    private DiscrepancyStatus status;

    @NotBlank(message = "Ghi chú giải trình không được để trống")
    private String resolutionNotes;

    private String resolvedBy;
}

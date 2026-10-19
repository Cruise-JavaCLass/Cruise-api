package com.cruise.pos.dto;

import com.cruise.pos.enums.DisputeStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDisputeRequest {

    @NotNull(message = "Trạng thái quyết định không được để trống")
    private DisputeStatus decisionStatus; // APPROVED_ADJUSTED or REJECTED

    private BigDecimal approvedRefundAmount;

    private String reviewerId;

    private String reviewerNote;
}

package com.cruise.pos.dto;

import com.cruise.pos.enums.DisputeReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class CreateFolioDisputeRequest {

    @NotNull(message = "Lý do khiếu nại không được để trống")
    private DisputeReason reason;

    @DecimalMin(value = "0.01", message = "Số tiền khiếu nại phải lớn hơn 0")
    private BigDecimal disputeAmount;

    @NotBlank(message = "Ghi chú khiếu nại không được để trống")
    private String disputeNote;
}

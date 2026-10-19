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
public class CreateDisputeRequest {

    @NotBlank(message = "Mã giao dịch không được để trống")
    private String transactionId;

    @NotBlank(message = "Mã Folio không được để trống")
    private String folioId;

    @NotBlank(message = "Số cabin không được để trống")
    private String cabinNumber;

    private String passengerId;
    private String passengerName;

    @NotNull(message = "Lý do khiếu nại không được để trống")
    private DisputeReason reason;

    @NotNull(message = "Số tiền khiếu nại không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền khiếu nại phải lớn hơn 0")
    private BigDecimal disputeAmount;

    @NotBlank(message = "Ghi chú giải thích của hành khách không được để trống")
    private String passengerNote;

    private String evidenceUrl;
}

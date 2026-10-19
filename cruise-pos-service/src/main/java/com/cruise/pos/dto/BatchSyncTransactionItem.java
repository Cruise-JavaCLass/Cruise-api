package com.cruise.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSyncTransactionItem {

    @NotBlank(message = "Mã giao dịch POS không được để trống")
    private String posTransactionId;

    @NotBlank(message = "Mã thiết bị POS không được để trống")
    private String terminalId;

    @NotBlank(message = "Khu vực dịch vụ không được để trống")
    private String serviceArea;

    @NotBlank(message = "Số cabin không được để trống")
    private String cabinNumber;

    private String passengerName;

    @NotNull(message = "Số tiền giao dịch không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0")
    private BigDecimal amount;

    @Builder.Default
    private String currency = "USD";

    @NotNull(message = "Thời điểm giao dịch không được để trống")
    private Instant transactionTime;

    private String idempotencyKey;
    private String itemsSummary;
}

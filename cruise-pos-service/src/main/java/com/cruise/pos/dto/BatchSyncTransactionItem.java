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
    @jakarta.validation.constraints.Size(max = 64, message = "Mã giao dịch POS tối đa 64 ký tự")
    private String posTransactionId;

    @NotBlank(message = "Mã thiết bị POS không được để trống")
    @jakarta.validation.constraints.Size(max = 64, message = "Mã thiết bị POS tối đa 64 ký tự")
    private String terminalId;

    @NotBlank(message = "Khu vực dịch vụ không được để trống")
    @jakarta.validation.constraints.Size(max = 64, message = "Khu vực dịch vụ tối đa 64 ký tự")
    private String serviceArea;

    @NotBlank(message = "Số cabin không được để trống")
    @jakarta.validation.constraints.Size(max = 32, message = "Số cabin tối đa 32 ký tự")
    private String cabinNumber;

    @jakarta.validation.constraints.Size(max = 128, message = "Tên hành khách tối đa 128 ký tự")
    private String passengerName;

    @NotNull(message = "Số tiền giao dịch không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0")
    private BigDecimal amount;

    @Builder.Default
    @jakarta.validation.constraints.Size(max = 8, message = "Loại tiền tệ tối đa 8 ký tự")
    private String currency = "USD";

    @NotNull(message = "Thời điểm giao dịch không được để trống")
    private Instant transactionTime;

    @jakarta.validation.constraints.Size(max = 64, message = "Idempotency key tối đa 64 ký tự")
    private String idempotencyKey;

    @jakarta.validation.constraints.Size(max = 512, message = "Tóm tắt mặt hàng tối đa 512 ký tự")
    private String itemsSummary;
}

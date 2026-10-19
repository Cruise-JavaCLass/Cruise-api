package com.cruise.pos.dto;

import com.cruise.pos.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransactionRequest {

    private String transactionUuid;

    private Long shiftId;

    @NotBlank(message = "Trạm POS (stationId) không được để trống")
    private String stationId;

    @NotBlank(message = "Mã thu ngân (cashierId) không được để trống")
    private String cashierId;

    private Long passengerId;

    private String cabinNumber;

    private String guestName;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod paymentMethod;

    @DecimalMin(value = "0.0", inclusive = true)
    @Builder.Default
    private BigDecimal tax = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", inclusive = true)
    @Builder.Default
    private BigDecimal gratuity = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", inclusive = true)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    private String notes;

    @NotEmpty(message = "Giỏ hàng phải có ít nhất 1 mặt hàng")
    @Valid
    private List<TransactionItemRequest> items;
}

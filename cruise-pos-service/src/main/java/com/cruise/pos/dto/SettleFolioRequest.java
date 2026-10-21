package com.cruise.pos.dto;

import com.cruise.pos.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Yêu cầu tất toán folio. Chỉ chấp nhận thanh toán bằng tiền mặt hoặc thẻ,
 * không cho phép trả nợ bằng chính folio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettleFolioRequest {

    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Số tiền khách đưa không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền khách đưa phải lớn hơn 0")
    @Digits(integer = 10, fraction = 2, message = "Số tiền chỉ được có tối đa 2 chữ số thập phân")
    private BigDecimal amountTendered;

    @Size(max = 512, message = "Ghi chú không được vượt quá 512 ký tự")
    private String notes;

    @Builder.Default
    private boolean forceWithHold = false;
}

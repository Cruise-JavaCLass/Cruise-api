package com.cruise.pos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoidTransactionRequest {

    @NotBlank(message = "Lý do hủy (voidReason) không được để trống")
    private String voidReason;

    @NotBlank(message = "Mã người thực hiện (performedBy) không được để trống")
    private String performedBy;

    private String managerPin;
}

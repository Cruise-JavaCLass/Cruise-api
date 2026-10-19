package com.cruise.pos.dto;

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
public class ProductModifierDto {
    private Long id;
    private Long productId;

    @NotBlank(message = "Tên modifier không được để trống")
    private String name;

    @NotNull(message = "Đơn giá điều chỉnh không được để trống")
    @DecimalMin(value = "0.00", message = "Đơn giá điều chỉnh phải >= 0")
    private BigDecimal priceDelta;

    private Boolean isRequired;
    private Integer minSelection;
    private Integer maxSelection;
    private Integer displayOrder;
    private String status;
}

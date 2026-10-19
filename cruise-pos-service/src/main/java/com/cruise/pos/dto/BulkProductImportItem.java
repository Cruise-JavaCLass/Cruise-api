package com.cruise.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class BulkProductImportItem {

    private String outletCode;
    private String categoryCode;

    @NotBlank(message = "Mã SKU không được để trống")
    private String sku;

    private String barcode;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    private String description;

    @NotNull(message = "Giá bán không được để trống")
    @DecimalMin(value = "0.00", message = "Giá bán phải >= 0")
    private BigDecimal basePrice;

    private BigDecimal costPrice;
    private String taxCategory;
    private Integer stockQuantity;
    private String imageUrl;
    private Boolean happyHourEligible;
    private BigDecimal happyHourDiscountPercent;
    private List<ProductModifierDto> modifiers;
}

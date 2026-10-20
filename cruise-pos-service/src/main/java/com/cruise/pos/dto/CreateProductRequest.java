package com.cruise.pos.dto;

import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.enums.TaxCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
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
public class CreateProductRequest {

    private Long outletId;

    @NotNull(message = "Danh mục không được để trống")
    private Long categoryId;

    @NotBlank(message = "Mã SKU không được để trống")
    private String sku;

    private String barcode;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    private String description;

    @NotNull(message = "Giá bán cơ sở không được để trống")
    @DecimalMin(value = "0.00", message = "Giá bán phải >= 0")
    private BigDecimal basePrice;

    private BigDecimal costPrice;

    @Builder.Default
    private TaxCategory taxCategory = TaxCategory.STANDARD_VAT;

    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @Builder.Default
    private Integer stockQuantity = 100;

    private String imageUrl;
    private Integer displayOrder;
    private Boolean happyHourEligible;

    @DecimalMin(value = "0.00", message = "Giảm giá Happy Hour phải >= 0")
    @DecimalMax(value = "100.00", message = "Giảm giá Happy Hour phải <= 100")
    private BigDecimal happyHourDiscountPercent;

    @Valid
    private List<@Valid ProductModifierDto> modifiers;
}

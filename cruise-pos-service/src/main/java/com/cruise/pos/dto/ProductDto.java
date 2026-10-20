package com.cruise.pos.dto;

import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.enums.TaxCategory;
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
public class ProductDto {
    private Long id;
    private Long outletId;
    private Long categoryId;
    private String categoryName;
    private String sku;
    private String barcode;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private BigDecimal costPrice;
    private TaxCategory taxCategory;
    private ProductStatus status;
    private Boolean isAvailable;
    private Boolean is86ed;
    private Integer stockQuantity;
    private String imageUrl;
    private Integer displayOrder;
    private Boolean happyHourEligible;
    private BigDecimal happyHourDiscountPercent;
    private Instant createdAt;
    private Instant updatedAt;
}

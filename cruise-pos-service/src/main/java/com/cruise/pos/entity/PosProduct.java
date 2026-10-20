package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.enums.TaxCategory;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pos_products", indexes = {
        @Index(name = "idx_pos_product_sku", columnList = "sku", unique = true),
        @Index(name = "idx_pos_product_barcode", columnList = "barcode"),
        @Index(name = "idx_pos_product_outlet", columnList = "outlet_id"),
        @Index(name = "idx_pos_product_category", columnList = "category_id"),
        @Index(name = "idx_pos_product_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosProduct extends BaseEntity {

    @Column(name = "outlet_id")
    private Long outletId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "sku", nullable = false, unique = true, length = 64)
    private String sku;

    @Column(name = "barcode", length = 64)
    private String barcode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "tax_category", nullable = false, length = 32)
    private TaxCategory taxCategory = TaxCategory.STANDARD_VAT;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 32)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Builder.Default
    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    @Builder.Default
    @Column(name = "is_86ed", nullable = false)
    private Boolean is86ed = false;

    @Builder.Default
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 100;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Builder.Default
    @Column(name = "happy_hour_eligible", nullable = false)
    private Boolean happyHourEligible = true;

    @Builder.Default
    @Column(name = "happy_hour_discount_percent", precision = 5, scale = 2)
    private BigDecimal happyHourDiscountPercent = BigDecimal.ZERO;
}

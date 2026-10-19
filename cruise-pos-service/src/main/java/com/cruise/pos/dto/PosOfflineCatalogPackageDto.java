package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosOfflineCatalogPackageDto {
    private String version;
    private String etag;
    private Instant exportedAt;
    private Long outletId;

    @Builder.Default
    private List<OutletDto> outlets = new ArrayList<>();

    @Builder.Default
    private List<CategoryDto> categories = new ArrayList<>();

    @Builder.Default
    private List<ProductDetailDto> products = new ArrayList<>();

    @Builder.Default
    private List<HappyHourRuleDto> happyHourRules = new ArrayList<>();

    @Builder.Default
    private BigDecimal defaultServiceChargeRate = new BigDecimal("18.00");

    @Builder.Default
    private BigDecimal standardPortVatRate = new BigDecimal("10.00");
}

package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosDeltaCatalogChangefeedDto {
    private String fromVersionHash;
    private String currentVersionHash;
    private Instant syncTimestamp;
    private Long outletId;
    private boolean requiresFullSnapshot;

    @Builder.Default
    private List<ProductDetailDto> updatedProducts = new ArrayList<>();

    @Builder.Default
    private List<Long> deletedProductIds = new ArrayList<>();

    @Builder.Default
    private List<CategoryDto> updatedCategories = new ArrayList<>();

    @Builder.Default
    private List<HappyHourRuleDto> updatedHappyHourRules = new ArrayList<>();
}

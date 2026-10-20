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
public class CategoryDto {
    private Long id;
    private Long outletId;
    private String code;
    private String name;
    private Long parentId;
    private Integer displayOrder;
    private Boolean isActive;
    private String icon;
    private String color;
    private String description;
    @Builder.Default
    private List<CategoryDto> children = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
}

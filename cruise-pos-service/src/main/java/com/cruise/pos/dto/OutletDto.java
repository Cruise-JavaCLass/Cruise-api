package com.cruise.pos.dto;

import com.cruise.pos.enums.OutletType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutletDto {
    private Long id;
    private String code;
    private String name;
    private OutletType outletType;
    private String location;
    private String openTime;
    private String closeTime;
    private Boolean isActive;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}

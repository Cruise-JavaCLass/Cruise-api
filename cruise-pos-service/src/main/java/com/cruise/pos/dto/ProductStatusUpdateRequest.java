package com.cruise.pos.dto;

import com.cruise.pos.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStatusUpdateRequest {
    private ProductStatus status;
    private Boolean is86ed;
    private Boolean isAvailable;
    private String reason;
    private String staffId;
}

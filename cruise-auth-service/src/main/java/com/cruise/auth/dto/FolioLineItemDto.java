package com.cruise.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO đại diện cho một dòng bút toán chi tiêu trên tài khoản Folio của hành khách (KAN-721).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolioLineItemDto {
    private String transactionId;
    private String description;
    private BigDecimal amount;
    private String category;
    private LocalDateTime postedAt;
}

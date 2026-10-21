package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolioTransactionDto {

    private Long id;
    private String transactionUuid;
    private Instant time;
    private String locationCounter;
    private String posCode;
    private String swipedBy;
    private BigDecimal amount;
    private String currency;
    private String category;
    private boolean complimentary;
    private String status;
    private List<ItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDto {
        private String itemCode;
        private String itemName;
        private String category;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private boolean complimentary;
    }
}

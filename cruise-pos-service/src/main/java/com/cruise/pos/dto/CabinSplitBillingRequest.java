package com.cruise.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinSplitBillingRequest {

    @NotBlank(message = "cabinNumber is required")
    private String cabinNumber;

    @NotBlank(message = "stationId is required")
    private String stationId;

    @NotBlank(message = "cashierId is required")
    private String cashierId;

    private Long shiftId;

    private String transactionUuid;

    private String notes;

    @NotEmpty(message = "splitShares cannot be empty")
    @Valid
    private List<CabinSplitShareRequest> splitShares;

    @Valid
    private List<TransactionItemRequest> items;
}

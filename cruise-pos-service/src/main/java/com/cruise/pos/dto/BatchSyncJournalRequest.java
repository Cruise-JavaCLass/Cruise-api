package com.cruise.pos.dto;

import jakarta.validation.Valid;
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
public class BatchSyncJournalRequest {

    private String terminalId;
    private String syncOperator;

    @NotEmpty(message = "Danh sách giao dịch nạp không được để trống")
    @Valid
    private List<BatchSyncTransactionItem> transactions;
}

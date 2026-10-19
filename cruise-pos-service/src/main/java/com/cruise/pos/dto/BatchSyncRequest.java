package com.cruise.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSyncRequest {

    @NotBlank(message = "DeviceId không được để trống")
    private String deviceId;

    private Instant clientSyncTimestamp;

    @NotEmpty(message = "Danh sách giao dịch offline không được để trống")
    @Valid
    private List<CreateTransactionRequest> transactions;
}

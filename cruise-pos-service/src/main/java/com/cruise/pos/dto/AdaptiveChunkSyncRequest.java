package com.cruise.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class AdaptiveChunkSyncRequest {

    @NotBlank(message = "DeviceId must not be blank")
    private String deviceId;

    @Min(value = 1, message = "Chunk index must be >= 1")
    private Integer chunkIndex;

    @Min(value = 1, message = "Total chunks must be >= 1")
    private Integer totalChunks;

    @Min(value = 1, message = "Batch size must be at least 1")
    @Max(value = 100, message = "Batch size cannot exceed 100 under maritime link policy")
    private Integer chunkSize;

    private Long reportedLatencyMs;

    private Instant clientSyncTimestamp;

    @NotEmpty(message = "Transactions chunk must not be empty")
    @Size(max = 100, message = "Transactions chunk cannot exceed 100 items under maritime link policy")
    @Valid
    private List<CreateTransactionRequest> transactions;
}

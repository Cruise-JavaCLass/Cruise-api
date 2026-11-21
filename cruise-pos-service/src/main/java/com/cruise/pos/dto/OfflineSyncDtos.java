package com.cruise.pos.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class OfflineSyncDtos {
    private OfflineSyncDtos() {}
    public record BatchRequest(@NotEmpty @Size(max=250) List<@NotNull @Valid OfflineTransactionDto> transactions) {}
    public record RecordResult(String idempotencyKey,String transactionReference,String status,Long serverTransactionId,Long conflictId,String errorCode) {}
    public record FolioState(String cabinNumber,String status,BigDecimal currentBalance,BigDecimal creditLimit) {}
    public record BatchResult(Long batchId,String terminalId,String batchNumber,String syncStatus,int totalRecords,
        int successfulCount,int duplicateCount,int failedCount,List<RecordResult> results,List<FolioState> serverFolios,Instant serverTime) {}
    public enum ResolutionAction { APPROVE, REJECT }
    public record ResolveRequest(@NotNull ResolutionAction action,@NotBlank @Size(max=512) String reason) {}
    public record HeartbeatRequest(@Size(max=32) String firmwareVersion) {}
    public record HeartbeatResult(String terminalId,String stationId,Instant lastSeenAt) {}
}

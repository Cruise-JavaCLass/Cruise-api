package com.cruise.pos.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record OfflineTransactionDto(@NotNull UUID idempotencyKey,
    @NotBlank @Size(max=128) String transactionReference,@NotNull @Valid CreateTransactionRequest transaction) {}

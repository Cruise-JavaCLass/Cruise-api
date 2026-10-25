package com.cruise.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TerminalLockCommand(
        @NotBlank String terminalId,
        @NotNull String command,
        String issuedBy,
        @Size(max = 512) String justification
) {
}

package com.cruise.pos.dto;

import jakarta.validation.constraints.Size;

/**
 * Request payload containing optional client-supplied justification for terminal lock actions (KAN-607).
 */
public record TerminalLockActionRequest(
        @Size(max = 512)
        String justification
) {
}

package com.cruise.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for a manager override action (KAN-606).
 *
 * @param managerId     identifier of the approving manager
 * @param managerPin    PIN of the approving manager (plain-text; hashed server-side)
 * @param action        the action being overridden (e.g. "APPLY_DISCOUNT", "PROCESS_REFUND")
 * @param targetId      optional ID of the transaction / record being overridden
 * @param justification business justification for the override (max 512 chars)
 */
public record ManagerOverrideRequest(

        @NotBlank
        @Size(max = 64)
        String managerId,

        @NotBlank
        String managerPin,

        @NotBlank
        @Size(max = 512)
        String action,

        Long targetId,

        @NotBlank
        @Size(max = 512)
        String justification
) {
}

package com.cruise.activity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistRfidCardRequest {

    @NotBlank
    private String cardUid;

    @NotBlank
    private String reason; // e.g. "PASSENGER_LOST", "SECURITY_COMPROMISED", "STOLEN"

    private String reportedBy;

    // Optional replacement pairing immediately
    private String replacementCardUid;
}

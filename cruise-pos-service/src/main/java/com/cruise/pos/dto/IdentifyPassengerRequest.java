package com.cruise.pos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentifyPassengerRequest {

    @NotBlank(message = "Raw credential must not be blank")
    private String rawCredential;

    private String stationId;

    private String cashierId;
}

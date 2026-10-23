package com.cruise.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 128, message = "Raw credential must not exceed 128 characters")
    private String rawCredential;

    @Size(max = 64, message = "Station ID must not exceed 64 characters")
    private String stationId;

    @Size(max = 64, message = "Cashier ID must not exceed 64 characters")
    private String cashierId;
}

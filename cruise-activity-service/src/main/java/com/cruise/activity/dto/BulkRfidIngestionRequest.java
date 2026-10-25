package com.cruise.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkRfidIngestionRequest {

    @NotBlank
    @Size(max = 50)
    private String batchNumber;

    @Builder.Default
    @Size(max = 30)
    private String cardType = "MIFARE_CLASSIC_1K";

    @NotEmpty
    @Size(max = 5000)
    private List<@Size(max = 64) String> cardUids;

    @Size(max = 500)
    private String notes;
}

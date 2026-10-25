package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkRfidIngestionResponse {

    private String batchNumber;
    private int totalSubmitted;
    private int successfullyIngested;
    private int duplicateSkipped;
    private List<String> skippedUids;
}

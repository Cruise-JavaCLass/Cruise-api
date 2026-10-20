package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportResponse {
    private int totalItems;
    private int successCount;
    private int failedCount;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    private Instant importedAt;
}

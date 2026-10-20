package com.cruise.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCatalogImportRequest {

    private String performedBy;
    private String supplierManifestNumber;

    @NotEmpty(message = "Danh sách sản phẩm nhập không được để trống")
    @Valid
    @Builder.Default
    private List<BulkProductImportItem> products = new ArrayList<>();
}

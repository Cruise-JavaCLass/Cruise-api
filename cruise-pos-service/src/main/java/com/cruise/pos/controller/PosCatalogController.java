package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.service.PosCatalogService;
import com.cruise.pos.service.BarcodeLookupService;
import com.cruise.pos.service.DynamicPricingEngine;
import com.cruise.pos.service.MaritimeTaxEngine;
import com.cruise.pos.service.PosOfflineSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class PosCatalogController {

    private final PosCatalogService catalogService;
    private final BarcodeLookupService barcodeLookupService;
    private final DynamicPricingEngine dynamicPricingEngine;
    private final MaritimeTaxEngine maritimeTaxEngine;
    private final PosOfflineSyncService offlineSyncService;

    @GetMapping("/outlets")
    public ApiResponse<List<OutletDto>> getOutlets() {
        return ApiResponse.ok(catalogService.getOutlets());
    }

    @GetMapping("/outlets/{id}")
    public ApiResponse<OutletDto> getOutletById(@PathVariable Long id) {
        return ApiResponse.ok(catalogService.getOutletById(id));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryDto>> getCategories(@RequestParam(required = false) Long outletId) {
        return ApiResponse.ok(catalogService.getCategories(outletId));
    }

    @GetMapping("/products")
    public ApiResponse<Page<ProductDto>> getProducts(
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "displayOrder") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction
    ) {
        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        return ApiResponse.ok(catalogService.getProducts(outletId, categoryId, status, keyword, pageable));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<ProductDetailDto> getProductById(@PathVariable Long id) {
        return ApiResponse.ok(catalogService.getProductById(id));
    }

    @PostMapping("/products")
    public ResponseEntity<ApiResponse<ProductDetailDto>> createProduct(@Valid @RequestBody CreateProductRequest req) {
        ProductDetailDto created = catalogService.createProduct(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo sản phẩm thành công", created));
    }

    @PutMapping("/products/{id}")
    public ApiResponse<ProductDetailDto> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest req
    ) {
        ProductDetailDto updated = catalogService.updateProduct(id, req);
        return ApiResponse.ok("Cập nhật thông tin sản phẩm thành công", updated);
    }

    @PatchMapping("/products/{id}/status")
    public ApiResponse<ProductDetailDto> updateProductStatus(
            @PathVariable Long id,
            @RequestBody ProductStatusUpdateRequest req
    ) {
        ProductDetailDto updated = catalogService.updateProductStatus(id, req);
        return ApiResponse.ok("Cập nhật trạng thái sản phẩm thành công", updated);
    }

    @GetMapping("/products/lookup")
    public ApiResponse<ProductDetailDto> lookupProduct(
            @RequestParam String code,
            @RequestParam(required = false) Long outletId
    ) {
        ProductDetailDto product = barcodeLookupService.lookupProductByBarcodeOrSku(code, outletId);
        return ApiResponse.ok(product);
    }

    @PostMapping("/pricing/calculate")
    public ApiResponse<CartPriceCalculationResponse> calculatePricing(
            @Valid @RequestBody CartPriceCalculationRequest req
    ) {
        CartPriceCalculationResponse response = maritimeTaxEngine.calculateCartPricing(req);
        return ApiResponse.ok(response);
    }

    @GetMapping("/pricing/happy-hour-rules")
    public ApiResponse<List<HappyHourRuleDto>> getHappyHourRules(@RequestParam(required = false) Long outletId) {
        return ApiResponse.ok(dynamicPricingEngine.getRulesByOutlet(outletId));
    }

    @PostMapping("/pricing/happy-hour-rules")
    public ResponseEntity<ApiResponse<HappyHourRuleDto>> createHappyHourRule(@Valid @RequestBody HappyHourRuleDto req) {
        HappyHourRuleDto created = dynamicPricingEngine.createRule(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo quy tắc Happy Hour thành công", created));
    }

    @PutMapping("/pricing/happy-hour-rules/{id}")
    public ApiResponse<HappyHourRuleDto> updateHappyHourRule(
            @PathVariable Long id,
            @Valid @RequestBody HappyHourRuleDto req
    ) {
        HappyHourRuleDto updated = dynamicPricingEngine.updateRule(id, req);
        return ApiResponse.ok("Cập nhật quy tắc Happy Hour thành công", updated);
    }

    @DeleteMapping("/pricing/happy-hour-rules/{id}")
    public ApiResponse<Void> deleteHappyHourRule(@PathVariable Long id) {
        dynamicPricingEngine.deleteRule(id);
        return ApiResponse.ok("Xóa quy tắc Happy Hour thành công", null);
    }

    @GetMapping("/catalog/offline-package")
    public ResponseEntity<ApiResponse<PosOfflineCatalogPackageDto>> getOfflinePackage(
            @RequestParam(required = false) Long outletId,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch
    ) {
        PosOfflineCatalogPackageDto pkg = offlineSyncService.exportOfflinePackage(outletId);
        String etag = pkg.getEtag();

        if (ifNoneMatch != null && ifNoneMatch.trim().equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .build();
        }

        return ResponseEntity.ok()
                .eTag(etag)
                .body(ApiResponse.ok("Xuất gói catalog offline thành công", pkg));
    }

    @PostMapping("/catalog/bulk-import")
    public ApiResponse<BulkImportResponse> bulkImportProducts(
            @Valid @RequestBody BulkCatalogImportRequest req
    ) {
        BulkImportResponse response = offlineSyncService.bulkImportProducts(req);
        return ApiResponse.ok("Nhập dữ liệu danh mục thành công", response);
    }

    @GetMapping("/catalog/audit-logs")
    public ApiResponse<Page<com.cruise.pos.entity.PosCatalogAuditLog>> getCatalogAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(offlineSyncService.getAuditLogs(pageable));
    }
}

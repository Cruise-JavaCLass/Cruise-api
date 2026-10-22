package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.service.PosCatalogService;
import com.cruise.pos.service.BarcodeLookupService;
import com.cruise.pos.service.DynamicPricingEngine;
import com.cruise.pos.service.MaritimeTaxEngine;
import com.cruise.pos.service.PosOfflineSyncService;
import com.cruise.pos.exception.BusinessRuleException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class PosCatalogController {

    private static final Set<String> SUPPORTED_SORT_FIELDS = Set.of(
            "id", "name", "sku", "barcode", "basePrice", "costPrice",
            "displayOrder", "createdAt", "updatedAt", "status", "stockQuantity",
            "isAvailable", "is86ed"
    );

    private final PosCatalogService catalogService;
    private final BarcodeLookupService barcodeLookupService;
    private final DynamicPricingEngine dynamicPricingEngine;
    private final MaritimeTaxEngine maritimeTaxEngine;
    private final PosOfflineSyncService offlineSyncService;

    /**
     * Returns the active POS outlets in an API response.
     */
    @GetMapping("/outlets")
    public ApiResponse<List<OutletDto>> getOutlets() {
        return ApiResponse.ok(catalogService.getOutlets());
    }

    /**
     * Returns the requested outlet in an API response.
     */
    @GetMapping("/outlets/{id}")
    public ApiResponse<OutletDto> getOutletById(@PathVariable Long id) {
        return ApiResponse.ok(catalogService.getOutletById(id));
    }

    /**
     * Returns the active category tree, optionally filtered by outlet and including shared categories.
     */
    @GetMapping("/categories")
    public ApiResponse<List<CategoryDto>> getCategories(@RequestParam(required = false) Long outletId) {
        return ApiResponse.ok(catalogService.getCategories(outletId));
    }

    /**
     * Returns products matching the optional filters with the requested pagination and sorting.
     */
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
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Hướng sắp xếp không hợp lệ: " + direction + ". Chỉ chấp nhận ASC hoặc DESC");
        }

        if (!SUPPORTED_SORT_FIELDS.contains(sortBy)) {
            throw new BusinessRuleException("Trường sắp xếp không hợp lệ: " + sortBy);
        }

        int pageSize = Math.min(Math.max(1, size), 200);
        int pageNumber = Math.max(0, page);

        Sort sort = Sort.by(sortDirection, sortBy);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        return ApiResponse.ok(catalogService.getProducts(outletId, categoryId, status, keyword, pageable));
    }

    /**
     * Returns the requested product with outlet, category, and modifier details.
     */
    @GetMapping("/products/{id}")
    public ApiResponse<ProductDetailDto> getProductById(@PathVariable Long id) {
        return ApiResponse.ok(catalogService.getProductById(id));
    }

    /**
     * Creates a validated product and returns its details with HTTP 201.
     */
    @PostMapping("/products")
    public ResponseEntity<ApiResponse<ProductDetailDto>> createProduct(@Valid @RequestBody CreateProductRequest req) {
        ProductDetailDto created = catalogService.createProduct(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo sản phẩm thành công", created));
    }

    /**
     * Updates the requested product with validated fields and returns its details.
     */
    @PutMapping("/products/{id}")
    public ApiResponse<ProductDetailDto> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest req
    ) {
        ProductDetailDto updated = catalogService.updateProduct(id, req);
        return ApiResponse.ok("Cập nhật thông tin sản phẩm thành công", updated);
    }

    /**
     * Updates product status and availability, including out-of-stock flags.
     */
    @PatchMapping("/products/{id}/status")
    public ApiResponse<ProductDetailDto> updateProductStatus(
            @PathVariable Long id,
            @RequestBody ProductStatusUpdateRequest req
    ) {
        ProductDetailDto updated = catalogService.updateProductStatus(id, req);
        return ApiResponse.ok("Cập nhật trạng thái sản phẩm thành công", updated);
    }

    /**
     * Looks up a barcode or SKU, optionally by outlet, using the lookup service cache.
     */
    @GetMapping("/products/lookup")
    public ApiResponse<ProductDetailDto> lookupProduct(
            @RequestParam String code,
            @RequestParam(required = false) Long outletId
    ) {
        ProductDetailDto product = barcodeLookupService.lookupProductByBarcodeOrSku(code, outletId);
        return ApiResponse.ok(product);
    }

    /**
     * Returns cart totals with modifiers, discounts, maritime tax, and service charges.
     */
    @PostMapping("/pricing/calculate")
    public ApiResponse<CartPriceCalculationResponse> calculatePricing(
            @Valid @RequestBody CartPriceCalculationRequest req
    ) {
        CartPriceCalculationResponse response = maritimeTaxEngine.calculateCartPricing(req);
        return ApiResponse.ok(response);
    }

    /**
     * Returns active Happy Hour rules for the outlet, including shared rules; null selects all outlets.
     */
    @GetMapping("/pricing/happy-hour-rules")
    public ApiResponse<List<HappyHourRuleDto>> getHappyHourRules(@RequestParam(required = false) Long outletId) {
        return ApiResponse.ok(dynamicPricingEngine.getRulesByOutlet(outletId));
    }

    /**
     * Creates a validated Happy Hour rule and returns it with HTTP 201.
     */
    @PostMapping("/pricing/happy-hour-rules")
    public ResponseEntity<ApiResponse<HappyHourRuleDto>> createHappyHourRule(@Valid @RequestBody HappyHourRuleDto req) {
        HappyHourRuleDto created = dynamicPricingEngine.createRule(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo quy tắc Happy Hour thành công", created));
    }

    /**
     * Applies supplied fields to an existing Happy Hour rule and returns the updated rule.
     */
    @PutMapping("/pricing/happy-hour-rules/{id}")
    public ApiResponse<HappyHourRuleDto> updateHappyHourRule(
            @PathVariable Long id,
            @Valid @RequestBody HappyHourRuleDto req
    ) {
        HappyHourRuleDto updated = dynamicPricingEngine.updateRule(id, req);
        return ApiResponse.ok("Cập nhật quy tắc Happy Hour thành công", updated);
    }

    /**
     * Deletes the requested Happy Hour rule and returns a success response.
     */
    @DeleteMapping("/pricing/happy-hour-rules/{id}")
    public ApiResponse<Void> deleteHappyHourRule(@PathVariable Long id) {
        dynamicPricingEngine.deleteRule(id);
        return ApiResponse.ok("Xóa quy tắc Happy Hour thành công", null);
    }

    /**
     * Exports the offline catalog with an ETag, or returns HTTP 304 when If-None-Match matches.
     */
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

    /**
     * Exports compressed GZIP master data catalog snapshot for bandwidth-constrained terminals.
     */
    @GetMapping("/catalog/offline-package/gzip")
    public ResponseEntity<byte[]> getOfflinePackageGzip(
            @RequestParam(required = false) Long outletId
    ) throws IOException {
        byte[] gzipContent = offlineSyncService.exportGzipCatalogPackage(outletId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(gzipContent);
    }

    /**
     * Returns delta catalog changes since client version hash or timestamp.
     */
    @GetMapping("/catalog/delta")
    public ApiResponse<PosDeltaCatalogChangefeedDto> getDeltaCatalog(
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) String versionHash,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since
    ) {
        PosDeltaCatalogChangefeedDto delta = offlineSyncService.getDeltaChangefeed(outletId, versionHash, since);
        return ApiResponse.ok("Lấy dữ liệu delta cập nhật thành công", delta);
    }

    /**
     * Imports a validated supplier manifest and returns success counts and per-item errors.
     */
    @PostMapping("/catalog/bulk-import")
    public ApiResponse<BulkImportResponse> bulkImportProducts(
            @Valid @RequestBody BulkCatalogImportRequest req
    ) {
        BulkImportResponse response = offlineSyncService.bulkImportProducts(req);
        return ApiResponse.ok("Nhập dữ liệu danh mục thành công", response);
    }

    /**
     * Returns a page of catalog audit entries ordered from newest to oldest.
     */
    @GetMapping("/catalog/audit-logs")
    public ApiResponse<Page<com.cruise.pos.entity.PosCatalogAuditLog>> getCatalogAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(offlineSyncService.getAuditLogs(pageable));
    }
}

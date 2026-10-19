package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.service.PosCatalogService;
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
}

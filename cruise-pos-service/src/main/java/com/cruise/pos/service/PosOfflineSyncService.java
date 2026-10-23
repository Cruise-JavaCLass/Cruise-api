package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.OutletType;
import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.enums.TaxCategory;
import com.cruise.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.GZIPOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosOfflineSyncService {

    private final PosCatalogService catalogService;
    private final DynamicPricingEngine pricingEngine;
    private final PosOutletRepository outletRepository;
    private final PosCategoryRepository categoryRepository;
    private final PosProductRepository productRepository;
    private final PosProductModifierRepository modifierRepository;
    private final PosCatalogAuditLogRepository auditLogRepository;
    private final BarcodeLookupService barcodeLookupService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    /**
     * Builds an offline package of outlets, scoped categories, non-archived products, and active pricing rules.
     */
    @Transactional(readOnly = true)
    public PosOfflineCatalogPackageDto exportOfflinePackage(Long outletId) {
        List<OutletDto> outlets = catalogService.getOutlets();
        List<CategoryDto> categories = catalogService.getCategories(outletId);
        List<HappyHourRuleDto> happyHourRules = pricingEngine.getRulesByOutlet(outletId);

        List<PosProduct> activeProducts = productRepository.findActiveCatalogByOutlet(outletId);
        List<ProductDetailDto> productDtos = new ArrayList<>();

        for (PosProduct p : activeProducts) {
            productDtos.add(catalogService.getProductById(p.getId()));
        }

        PosOfflineCatalogPackageDto pkg = PosOfflineCatalogPackageDto.builder()
                .version("1.0.0")
                .exportedAt(Instant.now())
                .outletId(outletId)
                .outlets(outlets)
                .categories(categories)
                .products(productDtos)
                .happyHourRules(happyHourRules)
                .defaultServiceChargeRate(new BigDecimal("18.00"))
                .standardPortVatRate(new BigDecimal("10.00"))
                .build();

        String etag = generateEtag(pkg);
        pkg.setEtag(etag);

        return pkg;
    }

    /**
     * Generates a GZIP compressed byte array of the full catalog snapshot package.
     */
    @Transactional(readOnly = true)
    public byte[] exportGzipCatalogPackage(Long outletId) throws IOException {
        PosOfflineCatalogPackageDto pkg = exportOfflinePackage(outletId);
        byte[] jsonBytes = objectMapper.writeValueAsBytes(pkg);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
            gzipStream.write(jsonBytes);
        }
        return byteStream.toByteArray();
    }

    /**
     * Calculates delta catalog updates since a given version or timestamp.
     */
    @Transactional(readOnly = true)
    public PosDeltaCatalogChangefeedDto getDeltaChangefeed(Long outletId, String clientVersionHash, Instant since) {
        Instant syncTimestamp = Instant.now();
        PosOfflineCatalogPackageDto currentPackage = exportOfflinePackage(outletId);
        String currentEtag = currentPackage.getEtag();

        if (clientVersionHash != null && clientVersionHash.equals(currentEtag)) {
            return PosDeltaCatalogChangefeedDto.builder()
                    .fromVersionHash(clientVersionHash)
                    .currentVersionHash(currentEtag)
                    .syncTimestamp(syncTimestamp)
                    .outletId(outletId)
                    .requiresFullSnapshot(false)
                    .updatedProducts(Collections.emptyList())
                    .deletedProductIds(Collections.emptyList())
                    .updatedCategories(Collections.emptyList())
                    .updatedHappyHourRules(Collections.emptyList())
                    .build();
        }

        boolean requiresFullSnapshot = (clientVersionHash == null || clientVersionHash.isBlank() || since == null);
        Instant threshold = (since != null) ? since : syncTimestamp.minus(7, ChronoUnit.DAYS);
        List<PosProduct> changedEntities = productRepository.findUpdatedProductsSince(outletId, threshold);

        List<ProductDetailDto> updatedProducts = new ArrayList<>();
        List<Long> deletedProductIds = new ArrayList<>();

        for (PosProduct p : changedEntities) {
            if (p.getStatus() == ProductStatus.ARCHIVED) {
                deletedProductIds.add(p.getId());
            } else {
                updatedProducts.add(catalogService.getProductById(p.getId()));
            }
        }

        if (outletId != null) {
            List<Long> reassignedIds = auditLogRepository.findReassignedProductIdsForOutlet(String.valueOf(outletId), threshold);
            if (reassignedIds != null) {
                for (Long rId : reassignedIds) {
                    if (rId != null && !deletedProductIds.contains(rId)) {
                        deletedProductIds.add(rId);
                    }
                }
            }
        }

        List<CategoryDto> currentCategories = catalogService.getCategories(outletId);
        List<HappyHourRuleDto> happyHourRules = pricingEngine.getRulesByOutlet(outletId);

        return PosDeltaCatalogChangefeedDto.builder()
                .fromVersionHash(clientVersionHash)
                .currentVersionHash(currentEtag)
                .syncTimestamp(syncTimestamp)
                .outletId(outletId)
                .requiresFullSnapshot(requiresFullSnapshot)
                .updatedProducts(updatedProducts)
                .deletedProductIds(deletedProductIds)
                .updatedCategories(currentCategories)
                .updatedHappyHourRules(happyHourRules)
                .build();
    }

    /**
     * Generates a SHA-256 ETag from the serialized offline package content (excluding exportedAt and etag).
     */
    public String generateEtag(PosOfflineCatalogPackageDto pkg) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", pkg.getVersion());
            payload.put("outletId", pkg.getOutletId());
            payload.put("outlets", pkg.getOutlets());
            payload.put("categories", pkg.getCategories());
            payload.put("products", pkg.getProducts());
            payload.put("happyHourRules", pkg.getHappyHourRules());
            payload.put("defaultServiceChargeRate", pkg.getDefaultServiceChargeRate());
            payload.put("standardPortVatRate", pkg.getStandardPortVatRate());

            byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(jsonBytes);
            StringBuilder sb = new StringBuilder("\"");
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            sb.append("\"");
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to generate offline package ETag: {}", e.getMessage());
            return "\"" + Integer.toHexString(Objects.hash(pkg.getOutletId(), pkg.getProducts())) + "\"";
        }
    }

    public String generateEtag(Long outletId, int productCount) {
        return "\"" + (outletId != null ? outletId : 0L) + "-" + productCount + "\"";
    }


    /**
     * Imports products by SKU, creates missing outlets and categories, and reports per-item errors.
     */
    public BulkImportResponse bulkImportProducts(BulkCatalogImportRequest request) {
        int total = request.getProducts().size();
        int successCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();

        Map<String, PosOutlet> outletCache = new HashMap<>();
        outletRepository.findAll().forEach(o -> outletCache.put(o.getCode().toUpperCase(), o));

        Map<String, PosCategory> categoryCache = new HashMap<>();
        categoryRepository.findAll().forEach(c -> categoryCache.put(c.getCode().toUpperCase(), c));

        for (BulkProductImportItem item : request.getProducts()) {
            Map<String, PosOutlet> stagedOutlets = new HashMap<>();
            Map<String, PosCategory> stagedCategories = new HashMap<>();
            try {
                if (transactionTemplate != null) {
                    transactionTemplate.execute(status -> {
                        processImportItem(item, request, outletCache, categoryCache, stagedOutlets, stagedCategories);
                        return null;
                    });
                } else {
                    processImportItem(item, request, outletCache, categoryCache, stagedOutlets, stagedCategories);
                }
                outletCache.putAll(stagedOutlets);
                categoryCache.putAll(stagedCategories);
                successCount++;
            } catch (Exception e) {
                failedCount++;
                errors.add("SKU " + item.getSku() + ": " + e.getMessage());
                log.warn("Bulk import failed for SKU {}: {}", item.getSku(), e.getMessage());
            }
        }

        barcodeLookupService.evictCache();

        log.info("Bulk catalog import finished: Total={}, Success={}, Failed={}", total, successCount, failedCount);

        return BulkImportResponse.builder()
                .totalItems(total)
                .successCount(successCount)
                .failedCount(failedCount)
                .errors(errors)
                .importedAt(Instant.now())
                .build();
    }

    private void processImportItem(
            BulkProductImportItem item,
            BulkCatalogImportRequest request,
            Map<String, PosOutlet> outletCache,
            Map<String, PosCategory> categoryCache,
            Map<String, PosOutlet> stagedOutlets,
            Map<String, PosCategory> stagedCategories
    ) {
        if (item.getSku() == null || item.getSku().isBlank()) {
            throw new IllegalArgumentException("SKU is mandatory");
        }
        if (item.getName() == null || item.getName().isBlank()) {
            throw new IllegalArgumentException("Product name is mandatory for SKU: " + item.getSku());
        }
        if (item.getBasePrice() == null || item.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid price for SKU: " + item.getSku());
        }
        if (item.getCategoryCode() == null || item.getCategoryCode().isBlank()) {
            throw new IllegalArgumentException("Category code is mandatory for SKU: " + item.getSku());
        }

        Long outletId = null;
        if (item.getOutletCode() != null && !item.getOutletCode().isBlank()) {
            String outletCodeKey = item.getOutletCode().toUpperCase();
            PosOutlet outlet = stagedOutlets.get(outletCodeKey);
            if (outlet == null) {
                outlet = outletCache.get(outletCodeKey);
            }
            if (outlet == null) {
                outlet = outletRepository.save(PosOutlet.builder()
                        .code(outletCodeKey)
                        .name(item.getOutletCode())
                        .outletType(OutletType.BAR)
                        .isActive(true)
                        .build());
                stagedOutlets.put(outletCodeKey, outlet);
            }
            outletId = outlet.getId();
        }

        String catCodeKey = item.getCategoryCode().toUpperCase();
        PosCategory category = stagedCategories.get(catCodeKey);
        if (category == null) {
            category = categoryCache.get(catCodeKey);
        }
        if (category == null) {
            category = categoryRepository.save(PosCategory.builder()
                    .outletId(outletId)
                    .code(catCodeKey)
                    .name(item.getCategoryCode())
                    .isActive(true)
                    .build());
            stagedCategories.put(catCodeKey, category);
        }
        Long categoryId = category.getId();

        TaxCategory tax = TaxCategory.STANDARD_VAT;
        if (item.getTaxCategory() != null) {
            try {
                tax = TaxCategory.valueOf(item.getTaxCategory().toUpperCase());
            } catch (Exception ignored) {
                // fallback to standard
            }
        }

        Optional<PosProduct> existingOpt = productRepository.findBySku(item.getSku());
        PosProduct product;
        if (existingOpt.isPresent()) {
            product = existingOpt.get();
            product.setName(item.getName());
            product.setBasePrice(item.getBasePrice());
            if (item.getCostPrice() != null) product.setCostPrice(item.getCostPrice());
            if (item.getBarcode() != null) product.setBarcode(item.getBarcode());
            if (item.getDescription() != null) product.setDescription(item.getDescription());
            product.setTaxCategory(tax);
            product.setCategoryId(categoryId);
            if (outletId != null) {
                Long oldOutletId = product.getOutletId();
                if (oldOutletId != null && !outletId.equals(oldOutletId)) {
                    auditLogRepository.save(PosCatalogAuditLog.builder()
                            .productId(product.getId())
                            .sku(product.getSku())
                            .action("OUTLET_REASSIGNED")
                            .oldValue(String.valueOf(oldOutletId))
                            .newValue(String.valueOf(outletId))
                            .performedBy(request.getPerformedBy() != null ? request.getPerformedBy() : "MANIFEST_SYNC")
                            .reason("Product moved from outlet " + oldOutletId + " to " + outletId)
                            .build());
                }
                product.setOutletId(outletId);
            }
            if (item.getStockQuantity() != null) product.setStockQuantity(item.getStockQuantity());
            if (item.getImageUrl() != null) product.setImageUrl(item.getImageUrl());
            if (item.getHappyHourEligible() != null) product.setHappyHourEligible(item.getHappyHourEligible());
            if (item.getHappyHourDiscountPercent() != null) product.setHappyHourDiscountPercent(item.getHappyHourDiscountPercent());
        } else {
            product = PosProduct.builder()
                    .sku(item.getSku())
                    .name(item.getName())
                    .barcode(item.getBarcode())
                    .description(item.getDescription())
                    .basePrice(item.getBasePrice())
                    .costPrice(item.getCostPrice())
                    .outletId(outletId)
                    .categoryId(categoryId)
                    .taxCategory(tax)
                    .status(ProductStatus.ACTIVE)
                    .isAvailable(true)
                    .is86ed(false)
                    .stockQuantity(item.getStockQuantity() != null ? item.getStockQuantity() : 100)
                    .imageUrl(item.getImageUrl())
                    .happyHourEligible(item.getHappyHourEligible() != null ? item.getHappyHourEligible() : true)
                    .happyHourDiscountPercent(item.getHappyHourDiscountPercent() != null ? item.getHappyHourDiscountPercent() : BigDecimal.ZERO)
                    .build();
        }

        PosProduct savedProduct = productRepository.save(product);

        if (item.getModifiers() != null && !item.getModifiers().isEmpty()) {
            modifierRepository.deleteByProductId(savedProduct.getId());
            for (ProductModifierDto mDto : item.getModifiers()) {
                PosProductModifier mod = PosProductModifier.builder()
                        .productId(savedProduct.getId())
                        .name(mDto.getName())
                        .priceDelta(mDto.getPriceDelta() != null ? mDto.getPriceDelta() : BigDecimal.ZERO)
                        .isRequired(Boolean.TRUE.equals(mDto.getIsRequired()))
                        .minSelection(mDto.getMinSelection() != null ? mDto.getMinSelection() : 0)
                        .maxSelection(mDto.getMaxSelection() != null ? mDto.getMaxSelection() : 1)
                        .displayOrder(mDto.getDisplayOrder() != null ? mDto.getDisplayOrder() : 0)
                        .status("ACTIVE")
                        .build();
                modifierRepository.save(mod);
            }
        }

        auditLogRepository.save(PosCatalogAuditLog.builder()
                .productId(savedProduct.getId())
                .sku(savedProduct.getSku())
                .action("BULK_IMPORT")
                .newValue("Imported price: " + savedProduct.getBasePrice())
                .performedBy(request.getPerformedBy() != null ? request.getPerformedBy() : "MANIFEST_SYNC")
                .reason("Supplier manifest sync #" + request.getSupplierManifestNumber())
                .build());
    }


    /**
     * Returns a page of catalog audit entries ordered from newest to oldest.
     */
    @Transactional(readOnly = true)
    public Page<PosCatalogAuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}

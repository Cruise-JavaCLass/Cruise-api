package com.cruise.pos.service;

import com.cruise.pos.dto.ProductDetailDto;
import com.cruise.pos.dto.ProductModifierDto;
import com.cruise.pos.entity.PosCategory;
import com.cruise.pos.entity.PosOutlet;
import com.cruise.pos.entity.PosProduct;
import com.cruise.pos.entity.PosProductModifier;
import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.PosCategoryRepository;
import com.cruise.pos.repository.PosOutletRepository;
import com.cruise.pos.repository.PosProductModifierRepository;
import com.cruise.pos.repository.PosProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BarcodeLookupService {

    private final PosProductRepository productRepository;
    private final PosProductModifierRepository modifierRepository;
    private final PosOutletRepository outletRepository;
    private final PosCategoryRepository categoryRepository;

    // Bounded high performance in-memory cache for ultra-fast barcode scanner response (< 50ms)
    private static final int MAX_CACHE_ENTRIES = 1000;
    private final Map<String, CacheEntry> lookupCache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            }
    );
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    private record CacheEntry(ProductDetailDto dto, long timestamp) {
        /**
         * Reports whether the cached lookup result is older than the five-minute TTL.
         */
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    /**
     * Evicts specific product entries from the barcode lookup cache.
     */
    public void evictProduct(String sku, String barcode, Long outletId) {
        if (sku != null && !sku.isBlank()) {
            String trimmedSku = sku.trim();
            lookupCache.remove("ALL:" + trimmedSku);
            if (outletId != null) {
                lookupCache.remove(outletId + ":" + trimmedSku);
            }
        }
        if (barcode != null && !barcode.isBlank()) {
            String trimmedBarcode = barcode.trim();
            lookupCache.remove("ALL:" + trimmedBarcode);
            if (outletId != null) {
                lookupCache.remove(outletId + ":" + trimmedBarcode);
            }
        }
    }

    /**
     * Clears all cached barcode and SKU lookup results.
     */
    public void evictCache() {
        lookupCache.clear();
        log.info("Barcode lookup cache cleared.");
    }

    /**
     * Returns cached or loaded product details for a trimmed barcode or SKU.
     *
     * Outlet lookups include shared products and fall back to searching all outlets.
     * Results are cached for five minutes; blank or unknown codes raise ResourceNotFoundException.
     */
    @Transactional(readOnly = true)
    public ProductDetailDto lookupProductByBarcodeOrSku(String code, Long outletId) {
        if (code == null || code.trim().isEmpty()) {
            throw new ResourceNotFoundException("Mã barcode hoặc SKU không được để trống");
        }

        String trimmedCode = code.trim();
        String cacheKey = (outletId != null ? outletId : "ALL") + ":" + trimmedCode;

        CacheEntry cached = lookupCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Cache hit for code: {} (key: {})", trimmedCode, cacheKey);
            return cached.dto();
        }

        long startTime = System.currentTimeMillis();

        List<PosProduct> matches = productRepository.findByCodeAndOutlet(trimmedCode, outletId);
        if (matches.isEmpty() && outletId != null) {
            // Fallback to global product without specific outlet
            matches = productRepository.findByCodeAndOutlet(trimmedCode, null);
        }

        List<PosProduct> eligible = matches.stream()
                .filter(p -> p.getStatus() != ProductStatus.ARCHIVED)
                .sorted(Comparator
                        .comparing((PosProduct p) -> (outletId != null && outletId.equals(p.getOutletId())) ? 0 : 1)
                        .thenComparing((PosProduct p) -> trimmedCode.equalsIgnoreCase(p.getBarcode()) ? 0 : 1)
                        .thenComparing(PosProduct::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                )
                .toList();

        if (eligible.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm với mã barcode/SKU: " + trimmedCode);
        }

        PosProduct product = eligible.get(0);

        String outletName = product.getOutletId() != null
                ? outletRepository.findById(product.getOutletId()).map(PosOutlet::getName).orElse(null)
                : "Tất cả điểm bán";
        String categoryName = categoryRepository.findById(product.getCategoryId())
                .map(PosCategory::getName).orElse("Không xác định");

        List<ProductModifierDto> modifiers = modifierRepository.findByProductIdOrderByDisplayOrderAsc(product.getId()).stream()
                .map(this::toModifierDto)
                .collect(Collectors.toList());

        ProductDetailDto dto = ProductDetailDto.builder()
                .id(product.getId())
                .outletId(product.getOutletId())
                .outletName(outletName)
                .categoryId(product.getCategoryId())
                .categoryName(categoryName)
                .sku(product.getSku())
                .barcode(product.getBarcode())
                .name(product.getName())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .costPrice(product.getCostPrice())
                .taxCategory(product.getTaxCategory())
                .status(product.getStatus())
                .isAvailable(product.getIsAvailable())
                .is86ed(product.getIs86ed())
                .stockQuantity(product.getStockQuantity())
                .imageUrl(product.getImageUrl())
                .displayOrder(product.getDisplayOrder())
                .happyHourEligible(product.getHappyHourEligible())
                .happyHourDiscountPercent(product.getHappyHourDiscountPercent())
                .modifiers(modifiers)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();

        lookupCache.put(cacheKey, new CacheEntry(dto, System.currentTimeMillis()));

        long duration = System.currentTimeMillis() - startTime;
        log.info("Barcode lookup for code '{}' completed in {}ms (SLA < 50ms)", trimmedCode, duration);

        return dto;
    }

    /**
     * Maps a product modifier to its catalog response DTO.
     */
    private ProductModifierDto toModifierDto(PosProductModifier m) {
        return ProductModifierDto.builder()
                .id(m.getId())
                .productId(m.getProductId())
                .name(m.getName())
                .priceDelta(m.getPriceDelta())
                .isRequired(m.getIsRequired())
                .minSelection(m.getMinSelection())
                .maxSelection(m.getMaxSelection())
                .displayOrder(m.getDisplayOrder())
                .status(m.getStatus())
                .build();
    }
}

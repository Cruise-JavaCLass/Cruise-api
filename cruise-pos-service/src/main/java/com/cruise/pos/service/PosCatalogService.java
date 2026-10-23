package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.PosCategory;
import com.cruise.pos.entity.PosOutlet;
import com.cruise.pos.entity.PosProduct;
import com.cruise.pos.entity.PosProductModifier;
import com.cruise.pos.enums.OutletType;
import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.enums.TaxCategory;
import com.cruise.pos.exception.DuplicateResourceException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.PosCategoryRepository;
import com.cruise.pos.repository.PosOutletRepository;
import com.cruise.pos.repository.PosProductModifierRepository;
import com.cruise.pos.repository.PosProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosCatalogService {

    private final PosOutletRepository outletRepository;
    private final PosCategoryRepository categoryRepository;
    private final PosProductRepository productRepository;
    private final PosProductModifierRepository modifierRepository;
    private final com.cruise.pos.repository.PosHappyHourRuleRepository happyHourRuleRepository;
    private final com.cruise.pos.repository.PosCatalogAuditLogRepository auditLogRepository;
    private final BarcodeLookupService barcodeLookupService;

    /**
     * Returns the active POS outlets.
     */
    @Transactional(readOnly = true)
    public List<OutletDto> getOutlets() {
        return outletRepository.findByIsActiveTrue().stream()
                .map(this::toOutletDto)
                .collect(Collectors.toList());
    }

    /**
     * Returns an outlet by ID, or throws if it does not exist.
     */
    @Transactional(readOnly = true)
    public OutletDto getOutletById(Long id) {
        return outletRepository.findById(id)
                .map(this::toOutletDto)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm bán với ID: " + id));
    }

    /**
     * Returns the active category tree for the outlet, including shared categories; null selects all outlets.
     */
    @Transactional(readOnly = true)
    public List<CategoryDto> getCategories(Long outletId) {
        List<PosCategory> all = categoryRepository.findCategoriesByOutlet(outletId);
        Map<Long, List<PosCategory>> childMap = new HashMap<>();
        List<PosCategory> rootList = new ArrayList<>();

        for (PosCategory cat : all) {
            if (cat.getParentId() == null) {
                rootList.add(cat);
            } else {
                childMap.computeIfAbsent(cat.getParentId(), k -> new ArrayList<>()).add(cat);
            }
        }

        return rootList.stream()
                .map(parent -> toCategoryDtoWithChildren(parent, childMap))
                .collect(Collectors.toList());
    }

    /**
     * Returns a page of products matching the optional outlet, category, status, and keyword filters.
     */
    @Transactional(readOnly = true)
    public Page<ProductDto> getProducts(Long outletId, Long categoryId, ProductStatus status, String keyword, Pageable pageable) {
        Page<PosProduct> page = productRepository.searchProducts(outletId, categoryId, status, keyword, pageable);
        Map<Long, String> categoryNames = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(PosCategory::getId, PosCategory::getName, (a, b) -> a));

        return page.map(p -> toProductDto(p, categoryNames.get(p.getCategoryId())));
    }

    /**
     * Returns product details with outlet, category, and modifiers, or throws if the product is missing.
     */
    @Transactional(readOnly = true)
    public ProductDetailDto getProductById(Long id) {
        PosProduct product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id));

        String outletName = product.getOutletId() != null
                ? outletRepository.findById(product.getOutletId()).map(PosOutlet::getName).orElse(null)
                : "Tất cả điểm bán";
        String categoryName = categoryRepository.findById(product.getCategoryId())
                .map(PosCategory::getName).orElse("Không xác định");

        List<ProductModifierDto> modifiers = modifierRepository.findByProductIdOrderByDisplayOrderAsc(product.getId()).stream()
                .map(this::toModifierDto)
                .collect(Collectors.toList());

        return toProductDetailDto(product, outletName, categoryName, modifiers);
    }

    /**
     * Creates a product and its modifiers, rejecting an existing SKU or nonblank barcode.
     */
    @Transactional
    public ProductDetailDto createProduct(CreateProductRequest req) {
        if (productRepository.existsBySku(req.getSku())) {
            throw new DuplicateResourceException("Mã SKU '" + req.getSku() + "' đã tồn tại trong hệ thống");
        }
        if (req.getBarcode() != null && !req.getBarcode().isBlank() && productRepository.existsByBarcode(req.getBarcode())) {
            throw new DuplicateResourceException("Mã vạch '" + req.getBarcode() + "' đã được gán cho sản phẩm khác");
        }

        PosProduct product = PosProduct.builder()
                .outletId(req.getOutletId())
                .categoryId(req.getCategoryId())
                .sku(req.getSku())
                .barcode(req.getBarcode())
                .name(req.getName())
                .description(req.getDescription())
                .basePrice(req.getBasePrice())
                .costPrice(req.getCostPrice())
                .taxCategory(req.getTaxCategory() != null ? req.getTaxCategory() : TaxCategory.STANDARD_VAT)
                .status(req.getStatus() != null ? req.getStatus() : ProductStatus.ACTIVE)
                .isAvailable(true)
                .is86ed(false)
                .stockQuantity(req.getStockQuantity() != null ? req.getStockQuantity() : 100)
                .imageUrl(req.getImageUrl())
                .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 0)
                .happyHourEligible(req.getHappyHourEligible() != null ? req.getHappyHourEligible() : true)
                .happyHourDiscountPercent(req.getHappyHourDiscountPercent() != null ? req.getHappyHourDiscountPercent() : BigDecimal.ZERO)
                .build();

        PosProduct saved;
        try {
            saved = productRepository.save(product);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Mã SKU '" + req.getSku() + "' hoặc mã vạch đã tồn tại trong hệ thống");
        }

        List<ProductModifierDto> savedModifiers = new ArrayList<>();
        if (req.getModifiers() != null && !req.getModifiers().isEmpty()) {
            for (ProductModifierDto mDto : req.getModifiers()) {
                PosProductModifier mod = PosProductModifier.builder()
                        .productId(saved.getId())
                        .name(mDto.getName())
                        .priceDelta(mDto.getPriceDelta() != null ? mDto.getPriceDelta() : BigDecimal.ZERO)
                        .isRequired(Boolean.TRUE.equals(mDto.getIsRequired()))
                        .minSelection(mDto.getMinSelection() != null ? mDto.getMinSelection() : 0)
                        .maxSelection(mDto.getMaxSelection() != null ? mDto.getMaxSelection() : 1)
                        .displayOrder(mDto.getDisplayOrder() != null ? mDto.getDisplayOrder() : 0)
                        .status("ACTIVE")
                        .build();
                savedModifiers.add(toModifierDto(modifierRepository.save(mod)));
            }
        }

        log.info("Created new POS product: ID={}, SKU={}, Name={}", saved.getId(), saved.getSku(), saved.getName());
        return getProductById(saved.getId());
    }

    /**
     * Applies supplied product fields, replaces modifiers when provided, and records an audit entry.
     */
    @Transactional
    public ProductDetailDto updateProduct(Long id, UpdateProductRequest req) {
        PosProduct product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id));

        String oldSku = product.getSku();
        String oldBarcode = product.getBarcode();
        Long oldOutletId = product.getOutletId();
        BigDecimal oldPrice = product.getBasePrice();

        if (req.getBarcode() != null && !req.getBarcode().isBlank() && !req.getBarcode().equals(product.getBarcode())) {
            if (productRepository.existsByBarcode(req.getBarcode())) {
                throw new DuplicateResourceException("Mã vạch '" + req.getBarcode() + "' đã được gán cho sản phẩm khác");
            }
            product.setBarcode(req.getBarcode());
        }

        if (req.getName() != null) product.setName(req.getName());
        if (req.getOutletId() != null) product.setOutletId(req.getOutletId());
        if (req.getCategoryId() != null) product.setCategoryId(req.getCategoryId());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        if (req.getBasePrice() != null) product.setBasePrice(req.getBasePrice());
        if (req.getCostPrice() != null) product.setCostPrice(req.getCostPrice());
        if (req.getTaxCategory() != null) product.setTaxCategory(req.getTaxCategory());
        if (req.getStatus() != null) product.setStatus(req.getStatus());
        if (req.getIsAvailable() != null) product.setIsAvailable(req.getIsAvailable());
        if (req.getIs86ed() != null) {
            product.setIs86ed(req.getIs86ed());
            if (Boolean.TRUE.equals(req.getIs86ed())) {
                product.setStatus(ProductStatus.EIGHTY_SIXED);
                product.setIsAvailable(false);
            }
        }
        if (req.getStockQuantity() != null) product.setStockQuantity(req.getStockQuantity());
        if (req.getImageUrl() != null) product.setImageUrl(req.getImageUrl());
        if (req.getDisplayOrder() != null) product.setDisplayOrder(req.getDisplayOrder());
        if (req.getHappyHourEligible() != null) product.setHappyHourEligible(req.getHappyHourEligible());
        if (req.getHappyHourDiscountPercent() != null) product.setHappyHourDiscountPercent(req.getHappyHourDiscountPercent());

        PosProduct updated = productRepository.save(product);

        if (req.getModifiers() != null) {
            modifierRepository.deleteByProductId(product.getId());
            for (ProductModifierDto mDto : req.getModifiers()) {
                PosProductModifier mod = PosProductModifier.builder()
                        .productId(product.getId())
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

        boolean priceChanged = req.getBasePrice() != null && (oldPrice == null || req.getBasePrice().compareTo(oldPrice) != 0);
        String action = priceChanged ? "PRICE_UPDATE" : "PRODUCT_UPDATE";
        String oldValue = priceChanged ? (oldPrice != null ? oldPrice.toString() : null) : null;
        String newValue = priceChanged ? (updated.getBasePrice() != null ? updated.getBasePrice().toString() : null) : "Product updated";
        String performedBy = resolveCurrentPrincipal();

        if (oldOutletId != null && updated.getOutletId() != null && !Objects.equals(oldOutletId, updated.getOutletId())) {
            auditLogRepository.save(com.cruise.pos.entity.PosCatalogAuditLog.builder()
                    .productId(updated.getId())
                    .sku(updated.getSku())
                    .action("OUTLET_REASSIGNED")
                    .oldValue(String.valueOf(oldOutletId))
                    .newValue(String.valueOf(updated.getOutletId()))
                    .performedBy(performedBy)
                    .reason("Product moved from outlet " + oldOutletId + " to " + updated.getOutletId())
                    .build());
        } else if (oldOutletId == null && updated.getOutletId() != null) {
            auditLogRepository.save(com.cruise.pos.entity.PosCatalogAuditLog.builder()
                    .productId(updated.getId())
                    .sku(updated.getSku())
                    .action("OUTLET_REASSIGNED")
                    .oldValue("GLOBAL")
                    .newValue(String.valueOf(updated.getOutletId()))
                    .performedBy(performedBy)
                    .reason("Global product restricted to outlet " + updated.getOutletId())
                    .build());
        }


        auditLogRepository.save(com.cruise.pos.entity.PosCatalogAuditLog.builder()
                .productId(updated.getId())
                .sku(updated.getSku())
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .performedBy(performedBy)
                .reason(priceChanged ? "Base price updated" : "Product information and price update")
                .build());

        barcodeLookupService.evictProduct(updated.getSku(), updated.getBarcode(), updated.getOutletId());
        if (!Objects.equals(oldBarcode, updated.getBarcode())
                || !Objects.equals(oldOutletId, updated.getOutletId())
                || !Objects.equals(oldSku, updated.getSku())) {
            barcodeLookupService.evictProduct(oldSku, oldBarcode, oldOutletId);
        }

        log.info("Updated POS product: ID={}, Name={}", product.getId(), product.getName());
        return getProductById(product.getId());
    }

    /**
     * Applies status and availability changes, handles out-of-stock flags, and records an audit entry.
     */
    @Transactional
    public ProductDetailDto updateProductStatus(Long id, ProductStatusUpdateRequest req) {
        PosProduct product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id));

        String action = "STATUS_UPDATE";
        String details = "Status changed to " + req.getStatus();

        if (req.getIs86ed() != null) {
            product.setIs86ed(req.getIs86ed());
            if (Boolean.TRUE.equals(req.getIs86ed())) {
                product.setStatus(ProductStatus.EIGHTY_SIXED);
                product.setIsAvailable(false);
                action = "EIGHTY_SIXED";
                details = "Marked 86'd (out-of-stock): " + req.getReason();
                log.warn("Product 86'd: ID={}, Name={}, Reason={}", product.getId(), product.getName(), req.getReason());
            } else {
                product.setStatus(ProductStatus.ACTIVE);
                product.setIsAvailable(true);
                action = "RESTORE_86ED";
                details = "Restored from 86'd back to active";
                log.info("Product restored from 86'd: ID={}, Name={}", product.getId(), product.getName());
            }
        }

        if (req.getStatus() != null) {
            product.setStatus(req.getStatus());
            if (req.getStatus() == ProductStatus.EIGHTY_SIXED) {
                product.setIs86ed(true);
                product.setIsAvailable(false);
                action = "EIGHTY_SIXED";
            } else if (req.getStatus() == ProductStatus.ACTIVE) {
                product.setIs86ed(false);
                product.setIsAvailable(true);
            }
        }

        if (req.getIsAvailable() != null) {
            product.setIsAvailable(req.getIsAvailable());
        }

        PosProduct saved = productRepository.save(product);
        if (req.getStaffId() != null && !req.getStaffId().isBlank()) {
            details = details + " [claimedStaffId: " + req.getStaffId() + "]";
        }


        auditLogRepository.save(com.cruise.pos.entity.PosCatalogAuditLog.builder()
                .productId(saved.getId())
                .sku(saved.getSku())
                .action(action)
                .newValue(saved.getStatus().name())
                .performedBy(resolveCurrentPrincipal())
                .reason(req.getReason())
                .details(details)
                .build());

        barcodeLookupService.evictProduct(saved.getSku(), saved.getBarcode(), saved.getOutletId());

        return getProductById(product.getId());
    }

    private String resolveCurrentPrincipal() {
        try {
            org.springframework.web.context.request.RequestAttributes attrs =
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
                jakarta.servlet.http.HttpServletRequest request = servletAttrs.getRequest();
                java.security.Principal principal = request.getUserPrincipal();
                if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
                    return principal.getName();
                }
                String remoteUser = request.getRemoteUser();
                if (remoteUser != null && !remoteUser.isBlank()) {
                    return remoteUser;
                }
            }
        } catch (Exception ignored) {
        }
        return "SUPERVISOR";
    }


    /**
     * Maps an outlet and its timestamps to a catalog response DTO.
     */
    private OutletDto toOutletDto(PosOutlet o) {
        return OutletDto.builder()
                .id(o.getId())
                .code(o.getCode())
                .name(o.getName())
                .outletType(o.getOutletType())
                .location(o.getLocation())
                .openTime(o.getOpenTime())
                .closeTime(o.getCloseTime())
                .isActive(o.getIsActive())
                .description(o.getDescription())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }

    /**
     * Recursively maps a category and its descendants using the supplied parent-to-children index.
     */
    private CategoryDto toCategoryDtoWithChildren(PosCategory c, Map<Long, List<PosCategory>> childMap) {
        List<CategoryDto> children = childMap.getOrDefault(c.getId(), Collections.emptyList()).stream()
                .map(child -> toCategoryDtoWithChildren(child, childMap))
                .collect(Collectors.toList());

        return CategoryDto.builder()
                .id(c.getId())
                .outletId(c.getOutletId())
                .code(c.getCode())
                .name(c.getName())
                .parentId(c.getParentId())
                .displayOrder(c.getDisplayOrder())
                .isActive(c.getIsActive())
                .icon(c.getIcon())
                .color(c.getColor())
                .description(c.getDescription())
                .children(children)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    /**
     * Maps a product to a catalog listing DTO with its resolved category name.
     */
    private ProductDto toProductDto(PosProduct p, String categoryName) {
        return ProductDto.builder()
                .id(p.getId())
                .outletId(p.getOutletId())
                .categoryId(p.getCategoryId())
                .categoryName(categoryName)
                .sku(p.getSku())
                .barcode(p.getBarcode())
                .name(p.getName())
                .description(p.getDescription())
                .basePrice(p.getBasePrice())
                .costPrice(p.getCostPrice())
                .taxCategory(p.getTaxCategory())
                .status(p.getStatus())
                .isAvailable(p.getIsAvailable())
                .is86ed(p.getIs86ed())
                .stockQuantity(p.getStockQuantity())
                .imageUrl(p.getImageUrl())
                .displayOrder(p.getDisplayOrder())
                .happyHourEligible(p.getHappyHourEligible())
                .happyHourDiscountPercent(p.getHappyHourDiscountPercent())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    /**
     * Maps a product to a detail DTO with resolved outlet and category names and modifiers.
     */
    private ProductDetailDto toProductDetailDto(PosProduct p, String outletName, String categoryName, List<ProductModifierDto> modifiers) {
        return ProductDetailDto.builder()
                .id(p.getId())
                .outletId(p.getOutletId())
                .outletName(outletName)
                .categoryId(p.getCategoryId())
                .categoryName(categoryName)
                .sku(p.getSku())
                .barcode(p.getBarcode())
                .name(p.getName())
                .description(p.getDescription())
                .basePrice(p.getBasePrice())
                .costPrice(p.getCostPrice())
                .taxCategory(p.getTaxCategory())
                .status(p.getStatus())
                .isAvailable(p.getIsAvailable())
                .is86ed(p.getIs86ed())
                .stockQuantity(p.getStockQuantity())
                .imageUrl(p.getImageUrl())
                .displayOrder(p.getDisplayOrder())
                .happyHourEligible(p.getHappyHourEligible())
                .happyHourDiscountPercent(p.getHappyHourDiscountPercent())
                .modifiers(modifiers)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
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

    /**
     * Seeds sample outlets, categories, products, modifiers, and a Happy Hour rule if no outlets exist.
     */
    @Transactional
    public void seedDefaultCatalog() {
        if (outletRepository.count() > 0) {
            log.info("POS Catalog already seeded. Skipping initial seeding.");
            return;
        }

        log.info("Seeding default cruise POS outlets, categories, products and modifiers...");

        PosOutlet skyBar = outletRepository.save(PosOutlet.builder()
                .code("OUTLET-SKYBAR")
                .name("Sky Bar & Lounge")
                .outletType(OutletType.BAR)
                .location("Deck 12, Aft")
                .openTime("16:00")
                .closeTime("02:00")
                .isActive(true)
                .description("Rooftop open-air cocktail bar with panoramic sunset ocean views")
                .build());

        PosOutlet lido = outletRepository.save(PosOutlet.builder()
                .code("OUTLET-LIDO")
                .name("Lido Buffet & Grill")
                .outletType(OutletType.RESTAURANT)
                .location("Deck 9, Midship")
                .openTime("06:00")
                .closeTime("22:00")
                .isActive(true)
                .description("Casual dining grill and international seafood buffet")
                .build());

        PosOutlet spa = outletRepository.save(PosOutlet.builder()
                .code("OUTLET-LOTUS")
                .name("Lotus Spa & Wellness")
                .outletType(OutletType.SPA)
                .location("Deck 11, Forward")
                .openTime("08:00")
                .closeTime("20:00")
                .isActive(true)
                .description("Luxury wellness sanctuary offering massages and thermal suites")
                .build());

        PosOutlet dutyFree = outletRepository.save(PosOutlet.builder()
                .code("OUTLET-DUTYFREE")
                .name("Royal Duty-Free Boutique")
                .outletType(OutletType.GIFT_SHOP)
                .location("Deck 5, Promenade")
                .openTime("09:00")
                .closeTime("23:00")
                .isActive(true)
                .description("Tax-free luxury perfumes, cosmetics, watches and souvenirs")
                .build());

        PosCategory cocktails = categoryRepository.save(PosCategory.builder()
                .outletId(skyBar.getId())
                .code("CAT-COCKTAILS")
                .name("Signature Cocktails")
                .displayOrder(1)
                .isActive(true)
                .icon("Wine")
                .color("#3b82f6")
                .description("Craft cocktails prepared by world-class mixologists")
                .build());

        PosCategory beers = categoryRepository.save(PosCategory.builder()
                .outletId(skyBar.getId())
                .code("CAT-BEERS")
                .name("Craft Beers & Cider")
                .displayOrder(2)
                .isActive(true)
                .icon("Beer")
                .color("#f59e0b")
                .description("Draft and bottled international craft beers")
                .build());

        PosCategory mains = categoryRepository.save(PosCategory.builder()
                .outletId(lido.getId())
                .code("CAT-MAINS")
                .name("Gourmet Grill & Burgers")
                .displayOrder(1)
                .isActive(true)
                .icon("Utensils")
                .color("#ef4444")
                .description("Grilled Angus burgers, artisanal sliders, and steaks")
                .build());

        PosCategory desserts = categoryRepository.save(PosCategory.builder()
                .outletId(lido.getId())
                .code("CAT-DESSERTS")
                .name("Desserts & Ice Cream")
                .displayOrder(2)
                .isActive(true)
                .icon("Cake")
                .color("#ec4899")
                .description("Freshly baked pastries, gelatos, and artisan desserts")
                .build());

        PosCategory massage = categoryRepository.save(PosCategory.builder()
                .outletId(spa.getId())
                .code("CAT-MASSAGE")
                .name("Massage Therapies")
                .displayOrder(1)
                .isActive(true)
                .icon("Sparkles")
                .color("#10b981")
                .description("Therapeutic deep tissue and aromatherapy treatments")
                .build());

        PosCategory perfumes = categoryRepository.save(PosCategory.builder()
                .outletId(dutyFree.getId())
                .code("CAT-PERFUMES")
                .name("Fragrances & Cosmetics")
                .displayOrder(1)
                .isActive(true)
                .icon("ShoppingBag")
                .color("#8b5cf6")
                .description("Top international designer fragrances and skincare")
                .build());

        // Products
        PosProduct mojito = productRepository.save(PosProduct.builder()
                .outletId(skyBar.getId())
                .categoryId(cocktails.getId())
                .sku("BEV-MOJ-001")
                .barcode("893850123401")
                .name("Pacific Sunset Mojito")
                .description("White rum, fresh lime juice, wild mint, raw brown sugar and club soda")
                .basePrice(new BigDecimal("14.50"))
                .costPrice(new BigDecimal("3.20"))
                .taxCategory(TaxCategory.STANDARD_VAT)
                .status(ProductStatus.ACTIVE)
                .isAvailable(true)
                .is86ed(false)
                .stockQuantity(150)
                .imageUrl("https://images.unsplash.com/photo-1551024709-8f23befc6f87?w=300")
                .displayOrder(1)
                .happyHourEligible(true)
                .happyHourDiscountPercent(new BigDecimal("20.00"))
                .build());

        modifierRepository.save(PosProductModifier.builder()
                .productId(mojito.getId())
                .name("Extra White Rum Shot")
                .priceDelta(new BigDecimal("3.50"))
                .isRequired(false)
                .minSelection(0)
                .maxSelection(2)
                .displayOrder(1)
                .status("ACTIVE")
                .build());

        modifierRepository.save(PosProductModifier.builder()
                .productId(mojito.getId())
                .name("No Sugar Added")
                .priceDelta(BigDecimal.ZERO)
                .isRequired(false)
                .minSelection(0)
                .maxSelection(1)
                .displayOrder(2)
                .status("ACTIVE")
                .build());

        productRepository.save(PosProduct.builder()
                .outletId(skyBar.getId())
                .categoryId(cocktails.getId())
                .sku("BEV-BLU-002")
                .barcode("893850123402")
                .name("Ocean Blue Lagoon")
                .description("Vodka, blue curacao, lemonade, garnished with a maraschino cherry")
                .basePrice(new BigDecimal("15.00"))
                .costPrice(new BigDecimal("3.50"))
                .taxCategory(TaxCategory.STANDARD_VAT)
                .status(ProductStatus.ACTIVE)
                .isAvailable(true)
                .is86ed(false)
                .stockQuantity(120)
                .imageUrl("https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?w=300")
                .displayOrder(2)
                .happyHourEligible(true)
                .happyHourDiscountPercent(new BigDecimal("20.00"))
                .build());

        productRepository.save(PosProduct.builder()
                .outletId(skyBar.getId())
                .categoryId(beers.getId())
                .sku("BEV-IPA-003")
                .barcode("893850123403")
                .name("Harbor Craft IPA Draft (Pint)")
                .description("Crisp citrus aroma with rich hoppy finish on tap")
                .basePrice(new BigDecimal("8.50"))
                .costPrice(new BigDecimal("1.80"))
                .taxCategory(TaxCategory.STANDARD_VAT)
                .status(ProductStatus.ACTIVE)
                .isAvailable(true)
                .is86ed(false)
                .stockQuantity(200)
                .imageUrl("https://images.unsplash.com/photo-1608270191771-464a9fb68393?w=300")
                .displayOrder(1)
                .happyHourEligible(true)
                .happyHourDiscountPercent(new BigDecimal("15.00"))
                .build());

        PosProduct burger = productRepository.save(PosProduct.builder()
                .outletId(lido.getId())
                .categoryId(mains.getId())
                .sku("FOOD-BGR-101")
                .barcode("893850123501")
                .name("Angus Beef Cheese Burger")
                .description("200g prime Angus beef patty, cheddar, brioche bun, served with fries")
                .basePrice(new BigDecimal("18.00"))
                .costPrice(new BigDecimal("4.50"))
                .taxCategory(TaxCategory.STANDARD_VAT)
                .status(ProductStatus.ACTIVE)
                .isAvailable(true)
                .is86ed(false)
                .stockQuantity(80)
                .imageUrl("https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=300")
                .displayOrder(1)
                .happyHourEligible(false)
                .happyHourDiscountPercent(BigDecimal.ZERO)
                .build());

        modifierRepository.save(PosProductModifier.builder()
                .productId(burger.getId())
                .name("Add Smoked Bacon")
                .priceDelta(new BigDecimal("2.50"))
                .isRequired(false)
                .minSelection(0)
                .maxSelection(1)
                .displayOrder(1)
                .status("ACTIVE")
                .build());

        modifierRepository.save(PosProductModifier.builder()
                .productId(burger.getId())
                .name("Gluten-Free Bun")
                .priceDelta(new BigDecimal("1.50"))
                .isRequired(false)
                .minSelection(0)
                .maxSelection(1)
                .displayOrder(2)
                .status("ACTIVE")
                .build());

        productRepository.save(PosProduct.builder()
                .outletId(lido.getId())
                .categoryId(desserts.getId())
                .sku("FOOD-CAK-103")
                .barcode("893850123503")
                .name("Warm Belgian Chocolate Lava Cake")
                .description("Molten dark chocolate core served with Madagascar vanilla gelato")
                .basePrice(new BigDecimal("11.00"))
                .costPrice(new BigDecimal("2.80"))
                .taxCategory(TaxCategory.STANDARD_VAT)
                .status(ProductStatus.ACTIVE)
                .isAvailable(true)
                .is86ed(false)
                .stockQuantity(50)
                .imageUrl("https://images.unsplash.com/photo-1606313564200-e75d5e30476c?w=300")
                .displayOrder(1)
                .happyHourEligible(false)
                .happyHourDiscountPercent(BigDecimal.ZERO)
                .build());

        productRepository.save(PosProduct.builder()
                .outletId(spa.getId())
                .categoryId(massage.getId())
                .sku("SPA-MAS-201")
                .barcode("893850123601")
                .name("Deep Tissue Ocean Massage 60m")
                .description("Full body therapeutic pressure massage with organic seaweed oils")
                .basePrice(new BigDecimal("120.00"))
                .costPrice(new BigDecimal("25.00"))
                .taxCategory(TaxCategory.STANDARD_VAT)
                .status(ProductStatus.ACTIVE)
                .isAvailable(true)
                .is86ed(false)
                .stockQuantity(999)
                .imageUrl("https://images.unsplash.com/photo-1544161515-4ab6ce6db874?w=300")
                .displayOrder(1)
                .happyHourEligible(false)
                .happyHourDiscountPercent(BigDecimal.ZERO)
                .build());

        productRepository.save(PosProduct.builder()
                .outletId(dutyFree.getId())
                .categoryId(perfumes.getId())
                .sku("DF-PERF-301")
                .barcode("893850123701")
                .name("Chanel No.5 Eau de Parfum 100ml")
                .description("Timeless, legendary floral aldehyde fragrance, duty-free retail")
                .basePrice(new BigDecimal("135.00"))
                .costPrice(new BigDecimal("75.00"))
                .taxCategory(TaxCategory.STANDARD_VAT)
                .status(ProductStatus.ACTIVE)
                .isAvailable(true)
                .is86ed(false)
                .stockQuantity(40)
                .imageUrl("https://images.unsplash.com/photo-1541643600914-78b084683601?w=300")
                .displayOrder(1)
                .happyHourEligible(false)
                .happyHourDiscountPercent(BigDecimal.ZERO)
                .build());

        happyHourRuleRepository.save(com.cruise.pos.entity.PosHappyHourRule.builder()
                .outletId(skyBar.getId())
                .name("Sunset Cocktails Happy Hour")
                .startTime("17:00")
                .endTime("19:00")
                .discountPercent(new BigDecimal("20.00"))
                .daysOfWeek("ALL")
                .isActive(true)
                .description("20% off all craft cocktails and beers during golden hour")
                .build());

        log.info("Default POS catalog successfully seeded with 4 outlets, 6 categories, and 7 products.");
    }
}

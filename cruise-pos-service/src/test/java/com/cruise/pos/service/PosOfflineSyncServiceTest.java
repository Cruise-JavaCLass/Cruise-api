package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.OutletType;
import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.enums.TaxCategory;
import com.cruise.pos.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PosOfflineSyncService Unit Tests")
class PosOfflineSyncServiceTest {

    @Mock
    private PosCatalogService catalogService;

    @Mock
    private DynamicPricingEngine pricingEngine;

    @Mock
    private PosOutletRepository outletRepository;

    @Mock
    private PosCategoryRepository categoryRepository;

    @Mock
    private PosProductRepository productRepository;

    @Mock
    private PosProductModifierRepository modifierRepository;

    @Mock
    private PosCatalogAuditLogRepository auditLogRepository;

    @Mock
    private BarcodeLookupService barcodeLookupService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper realObjectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private PosOfflineSyncService offlineSyncService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        offlineSyncService = new PosOfflineSyncService(
                catalogService,
                pricingEngine,
                outletRepository,
                categoryRepository,
                productRepository,
                modifierRepository,
                auditLogRepository,
                barcodeLookupService,
                realObjectMapper,
                transactionTemplate
        );
    }

    @Test
    @DisplayName("bulkImportProducts processes items independently: one failure does not abort remaining items")
    void testBulkImportProductsIndependentTransactions() {
        BulkProductImportItem validItem = BulkProductImportItem.builder()
                .sku("SKU-VALID")
                .name("Valid Drink")
                .categoryCode("BEV")
                .basePrice(new BigDecimal("10.00"))
                .build();

        BulkProductImportItem invalidItem = BulkProductImportItem.builder()
                .sku("SKU-INVALID")
                .name("Missing Category Item")
                .categoryCode("") // missing category
                .basePrice(new BigDecimal("5.00"))
                .build();

        BulkCatalogImportRequest request = BulkCatalogImportRequest.builder()
                .supplierManifestNumber("MANIFEST-001")
                .performedBy("CHIEF-PURSER")
                .products(List.of(invalidItem, validItem))
                .build();

        PosCategory category = PosCategory.builder()
                .code("BEV")
                .name("BEV")
                .build();
        category.setId(10L);

        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(outletRepository.findAll()).thenReturn(Collections.emptyList());
        when(productRepository.findBySku("SKU-VALID")).thenReturn(Optional.empty());
        when(productRepository.save(any(PosProduct.class))).thenAnswer(inv -> {
            PosProduct p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        BulkImportResponse response = offlineSyncService.bulkImportProducts(request);

        assertThat(response.getTotalItems()).isEqualTo(2);
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailedCount()).isEqualTo(1);
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().get(0)).contains("Category code is mandatory");

        verify(barcodeLookupService, times(1)).evictCache();
    }

    @Test
    @DisplayName("bulkImportProducts: failed transaction does not publish rolled-back outlets or categories into shared cache")
    void testFailedTransactionDoesNotPublishStagedCaches() {
        when(outletRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());

        // First item fails after creating outlet during transaction because basePrice is invalid
        BulkProductImportItem failingItem = BulkProductImportItem.builder()
                .sku("FAIL-01")
                .name("Fail Item")
                .outletCode("NEW-OUTLET")
                .categoryCode("NEW-CAT")
                .basePrice(new BigDecimal("-5.00"))
                .build();

        BulkProductImportItem validItem = BulkProductImportItem.builder()
                .sku("PASS-02")
                .name("Pass Item")
                .outletCode("NEW-OUTLET")
                .categoryCode("NEW-CAT")
                .basePrice(new BigDecimal("10.00"))
                .build();

        BulkCatalogImportRequest request = BulkCatalogImportRequest.builder()
                .supplierManifestNumber("MAN-TEST")
                .products(List.of(failingItem, validItem))
                .build();

        when(outletRepository.save(any(PosOutlet.class))).thenAnswer(inv -> {
            PosOutlet o = inv.getArgument(0);
            o.setId(99L);
            return o;
        });
        when(categoryRepository.save(any(PosCategory.class))).thenAnswer(inv -> {
            PosCategory c = inv.getArgument(0);
            c.setId(999L);
            return c;
        });
        when(productRepository.findBySku("PASS-02")).thenReturn(Optional.empty());
        when(productRepository.save(any(PosProduct.class))).thenAnswer(inv -> inv.getArgument(0));

        BulkImportResponse response = offlineSyncService.bulkImportProducts(request);

        assertThat(response.getFailedCount()).isEqualTo(1);
        assertThat(response.getSuccessCount()).isEqualTo(1);
        // Because first transaction failed, NEW-OUTLET was not retained in shared cache, so save was invoked for validItem
        verify(outletRepository, times(1)).save(any(PosOutlet.class));
    }

    @Test
    @DisplayName("exportOfflinePackage generates package and computes valid SHA-256 ETag without full table scan")
    void testExportOfflinePackageComputesEtag() {
        OutletDto outlet = OutletDto.builder().id(1L).code("BAR").name("Lounge Bar").build();
        CategoryDto category = CategoryDto.builder().id(10L).code("BEV").name("Beverages").build();
        HappyHourRuleDto rule = HappyHourRuleDto.builder().id(5L).name("Evening HH").startTime("17:00").endTime("19:00").discountPercent(new BigDecimal("20.00")).build();
        PosProduct product = PosProduct.builder().sku("MOJITO").name("Mojito").basePrice(new BigDecimal("12.00")).build();
        product.setId(101L);

        when(catalogService.getOutlets()).thenReturn(List.of(outlet));
        when(catalogService.getCategories(1L)).thenReturn(List.of(category));
        when(pricingEngine.getRulesByOutlet(1L)).thenReturn(List.of(rule));
        when(productRepository.findActiveCatalogByOutlet(1L)).thenReturn(List.of(product));
        when(catalogService.getProductById(101L)).thenReturn(ProductDetailDto.builder().id(101L).sku("MOJITO").basePrice(new BigDecimal("12.00")).build());

        PosOfflineCatalogPackageDto pkg = offlineSyncService.exportOfflinePackage(1L);

        assertThat(pkg).isNotNull();
        assertThat(pkg.getEtag()).isNotNull().matches("^\"[a-f0-9]{16}\"$");
        assertThat(pkg.getProducts()).hasSize(1);
        verify(productRepository, never()).findAll();
    }

    @Test
    @DisplayName("generateEtag: identical content produces same ETag despite different exportedAt timestamps")
    void testGenerateEtagIdenticalContentSameEtag() {
        OutletDto outlet = OutletDto.builder().id(1L).code("BAR").name("Bar").build();
        CategoryDto category = CategoryDto.builder().id(10L).code("BEV").name("Beverages").build();
        ProductDetailDto product = ProductDetailDto.builder().id(101L).sku("BEER").basePrice(new BigDecimal("8.00")).build();
        HappyHourRuleDto rule = HappyHourRuleDto.builder().id(5L).discountPercent(new BigDecimal("15.00")).build();

        PosOfflineCatalogPackageDto pkg1 = PosOfflineCatalogPackageDto.builder()
                .version("1.0.0").outletId(1L)
                .exportedAt(Instant.parse("2026-10-20T10:00:00Z"))
                .outlets(List.of(outlet)).categories(List.of(category))
                .products(List.of(product)).happyHourRules(List.of(rule))
                .build();

        PosOfflineCatalogPackageDto pkg2 = PosOfflineCatalogPackageDto.builder()
                .version("1.0.0").outletId(1L)
                .exportedAt(Instant.parse("2026-10-20T12:34:56Z"))
                .outlets(List.of(outlet)).categories(List.of(category))
                .products(List.of(product)).happyHourRules(List.of(rule))
                .build();

        String etag1 = offlineSyncService.generateEtag(pkg1);
        String etag2 = offlineSyncService.generateEtag(pkg2);

        assertThat(etag1).isEqualTo(etag2);
    }

    @Test
    @DisplayName("generateEtag: changing HappyHourRuleDto discountPercent produces different ETag")
    void testGenerateEtagChangingDiscountPercentProducesDifferentEtag() {
        HappyHourRuleDto rule1 = HappyHourRuleDto.builder().id(5L).discountPercent(new BigDecimal("15.00")).build();
        HappyHourRuleDto rule2 = HappyHourRuleDto.builder().id(5L).discountPercent(new BigDecimal("25.00")).build();

        PosOfflineCatalogPackageDto pkg1 = PosOfflineCatalogPackageDto.builder()
                .version("1.0.0").outletId(1L).happyHourRules(List.of(rule1)).build();
        PosOfflineCatalogPackageDto pkg2 = PosOfflineCatalogPackageDto.builder()
                .version("1.0.0").outletId(1L).happyHourRules(List.of(rule2)).build();

        assertThat(offlineSyncService.generateEtag(pkg1)).isNotEqualTo(offlineSyncService.generateEtag(pkg2));
    }

    @Test
    @DisplayName("generateEtag: changing modifier list produces different ETag")
    void testGenerateEtagChangingModifiersProducesDifferentEtag() {
        ProductModifierDto mod = ProductModifierDto.builder().id(1L).name("Lime").priceDelta(new BigDecimal("1.00")).build();
        ProductDetailDto prod1 = ProductDetailDto.builder().id(101L).sku("BEER").modifiers(Collections.emptyList()).build();
        ProductDetailDto prod2 = ProductDetailDto.builder().id(101L).sku("BEER").modifiers(List.of(mod)).build();

        PosOfflineCatalogPackageDto pkg1 = PosOfflineCatalogPackageDto.builder()
                .version("1.0.0").outletId(1L).products(List.of(prod1)).build();
        PosOfflineCatalogPackageDto pkg2 = PosOfflineCatalogPackageDto.builder()
                .version("1.0.0").outletId(1L).products(List.of(prod2)).build();

        assertThat(offlineSyncService.generateEtag(pkg1)).isNotEqualTo(offlineSyncService.generateEtag(pkg2));
    }

    @Test
    @DisplayName("getAuditLogs delegates to repository with pagination")
    void testGetAuditLogs() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<PosCatalogAuditLog> expectedPage = new org.springframework.data.domain.PageImpl<>(Collections.emptyList());
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(expectedPage);

        org.springframework.data.domain.Page<PosCatalogAuditLog> actual = offlineSyncService.getAuditLogs(pageable);
        assertThat(actual).isSameAs(expectedPage);
    }

    @Test
    @DisplayName("generateEtag with outletId and productCount returns formatted tag")
    void testGenerateEtagCountFallback() {
        String tag = offlineSyncService.generateEtag(10L, 50);
        assertThat(tag).isEqualTo("\"10-50\"");

        String tagNull = offlineSyncService.generateEtag(null, 5);
        assertThat(tagNull).isEqualTo("\"0-5\"");
    }

    @Test
    @DisplayName("bulkImportProducts updates existing product and replaces modifiers")
    void testBulkImportProductsUpdatesExistingProduct() {
        when(outletRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());

        PosProduct existing = PosProduct.builder()
                .sku("UPDATE-SKU")
                .name("Old Name")
                .basePrice(new BigDecimal("10.00"))
                .build();
        existing.setId(555L);

        when(productRepository.findBySku("UPDATE-SKU")).thenReturn(Optional.of(existing));
        when(productRepository.save(any(PosProduct.class))).thenAnswer(inv -> inv.getArgument(0));

        BulkProductImportItem updateItem = BulkProductImportItem.builder()
                .sku("UPDATE-SKU")
                .name("New Name")
                .basePrice(new BigDecimal("12.50"))
                .costPrice(new BigDecimal("4.00"))
                .barcode("NEW-BAR")
                .description("New Description")
                .categoryCode("MAIN")
                .taxCategory("STANDARD_VAT")
                .stockQuantity(200)
                .imageUrl("https://img.jpg")
                .modifiers(List.of(ProductModifierDto.builder().name("Extra Ice").priceDelta(BigDecimal.ZERO).build()))
                .build();

        BulkCatalogImportRequest request = BulkCatalogImportRequest.builder()
                .supplierManifestNumber("MANIFEST-UPDATE")
                .products(List.of(updateItem))
                .build();

        when(categoryRepository.save(any(PosCategory.class))).thenAnswer(inv -> {
            PosCategory c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        BulkImportResponse response = offlineSyncService.bulkImportProducts(request);

        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getBasePrice()).isEqualByComparingTo(new BigDecimal("12.50"));
        verify(modifierRepository).deleteByProductId(555L);
        verify(modifierRepository).save(any(PosProductModifier.class));
    }

    @Test
    @DisplayName("exportGzipCatalogPackage produces valid gzip compressed payload")
    void testExportGzipCatalogPackage() throws Exception {
        OutletDto outlet = OutletDto.builder()
                .code("BAR-POOL")
                .name("Pool Bar")
                .build();

        when(catalogService.getOutlets()).thenReturn(List.of(outlet));
        when(catalogService.getCategories(1L)).thenReturn(Collections.emptyList());
        when(pricingEngine.getRulesByOutlet(1L)).thenReturn(Collections.emptyList());
        when(productRepository.findActiveCatalogByOutlet(1L)).thenReturn(Collections.emptyList());

        byte[] gzipBytes = offlineSyncService.exportGzipCatalogPackage(1L);
        assertThat(gzipBytes).isNotNull();
        assertThat(gzipBytes.length).isGreaterThan(0);

        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gzipBytes))) {
            String uncompressedJson = new String(gis.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(uncompressedJson).contains("BAR-POOL");
            assertThat(uncompressedJson).contains("Pool Bar");
        }
    }

    @Test
    @DisplayName("getDeltaChangefeed returns empty when clientVersionHash matches current ETag")
    void testGetDeltaChangefeedMatchingEtag() {
        when(catalogService.getOutlets()).thenReturn(Collections.emptyList());
        when(catalogService.getCategories(1L)).thenReturn(Collections.emptyList());
        when(pricingEngine.getRulesByOutlet(1L)).thenReturn(Collections.emptyList());
        when(productRepository.findActiveCatalogByOutlet(1L)).thenReturn(Collections.emptyList());

        PosOfflineCatalogPackageDto pkg = offlineSyncService.exportOfflinePackage(1L);
        String currentEtag = pkg.getEtag();

        PosDeltaCatalogChangefeedDto delta = offlineSyncService.getDeltaChangefeed(1L, currentEtag, Instant.now());
        assertThat(delta.getFromVersionHash()).isEqualTo(currentEtag);
        assertThat(delta.getCurrentVersionHash()).isEqualTo(currentEtag);
        assertThat(delta.isRequiresFullSnapshot()).isFalse();
        assertThat(delta.getUpdatedOutlets()).isEmpty();
        assertThat(delta.getUpdatedProducts()).isEmpty();
        assertThat(delta.getDeletedProductIds()).isEmpty();
        assertThat(delta.getSyncTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("getDeltaChangefeed requiresFullSnapshot when since timestamp is null")
    void testGetDeltaChangefeedRequiresFullSnapshotWhenSinceIsNull() {
        when(catalogService.getOutlets()).thenReturn(Collections.emptyList());
        when(catalogService.getCategories(1L)).thenReturn(Collections.emptyList());
        when(pricingEngine.getRulesByOutlet(1L)).thenReturn(Collections.emptyList());
        when(productRepository.findActiveCatalogByOutlet(1L)).thenReturn(Collections.emptyList());
        when(productRepository.findUpdatedProductsSince(eq(1L), any(Instant.class))).thenReturn(Collections.emptyList());

        PosDeltaCatalogChangefeedDto delta = offlineSyncService.getDeltaChangefeed(1L, "\"older-hash\"", null);
        assertThat(delta.isRequiresFullSnapshot()).isTrue();
    }

    @Test
    @DisplayName("getDeltaChangefeed splits updated and archived products and includes reassigned outlet products")
    void testGetDeltaChangefeedWithUpdatesDeletionsAndReassigned() {
        OutletDto mockOutlet = OutletDto.builder().id(1L).code("POOL_BAR").name("Pool Bar").build();
        when(catalogService.getOutlets()).thenReturn(List.of(mockOutlet));
        when(catalogService.getCategories(1L)).thenReturn(Collections.emptyList());
        when(pricingEngine.getRulesByOutlet(1L)).thenReturn(Collections.emptyList());
        when(productRepository.findActiveCatalogByOutlet(1L)).thenReturn(Collections.emptyList());

        Instant since = Instant.now().minusSeconds(3600);

        PosProduct activeProd = PosProduct.builder().sku("SKU-ACTIVE").status(ProductStatus.ACTIVE).build();
        activeProd.setId(101L);
        PosProduct archivedProd = PosProduct.builder().sku("SKU-ARCH").status(ProductStatus.ARCHIVED).build();
        archivedProd.setId(102L);

        when(productRepository.findUpdatedProductsSince(eq(1L), eq(since))).thenReturn(List.of(activeProd, archivedProd));
        when(catalogService.getProductById(101L)).thenReturn(ProductDetailDto.builder().id(101L).sku("SKU-ACTIVE").build());
        when(auditLogRepository.findReassignedProductIdsForOutlet(eq("1"), eq(since))).thenReturn(List.of(103L));

        PosDeltaCatalogChangefeedDto delta = offlineSyncService.getDeltaChangefeed(1L, "\"different-hash\"", since);
        assertThat(delta.isRequiresFullSnapshot()).isFalse();
        assertThat(delta.getUpdatedProducts()).hasSize(1);
        assertThat(delta.getUpdatedProducts().get(0).getId()).isEqualTo(101L);
        assertThat(delta.getDeletedProductIds()).containsExactlyInAnyOrder(102L, 103L);
        assertThat(delta.getUpdatedOutlets()).hasSize(1);
        assertThat(delta.getUpdatedOutlets().get(0).getCode()).isEqualTo("POOL_BAR");
    }

    @Test
    @DisplayName("getDeltaChangefeed includes current outlets in delta response to prevent stale terminals")
    void testGetDeltaChangefeedIncludesCurrentOutlets() {
        OutletDto outlet1 = OutletDto.builder().id(1L).code("POOL_BAR").name("Pool Bar").build();
        OutletDto outlet2 = OutletDto.builder().id(2L).code("MAIN_DINING").name("Main Dining").build();
        when(catalogService.getOutlets()).thenReturn(List.of(outlet1, outlet2));
        when(catalogService.getCategories(1L)).thenReturn(Collections.emptyList());
        when(pricingEngine.getRulesByOutlet(1L)).thenReturn(Collections.emptyList());
        when(productRepository.findActiveCatalogByOutlet(1L)).thenReturn(Collections.emptyList());
        when(productRepository.findUpdatedProductsSince(eq(1L), any(Instant.class))).thenReturn(Collections.emptyList());

        Instant since = Instant.now().minusSeconds(1800);
        PosDeltaCatalogChangefeedDto delta = offlineSyncService.getDeltaChangefeed(1L, "\"stale-hash\"", since);

        assertThat(delta.isRequiresFullSnapshot()).isFalse();
        assertThat(delta.getUpdatedOutlets()).hasSize(2);
        assertThat(delta.getUpdatedOutlets()).extracting(OutletDto::getCode).containsExactly("POOL_BAR", "MAIN_DINING");
    }

    @Test
    @DisplayName("bulkImportProducts preserves happy hour fields on insert and update, and logs outlet reassignment")
    void testBulkImportProductsPreservesHappyHourAndLogsReassignment() {
        PosProduct existing = PosProduct.builder()
                .sku("EXISTING-BEV")
                .name("Old Drink")
                .basePrice(new BigDecimal("10.00"))
                .outletId(1L)
                .status(ProductStatus.ACTIVE)
                .build();
        existing.setId(201L);

        PosOutlet outlet2 = PosOutlet.builder()
                .code("OUTLET-2")
                .name("Outlet 2")
                .outletType(OutletType.BAR)
                .isActive(true)
                .build();
        outlet2.setId(2L);

        when(outletRepository.findAll()).thenReturn(List.of(outlet2));
        when(productRepository.findBySku("EXISTING-BEV")).thenReturn(Optional.of(existing));
        when(productRepository.findBySku("NEW-BEV")).thenReturn(Optional.empty());
        when(productRepository.save(any(PosProduct.class))).thenAnswer(inv -> inv.getArgument(0));

        BulkProductImportItem updateItem = BulkProductImportItem.builder()
                .sku("EXISTING-BEV")
                .name("Updated Drink")
                .basePrice(new BigDecimal("12.00"))
                .categoryCode("BEV")
                .outletCode("OUTLET-2")
                .happyHourEligible(true)
                .happyHourDiscountPercent(new BigDecimal("25.00"))
                .build();

        BulkProductImportItem newItem = BulkProductImportItem.builder()
                .sku("NEW-BEV")
                .name("Special Cocktail")
                .basePrice(new BigDecimal("15.00"))
                .categoryCode("BEV")
                .outletCode("OUTLET-2")
                .happyHourEligible(false)
                .happyHourDiscountPercent(BigDecimal.ZERO)
                .build();

        BulkCatalogImportRequest request = BulkCatalogImportRequest.builder()
                .supplierManifestNumber("MANIFEST-HH")
                .products(List.of(updateItem, newItem))
                .build();

        when(categoryRepository.save(any(PosCategory.class))).thenAnswer(inv -> {
            PosCategory c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        BulkImportResponse response = offlineSyncService.bulkImportProducts(request);
        assertThat(response.getSuccessCount()).isEqualTo(2);

        // Verify existing updated product happy hour and outlet
        assertThat(existing.getOutletId()).isEqualTo(2L);
        assertThat(existing.getHappyHourEligible()).isTrue();
        assertThat(existing.getHappyHourDiscountPercent()).isEqualByComparingTo(new BigDecimal("25.00"));

        // Verify audit log for OUTLET_REASSIGNED
        verify(auditLogRepository).save(argThat(log ->
                "OUTLET_REASSIGNED".equals(log.getAction()) &&
                "1".equals(log.getOldValue()) &&
                "2".equals(log.getNewValue()) &&
                Long.valueOf(201L).equals(log.getProductId())
        ));
    }

    @Test
    @DisplayName("bulkImportProducts logs GLOBAL as oldValue when global product moves to specific outlet")
    void testBulkImportProductsGlobalToSpecificOutlet() {
        PosProduct globalProduct = PosProduct.builder()
                .sku("GLOBAL-DRINK")
                .name("Global Drink")
                .basePrice(new BigDecimal("10.00"))
                .outletId(null)
                .status(ProductStatus.ACTIVE)
                .build();
        globalProduct.setId(301L);

        PosOutlet outlet3 = PosOutlet.builder()
                .code("OUTLET-3")
                .name("Outlet 3")
                .outletType(OutletType.RESTAURANT)
                .isActive(true)
                .build();
        outlet3.setId(3L);

        when(outletRepository.findAll()).thenReturn(List.of(outlet3));
        when(productRepository.findBySku("GLOBAL-DRINK")).thenReturn(Optional.of(globalProduct));
        when(productRepository.save(any(PosProduct.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.save(any(PosCategory.class))).thenAnswer(inv -> {
            PosCategory c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        BulkProductImportItem updateItem = BulkProductImportItem.builder()
                .sku("GLOBAL-DRINK")
                .name("Global Drink Restricted")
                .basePrice(new BigDecimal("10.00"))
                .categoryCode("BEV")
                .outletCode("OUTLET-3")
                .build();

        BulkCatalogImportRequest request = BulkCatalogImportRequest.builder()
                .supplierManifestNumber("MANIFEST-GLOBAL")
                .products(List.of(updateItem))
                .build();

        BulkImportResponse response = offlineSyncService.bulkImportProducts(request);
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(globalProduct.getOutletId()).isEqualTo(3L);

        // Verify audit log for OUTLET_REASSIGNED with GLOBAL sentinel
        verify(auditLogRepository).save(argThat(log ->
                "OUTLET_REASSIGNED".equals(log.getAction()) &&
                "GLOBAL".equals(log.getOldValue()) &&
                "3".equals(log.getNewValue()) &&
                Long.valueOf(301L).equals(log.getProductId())
        ));
    }

    @Test
    @DisplayName("getDeltaChangefeed excludes reassigned ID if product is already in updatedProducts")
    void testGetDeltaChangefeedExcludesReassignedIfInUpdatedProducts() {
        when(catalogService.getOutlets()).thenReturn(Collections.emptyList());
        when(catalogService.getCategories(1L)).thenReturn(Collections.emptyList());
        when(pricingEngine.getRulesByOutlet(1L)).thenReturn(Collections.emptyList());
        when(productRepository.findActiveCatalogByOutlet(1L)).thenReturn(Collections.emptyList());

        Instant since = Instant.now().minusSeconds(3600);

        PosProduct activeProd = PosProduct.builder().sku("SKU-MOVED-BACK").status(ProductStatus.ACTIVE).build();
        activeProd.setId(105L);

        when(productRepository.findUpdatedProductsSince(eq(1L), eq(since))).thenReturn(List.of(activeProd));
        when(catalogService.getProductById(105L)).thenReturn(ProductDetailDto.builder().id(105L).sku("SKU-MOVED-BACK").build());
        // 105L appears in reassigned audit log, but is also in updatedProducts
        when(auditLogRepository.findReassignedProductIdsForOutlet(eq("1"), eq(since))).thenReturn(List.of(105L, 106L));

        PosDeltaCatalogChangefeedDto delta = offlineSyncService.getDeltaChangefeed(1L, "\"diff-hash\"", since);
        assertThat(delta.getUpdatedProducts()).hasSize(1);
        assertThat(delta.getUpdatedProducts().get(0).getId()).isEqualTo(105L);
        // Only 106L should be in deletedProductIds; 105L must not be deleted
        assertThat(delta.getDeletedProductIds()).containsExactly(106L);
    }
}

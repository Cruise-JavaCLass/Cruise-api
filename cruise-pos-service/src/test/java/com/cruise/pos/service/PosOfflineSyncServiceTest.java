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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
}

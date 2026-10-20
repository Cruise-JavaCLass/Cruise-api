package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.OutletType;
import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.enums.TaxCategory;
import com.cruise.pos.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ObjectMapper objectMapper;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private PosOfflineSyncService offlineSyncService;

    @BeforeEach
    void setUp() {
        // By default, execute transaction callback synchronously
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
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
    @DisplayName("exportOfflinePackage generates package and computes ETag via serialization without full table scan")
    void testExportOfflinePackageComputesEtag() throws Exception {
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
        when(objectMapper.writeValueAsBytes(any())).thenReturn("package-json-content".getBytes());

        PosOfflineCatalogPackageDto pkg = offlineSyncService.exportOfflinePackage(1L);

        assertThat(pkg).isNotNull();
        assertThat(pkg.getEtag()).isNotNull();
        assertThat(pkg.getProducts()).hasSize(1);
        verify(productRepository, never()).findAll(); // Verifies table scan was removed!
    }
}

package com.cruise.pos.service;

import com.cruise.pos.dto.ProductDetailDto;
import com.cruise.pos.entity.PosCategory;
import com.cruise.pos.entity.PosOutlet;
import com.cruise.pos.entity.PosProduct;
import com.cruise.pos.enums.OutletType;
import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.enums.TaxCategory;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.PosCategoryRepository;
import com.cruise.pos.repository.PosOutletRepository;
import com.cruise.pos.repository.PosProductModifierRepository;
import com.cruise.pos.repository.PosProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BarcodeLookupService Unit Tests")
class BarcodeLookupServiceTest {

    @Mock
    private PosProductRepository productRepository;

    @Mock
    private PosProductModifierRepository modifierRepository;

    @Mock
    private PosOutletRepository outletRepository;

    @Mock
    private PosCategoryRepository categoryRepository;

    @InjectMocks
    private BarcodeLookupService barcodeLookupService;

    /**
     * Clears cached barcode lookups before each test.
     */
    @BeforeEach
    void setUp() {
        barcodeLookupService.evictCache();
    }

    /**
     * Verifies that repeated barcode lookups return the product without a second repository lookup.
     */
    @Test
    @DisplayName("lookupProductByBarcodeOrSku returns product and caches result for subsequent fast calls (< 50ms)")
    void testLookupProductSuccessAndCacheHit() {
        PosProduct product = PosProduct.builder()
                .sku("BEV-MOJ-001")
                .barcode("893850123401")
                .name("Pacific Sunset Mojito")
                .basePrice(new BigDecimal("14.50"))
                .taxCategory(TaxCategory.STANDARD_VAT)
                .status(ProductStatus.ACTIVE)
                .categoryId(1L)
                .outletId(10L)
                .build();
        product.setId(50L);

        when(productRepository.findByCodeAndOutlet("893850123401", 10L)).thenReturn(List.of(product));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(PosCategory.builder().name("Cocktails").build()));
        when(outletRepository.findById(10L)).thenReturn(Optional.of(PosOutlet.builder().name("Sky Bar").outletType(OutletType.BAR).build()));
        when(modifierRepository.findByProductIdOrderByDisplayOrderAsc(50L)).thenReturn(Collections.emptyList());

        // First call: DB query (cache miss)
        ProductDetailDto firstCall = barcodeLookupService.lookupProductByBarcodeOrSku("893850123401", 10L);
        assertThat(firstCall).isNotNull();
        assertThat(firstCall.getSku()).isEqualTo("BEV-MOJ-001");
        verify(productRepository, times(1)).findByCodeAndOutlet("893850123401", 10L);

        // Second call: In-memory cache hit (no additional DB call)
        ProductDetailDto secondCall = barcodeLookupService.lookupProductByBarcodeOrSku("893850123401", 10L);
        assertThat(secondCall).isNotNull();
        assertThat(secondCall.getSku()).isEqualTo("BEV-MOJ-001");
        verify(productRepository, times(1)).findByCodeAndOutlet("893850123401", 10L);
    }

    /**
     * Verifies that an unknown barcode raises a not-found error after the fallback lookup.
     */
    @Test
    @DisplayName("lookupProductByBarcodeOrSku throws ResourceNotFoundException when barcode is unknown")
    void testLookupUnknownBarcodeThrowsNotFound() {
        when(productRepository.findByCodeAndOutlet("UNKNOWN-999", 1L)).thenReturn(Collections.emptyList());
        when(productRepository.findByCodeAndOutlet("UNKNOWN-999", null)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> barcodeLookupService.lookupProductByBarcodeOrSku("UNKNOWN-999", 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("UNKNOWN-999");
    }

    @Test
    @DisplayName("lookupProductByBarcodeOrSku excludes ARCHIVED products")
    void testLookupExcludesArchivedProducts() {
        PosProduct archived = PosProduct.builder()
                .sku("OLD-001")
                .barcode("111222")
                .name("Old Product")
                .basePrice(new BigDecimal("10.00"))
                .status(ProductStatus.ARCHIVED)
                .outletId(1L)
                .categoryId(1L)
                .build();
        archived.setId(99L);

        when(productRepository.findByCodeAndOutlet("111222", 1L)).thenReturn(List.of(archived));

        assertThatThrownBy(() -> barcodeLookupService.lookupProductByBarcodeOrSku("111222", 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("lookupProductByBarcodeOrSku prefers outlet match over global and barcode over SKU")
    void testLookupDeterministicPreference() {
        PosProduct globalSkuMatch = PosProduct.builder()
                .sku("MATCH-CODE")
                .barcode("OTHER-BARCODE")
                .name("Global SKU Match")
                .basePrice(new BigDecimal("10.00"))
                .status(ProductStatus.ACTIVE)
                .outletId(null)
                .categoryId(1L)
                .build();
        globalSkuMatch.setId(1L);

        PosProduct outletBarcodeMatch = PosProduct.builder()
                .sku("DIFFERENT-SKU")
                .barcode("MATCH-CODE")
                .name("Outlet Barcode Match")
                .basePrice(new BigDecimal("12.00"))
                .status(ProductStatus.ACTIVE)
                .outletId(10L)
                .categoryId(1L)
                .build();
        outletBarcodeMatch.setId(2L);

        when(productRepository.findByCodeAndOutlet("MATCH-CODE", 10L))
                .thenReturn(List.of(globalSkuMatch, outletBarcodeMatch));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(PosCategory.builder().name("Drinks").build()));
        when(outletRepository.findById(10L)).thenReturn(Optional.of(PosOutlet.builder().name("Lounge").build()));
        when(modifierRepository.findByProductIdOrderByDisplayOrderAsc(2L)).thenReturn(Collections.emptyList());

        ProductDetailDto result = barcodeLookupService.lookupProductByBarcodeOrSku("MATCH-CODE", 10L);
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getName()).isEqualTo("Outlet Barcode Match");
    }

    @Test
    @DisplayName("evictProduct removes targeted entry from cache")
    void testEvictProductTargeted() {
        PosProduct product = PosProduct.builder()
                .sku("SKU-100")
                .barcode("BAR-100")
                .name("Test Product")
                .basePrice(new BigDecimal("5.00"))
                .status(ProductStatus.ACTIVE)
                .outletId(1L)
                .categoryId(1L)
                .build();
        product.setId(100L);

        when(productRepository.findByCodeAndOutlet("BAR-100", 1L)).thenReturn(List.of(product));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(PosCategory.builder().name("Snacks").build()));
        when(outletRepository.findById(1L)).thenReturn(Optional.of(PosOutlet.builder().name("Cafe").build()));
        when(modifierRepository.findByProductIdOrderByDisplayOrderAsc(100L)).thenReturn(Collections.emptyList());

        barcodeLookupService.lookupProductByBarcodeOrSku("BAR-100", 1L);
        verify(productRepository, times(1)).findByCodeAndOutlet("BAR-100", 1L);

        // Evict
        barcodeLookupService.evictProduct("SKU-100", "BAR-100", 1L);

        // Next lookup should hit repository again
        barcodeLookupService.lookupProductByBarcodeOrSku("BAR-100", 1L);
        verify(productRepository, times(2)).findByCodeAndOutlet("BAR-100", 1L);
    }
}

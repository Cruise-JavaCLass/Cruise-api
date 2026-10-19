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

    @BeforeEach
    void setUp() {
        barcodeLookupService.evictCache();
    }

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

    @Test
    @DisplayName("lookupProductByBarcodeOrSku throws ResourceNotFoundException when barcode is unknown")
    void testLookupUnknownBarcodeThrowsNotFound() {
        when(productRepository.findByCodeAndOutlet("UNKNOWN-999", 1L)).thenReturn(Collections.emptyList());
        when(productRepository.findByCodeAndOutlet("UNKNOWN-999", null)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> barcodeLookupService.lookupProductByBarcodeOrSku("UNKNOWN-999", 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("UNKNOWN-999");
    }
}

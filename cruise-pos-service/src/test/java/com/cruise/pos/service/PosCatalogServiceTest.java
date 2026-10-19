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
import com.cruise.pos.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PosCatalogService Unit Tests")
class PosCatalogServiceTest {

    @Mock
    private PosOutletRepository outletRepository;

    @Mock
    private PosCategoryRepository categoryRepository;

    @Mock
    private PosProductRepository productRepository;

    @Mock
    private PosProductModifierRepository modifierRepository;

    @Mock
    private PosHappyHourRuleRepository happyHourRuleRepository;

    @Mock
    private PosCatalogAuditLogRepository auditLogRepository;

    @InjectMocks
    private PosCatalogService catalogService;

    @Test
    @DisplayName("getOutlets returns active outlets list")
    void testGetOutlets() {
        PosOutlet bar = PosOutlet.builder()
                .code("SKY-BAR")
                .name("Sky Bar")
                .outletType(OutletType.BAR)
                .isActive(true)
                .build();
        bar.setId(1L);

        when(outletRepository.findByIsActiveTrue()).thenReturn(List.of(bar));

        List<OutletDto> result = catalogService.getOutlets();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("SKY-BAR");
    }

    @Test
    @DisplayName("getCategories builds hierarchical tree with root and child categories")
    void testGetCategoriesHierarchical() {
        PosCategory parent = PosCategory.builder()
                .code("BEVERAGES")
                .name("Beverages")
                .outletId(1L)
                .parentId(null)
                .displayOrder(1)
                .isActive(true)
                .build();
        parent.setId(10L);

        PosCategory child = PosCategory.builder()
                .code("COCKTAILS")
                .name("Cocktails")
                .outletId(1L)
                .parentId(10L)
                .displayOrder(1)
                .isActive(true)
                .build();
        child.setId(11L);

        when(categoryRepository.findCategoriesByOutlet(1L)).thenReturn(List.of(parent, child));

        List<CategoryDto> result = catalogService.getCategories(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("BEVERAGES");
        assertThat(result.get(0).getChildren()).hasSize(1);
        assertThat(result.get(0).getChildren().get(0).getCode()).isEqualTo("COCKTAILS");
    }

    @Test
    @DisplayName("createProduct throws DuplicateResourceException when SKU already exists")
    void testCreateProductDuplicateSkuThrowsException() {
        CreateProductRequest req = CreateProductRequest.builder()
                .sku("DUP-SKU-001")
                .name("Mojito")
                .categoryId(1L)
                .basePrice(new BigDecimal("12.00"))
                .build();

        when(productRepository.existsBySku("DUP-SKU-001")).thenReturn(true);

        assertThatThrownBy(() -> catalogService.createProduct(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("DUP-SKU-001");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProduct throws DuplicateResourceException when barcode already exists")
    void testCreateProductDuplicateBarcodeThrowsException() {
        CreateProductRequest req = CreateProductRequest.builder()
                .sku("NEW-SKU-002")
                .barcode("893850000001")
                .name("Gin Tonic")
                .categoryId(1L)
                .basePrice(new BigDecimal("11.00"))
                .build();

        when(productRepository.existsBySku("NEW-SKU-002")).thenReturn(false);
        when(productRepository.existsByBarcode("893850000001")).thenReturn(true);

        assertThatThrownBy(() -> catalogService.createProduct(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("893850000001");
    }

    @Test
    @DisplayName("createProduct saves product and its modifiers successfully")
    void testCreateProductSuccess() {
        CreateProductRequest req = CreateProductRequest.builder()
                .sku("MOJ-001")
                .barcode("893850123001")
                .name("Mojito Cocktail")
                .categoryId(1L)
                .outletId(1L)
                .basePrice(new BigDecimal("14.00"))
                .modifiers(List.of(
                        ProductModifierDto.builder().name("Extra Lime").priceDelta(new BigDecimal("1.00")).build()
                ))
                .build();

        PosProduct saved = PosProduct.builder()
                .sku("MOJ-001")
                .name("Mojito Cocktail")
                .categoryId(1L)
                .outletId(1L)
                .basePrice(new BigDecimal("14.00"))
                .status(ProductStatus.ACTIVE)
                .isAvailable(true)
                .is86ed(false)
                .stockQuantity(100)
                .build();
        saved.setId(100L);

        when(productRepository.existsBySku("MOJ-001")).thenReturn(false);
        when(productRepository.save(any(PosProduct.class))).thenReturn(saved);
        when(modifierRepository.save(any(PosProductModifier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findById(100L)).thenReturn(Optional.of(saved));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(PosCategory.builder().name("Cocktails").build()));

        ProductDetailDto result = catalogService.createProduct(req);

        assertThat(result).isNotNull();
        assertThat(result.getSku()).isEqualTo("MOJ-001");
        verify(modifierRepository, times(1)).save(any(PosProductModifier.class));
    }

    @Test
    @DisplayName("updateProductStatus marks product 86'd (out-of-stock) and records audit log")
    void testUpdateProductStatusEightySixed() {
        PosProduct product = PosProduct.builder()
                .sku("LAVA-CAKE-001")
                .name("Lava Cake")
                .categoryId(2L)
                .basePrice(new BigDecimal("9.00"))
                .status(ProductStatus.ACTIVE)
                .isAvailable(true)
                .is86ed(false)
                .build();
        product.setId(200L);

        when(productRepository.findById(200L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(PosProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductStatusUpdateRequest req = ProductStatusUpdateRequest.builder()
                .is86ed(true)
                .reason("Kitchen ran out of fresh Belgian chocolate")
                .staffId("CHEF-MICHAEL")
                .build();

        ProductDetailDto result = catalogService.updateProductStatus(200L, req);

        assertThat(result.getStatus()).isEqualTo(ProductStatus.EIGHTY_SIXED);
        assertThat(result.getIs86ed()).isTrue();
        assertThat(result.getIsAvailable()).isFalse();
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("updateProductStatus restores 86'd product back to ACTIVE")
    void testUpdateProductStatusRestore86ed() {
        PosProduct product = PosProduct.builder()
                .sku("LAVA-CAKE-001")
                .name("Lava Cake")
                .categoryId(2L)
                .basePrice(new BigDecimal("9.00"))
                .status(ProductStatus.EIGHTY_SIXED)
                .isAvailable(false)
                .is86ed(true)
                .build();
        product.setId(200L);

        when(productRepository.findById(200L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(PosProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductStatusUpdateRequest req = ProductStatusUpdateRequest.builder()
                .is86ed(false)
                .staffId("CHEF-MICHAEL")
                .build();

        ProductDetailDto result = catalogService.updateProductStatus(200L, req);

        assertThat(result.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(result.getIs86ed()).isFalse();
        assertThat(result.getIsAvailable()).isTrue();
    }
}

package com.cruise.pos.controller;

import com.cruise.pos.config.PosConfig;
import com.cruise.pos.dto.*;
import com.cruise.pos.enums.OutletType;
import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.enums.TaxCategory;
import com.cruise.pos.enums.VoyageLocationStatus;
import com.cruise.pos.exception.DuplicateResourceException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PosCatalogController.class)
@Import(PosConfig.class)
@DisplayName("PosCatalogController MockMvc Integration Tests")
class PosCatalogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PosCatalogService catalogService;

    @MockitoBean
    private BarcodeLookupService barcodeLookupService;

    @MockitoBean
    private DynamicPricingEngine dynamicPricingEngine;

    @MockitoBean
    private MaritimeTaxEngine maritimeTaxEngine;

    @MockitoBean
    private PosOfflineSyncService offlineSyncService;

    /**
     * Verifies that active outlets are returned with their catalog fields.
     */
    @Test
    @DisplayName("GET /api/pos/outlets returns list of active outlets (200 OK)")
    void testGetOutlets() throws Exception {
        OutletDto outlet = OutletDto.builder()
                .id(1L)
                .code("SKY-BAR")
                .name("Sky Bar & Lounge")
                .outletType(OutletType.BAR)
                .isActive(true)
                .build();

        when(catalogService.getOutlets()).thenReturn(List.of(outlet));

        mockMvc.perform(get("/api/pos/outlets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("SKY-BAR"))
                .andExpect(jsonPath("$.data[0].name").value("Sky Bar & Lounge"));
    }

    /**
     * Verifies that the category endpoint returns the requested outlet category tree.
     */
    @Test
    @DisplayName("GET /api/pos/categories returns category tree (200 OK)")
    void testGetCategories() throws Exception {
        CategoryDto cat = CategoryDto.builder()
                .id(10L)
                .code("CAT-COCKTAILS")
                .name("Signature Cocktails")
                .displayOrder(1)
                .children(Collections.emptyList())
                .build();

        when(catalogService.getCategories(1L)).thenReturn(List.of(cat));

        mockMvc.perform(get("/api/pos/categories?outletId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("CAT-COCKTAILS"));
    }

    /**
     * Verifies that the product endpoint returns a page containing the expected SKU.
     */
    @Test
    @DisplayName("GET /api/pos/products returns paginated products list (200 OK)")
    void testGetProductsPaginated() throws Exception {
        ProductDto p = ProductDto.builder()
                .id(100L)
                .sku("BEV-MOJ-001")
                .name("Pacific Sunset Mojito")
                .basePrice(new BigDecimal("14.50"))
                .status(ProductStatus.ACTIVE)
                .build();

        when(catalogService.getProducts(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(p)));

        mockMvc.perform(get("/api/pos/products?outletId=1&page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].sku").value("BEV-MOJ-001"));
    }

    /**
     * Verifies successful product creation and the expected product details.
     */
    @Test
    @DisplayName("POST /api/pos/products creates new product (201 Created)")
    void testCreateProductSuccess() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .sku("NEW-GIN-001")
                .barcode("893850999001")
                .name("Botanical Gin & Tonic")
                .categoryId(1L)
                .basePrice(new BigDecimal("13.00"))
                .taxCategory(TaxCategory.STANDARD_VAT)
                .build();

        ProductDetailDto detail = ProductDetailDto.builder()
                .id(101L)
                .sku("NEW-GIN-001")
                .barcode("893850999001")
                .name("Botanical Gin & Tonic")
                .basePrice(new BigDecimal("13.00"))
                .status(ProductStatus.ACTIVE)
                .build();

        when(catalogService.createProduct(any(CreateProductRequest.class))).thenReturn(detail);

        mockMvc.perform(post("/api/pos/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sku").value("NEW-GIN-001"));
    }

    /**
     * Verifies that a duplicate SKU produces HTTP 409 with the service error message.
     */
    @Test
    @DisplayName("POST /api/pos/products returns 409 Conflict when SKU already exists")
    void testCreateProductDuplicateSkuConflict() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .sku("DUP-SKU-999")
                .name("Duplicate Drink")
                .categoryId(1L)
                .basePrice(new BigDecimal("10.00"))
                .build();

        when(catalogService.createProduct(any(CreateProductRequest.class)))
                .thenThrow(new DuplicateResourceException("Mã SKU 'DUP-SKU-999' đã tồn tại trong hệ thống"));

        mockMvc.perform(post("/api/pos/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mã SKU 'DUP-SKU-999' đã tồn tại trong hệ thống"));
    }

    /**
     * Verifies that marking a product out of stock returns its updated status and flag.
     */
    @Test
    @DisplayName("PATCH /api/pos/products/{id}/status toggles 86'd status (200 OK)")
    void testPatchProductStatus86ed() throws Exception {
        ProductStatusUpdateRequest req = ProductStatusUpdateRequest.builder()
                .is86ed(true)
                .reason("Out of stock")
                .staffId("BARTENDER-JOHN")
                .build();

        ProductDetailDto updated = ProductDetailDto.builder()
                .id(100L)
                .sku("BEV-MOJ-001")
                .name("Pacific Sunset Mojito")
                .status(ProductStatus.EIGHTY_SIXED)
                .is86ed(true)
                .isAvailable(false)
                .build();

        when(catalogService.updateProductStatus(eq(100L), any(ProductStatusUpdateRequest.class))).thenReturn(updated);

        mockMvc.perform(patch("/api/pos/products/100/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.is86ed").value(true))
                .andExpect(jsonPath("$.data.status").value("EIGHTY_SIXED"));
    }

    /**
     * Verifies that the barcode endpoint returns the matched barcode and SKU.
     */
    @Test
    @DisplayName("GET /api/pos/products/lookup finds product fast by barcode (200 OK)")
    void testLookupProductByBarcode() throws Exception {
        ProductDetailDto product = ProductDetailDto.builder()
                .id(50L)
                .sku("BEV-MOJ-001")
                .barcode("893850123401")
                .name("Pacific Sunset Mojito")
                .basePrice(new BigDecimal("14.50"))
                .build();

        when(barcodeLookupService.lookupProductByBarcodeOrSku("893850123401", 1L)).thenReturn(product);

        mockMvc.perform(get("/api/pos/products/lookup?code=893850123401&outletId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.barcode").value("893850123401"))
                .andExpect(jsonPath("$.data.sku").value("BEV-MOJ-001"));
    }

    /**
     * Verifies that the pricing endpoint returns the calculated subtotal and grand total.
     */
    @Test
    @DisplayName("POST /api/pos/pricing/calculate calculates cart total with maritime tax & gratuity (200 OK)")
    void testCalculateCartPricing() throws Exception {
        CartPriceCalculationRequest req = CartPriceCalculationRequest.builder()
                .outletId(1L)
                .voyageLocation(VoyageLocationStatus.IN_PORT)
                .items(List.of(
                        CartItemCalculationRequest.builder().productId(1L).quantity(2).build()
                ))
                .build();

        CartPriceCalculationResponse resp = CartPriceCalculationResponse.builder()
                .subtotal(new BigDecimal("20.00"))
                .happyHourDiscount(BigDecimal.ZERO)
                .taxableSubtotal(new BigDecimal("20.00"))
                .maritimeTax(new BigDecimal("2.00"))
                .serviceCharge(new BigDecimal("3.60"))
                .grandTotal(new BigDecimal("25.60"))
                .isDutyFree(false)
                .build();

        when(maritimeTaxEngine.calculateCartPricing(any(CartPriceCalculationRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/pos/pricing/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.subtotal").value(20.00))
                .andExpect(jsonPath("$.data.grandTotal").value(25.60));
    }

    @Test
    @DisplayName("GET /api/pos/products with invalid sortBy returns 400 Bad Request")
    void testGetProductsInvalidSortByReturns400() throws Exception {
        mockMvc.perform(get("/api/pos/products?sortBy=invalidField"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Trường sắp xếp không hợp lệ")));
    }

    @Test
    @DisplayName("GET /api/pos/products with invalid direction returns 400 Bad Request")
    void testGetProductsInvalidDirectionReturns400() throws Exception {
        mockMvc.perform(get("/api/pos/products?direction=SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Hướng sắp xếp không hợp lệ")));
    }

    @Test
    @DisplayName("POST /api/pos/products with invalid happyHourDiscountPercent returns 400 Bad Request")
    void testCreateProductInvalidDiscountPercentReturns400() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .sku("BEV-TEST-999")
                .name("Test Wine")
                .categoryId(1L)
                .basePrice(new BigDecimal("10.00"))
                .happyHourDiscountPercent(new BigDecimal("150.00"))
                .build();

        mockMvc.perform(post("/api/pos/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/pos/products cascades validation to modifiers and returns 400 on blank modifier name")
    void testCreateProductInvalidModifierReturns400() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .sku("BEV-TEST-888")
                .name("Test Drink")
                .categoryId(1L)
                .basePrice(new BigDecimal("10.00"))
                .modifiers(List.of(
                        ProductModifierDto.builder().name("").priceDelta(new BigDecimal("1.00")).build()
                ))
                .build();

        mockMvc.perform(post("/api/pos/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/pos/pricing/happy-hour-rules with invalid startTime format returns 400 Bad Request")
    void testCreateHappyHourRuleInvalidTimeFormatReturns400() throws Exception {
        HappyHourRuleDto dto = HappyHourRuleDto.builder()
                .name("Invalid Hour")
                .startTime("25:99")
                .endTime("18:00")
                .discountPercent(new BigDecimal("20.00"))
                .build();

        mockMvc.perform(post("/api/pos/pricing/happy-hour-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}

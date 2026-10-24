package com.cruise.pos.controller;

import com.cruise.pos.config.PosConfig;
import com.cruise.pos.dto.*;
import com.cruise.pos.entity.PosCatalogAuditLog;
import com.cruise.pos.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PosCatalogController.class)
@Import(PosConfig.class)
@DisplayName("PosOfflinePackageIntegrationTest MockMvc Tests")
class PosOfflinePackageIntegrationTest {

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
     * Verifies that offline export returns the package and its ETag header.
     */
    @Test
    @DisplayName("GET /api/pos/catalog/offline-package returns full package with ETag header (200 OK)")
    void testGetOfflinePackageSuccess() throws Exception {
        PosOfflineCatalogPackageDto pkg = PosOfflineCatalogPackageDto.builder()
                .version("1.0.0")
                .etag("\"abc12345\"")
                .exportedAt(Instant.now())
                .outletId(1L)
                .outlets(Collections.emptyList())
                .categories(Collections.emptyList())
                .products(Collections.emptyList())
                .build();

        when(offlineSyncService.exportOfflinePackage(1L)).thenReturn(pkg);

        mockMvc.perform(get("/api/pos/catalog/offline-package?outletId=1"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"abc12345\""))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.etag").value("\"abc12345\""));
    }

    /**
     * Verifies HTTP 304 and the ETag header when the request ETag matches the export.
     */
    @Test
    @DisplayName("GET /api/pos/catalog/offline-package returns 304 Not Modified when If-None-Match matches ETag")
    void testGetOfflinePackageNotModified() throws Exception {
        PosOfflineCatalogPackageDto pkg = PosOfflineCatalogPackageDto.builder()
                .version("1.0.0")
                .etag("\"abc12345\"")
                .exportedAt(Instant.now())
                .outletId(1L)
                .build();

        when(offlineSyncService.exportOfflinePackage(1L)).thenReturn(pkg);

        mockMvc.perform(get("/api/pos/catalog/offline-package?outletId=1")
                        .header("If-None-Match", "\"abc12345\""))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "\"abc12345\""));
    }

    /**
     * Verifies that the GZIP catalog packaging endpoint returns 200 with Content-Encoding gzip.
     */
    @Test
    @DisplayName("GET /api/pos/catalog/offline-package/gzip returns compressed gzip stream (200 OK)")
    void testGetOfflinePackageGzipSuccess() throws Exception {
        byte[] mockGzip = new byte[]{1, 2, 3, 4};
        when(offlineSyncService.exportGzipCatalogPackage(1L)).thenReturn(mockGzip);

        mockMvc.perform(get("/api/pos/catalog/offline-package/gzip?outletId=1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_ENCODING, "gzip"))
                .andExpect(content().bytes(mockGzip));
    }

    /**
     * Verifies that the delta changefeed endpoint returns changed items.
     */
    @Test
    @DisplayName("GET /api/pos/catalog/delta returns incremental updates (200 OK)")
    void testGetDeltaCatalogSuccess() throws Exception {
        PosDeltaCatalogChangefeedDto deltaDto = PosDeltaCatalogChangefeedDto.builder()
                .fromVersionHash("\"old-hash\"")
                .currentVersionHash("\"new-hash\"")
                .syncTimestamp(Instant.now())
                .outletId(1L)
                .requiresFullSnapshot(false)
                .updatedOutlets(List.of(
                        OutletDto.builder().id(1L).code("OUT-01").name("Pool Bar").build()
                ))
                .updatedProducts(List.of(
                        ProductDetailDto.builder()
                                .id(10L)
                                .sku("SKU-10")
                                .name("Mojito Updated")
                                .basePrice(new BigDecimal("12.50"))
                                .build()
                ))
                .deletedProductIds(List.of(99L))
                .build();

        when(offlineSyncService.getDeltaChangefeed(eq(1L), eq("\"old-hash\""), any())).thenReturn(deltaDto);

        mockMvc.perform(get("/api/pos/catalog/delta?outletId=1&versionHash=\"old-hash\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fromVersionHash").value("\"old-hash\""))
                .andExpect(jsonPath("$.data.currentVersionHash").value("\"new-hash\""))
                .andExpect(jsonPath("$.data.updatedProducts[0].sku").value("SKU-10"))
                .andExpect(jsonPath("$.data.deletedProductIds[0]").value(99))
                .andExpect(jsonPath("$.data.updatedOutlets[0].code").value("OUT-01"));
    }

    /**
     * Verifies that the bulk-import endpoint returns the manifest import counts.
     */
    @Test
    @DisplayName("POST /api/pos/catalog/bulk-import ingests supplier manifest products (200 OK)")
    void testBulkImportProducts() throws Exception {
        BulkCatalogImportRequest req = BulkCatalogImportRequest.builder()
                .supplierManifestNumber("MANIFEST-2026-10-A")
                .performedBy("CHIEF_PURSER")
                .products(List.of(
                        BulkProductImportItem.builder()
                                .sku("DF-PERF-888")
                                .name("Dior Sauvage 100ml")
                                .basePrice(new BigDecimal("110.00"))
                                .build()
                ))
                .build();

        BulkImportResponse resp = BulkImportResponse.builder()
                .totalItems(1)
                .successCount(1)
                .failedCount(0)
                .importedAt(Instant.now())
                .build();

        when(offlineSyncService.bulkImportProducts(any(BulkCatalogImportRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/pos/catalog/bulk-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.successCount").value(1));
    }

    /**
     * Verifies that the audit endpoint returns the expected catalog action.
     */
    @Test
    @DisplayName("GET /api/pos/catalog/audit-logs returns audit log entries (200 OK)")
    void testGetCatalogAuditLogs() throws Exception {
        PosCatalogAuditLog logEntry = PosCatalogAuditLog.builder()
                .sku("BEV-MOJ-001")
                .action("EIGHTY_SIXED")
                .performedBy("BARTENDER-JOHN")
                .reason("Fresh mint shortage")
                .build();

        when(offlineSyncService.getAuditLogs(any())).thenReturn(new PageImpl<>(List.of(logEntry)));

        mockMvc.perform(get("/api/pos/catalog/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].sku").value("BEV-MOJ-001"))
                .andExpect(jsonPath("$.data.content[0].action").value("EIGHTY_SIXED"));
    }
}

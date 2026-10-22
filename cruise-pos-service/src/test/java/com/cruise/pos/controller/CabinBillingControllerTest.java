package com.cruise.pos.controller;

import com.cruise.pos.config.PosConfig;
import com.cruise.pos.dto.*;
import com.cruise.pos.service.CabinBillingSplitService;
import com.cruise.pos.service.CabinCreditLimitEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CabinBillingController.class)
@Import(PosConfig.class)
@DisplayName("CabinBillingController WebMvc Tests")
class CabinBillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CabinBillingSplitService billingSplitService;

    @MockitoBean
    private CabinCreditLimitEngine creditLimitEngine;

    @Test
    @DisplayName("GET /api/pos/cabin-billing/{cabinNumber}/members should return cabin members")
    void testGetCabinMembers() throws Exception {
        CabinMemberDto member = CabinMemberDto.builder()
                .passengerId(101L)
                .guestName("Nguyễn Văn An")
                .isMinor(false)
                .isAuthorizedSigner(true)
                .individualCreditLimit(BigDecimal.valueOf(1000.00))
                .build();

        when(billingSplitService.getCabinMembers("CABIN-7012")).thenReturn(List.of(member));

        mockMvc.perform(get("/api/pos/cabin-billing/CABIN-7012/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].guestName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.data[0].isAuthorizedSigner").value(true));
    }

    @Test
    @DisplayName("POST /api/pos/cabin-billing/split should successfully split transaction")
    void testSplitBilling() throws Exception {
        CabinSplitBillingRequest req = CabinSplitBillingRequest.builder()
                .cabinNumber("CABIN-7012")
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-01")
                .splitShares(List.of(
                        CabinSplitShareRequest.builder().passengerId(101L).amount(BigDecimal.valueOf(50.00)).build()
                ))
                .build();

        CabinSplitBillingResponse res = CabinSplitBillingResponse.builder()
                .cabinNumber("CABIN-7012")
                .totalAmount(BigDecimal.valueOf(50.00))
                .splitCount(1)
                .splits(List.of(
                        CabinSplitBillingResponse.SplitShareResult.builder()
                                .passengerId(101L)
                                .guestName("Nguyễn Văn An")
                                .amount(BigDecimal.valueOf(50.00))
                                .transactionUuid("TX-SPLIT-01")
                                .build()
                ))
                .build();

        when(billingSplitService.processSplitBilling(any(CabinSplitBillingRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/pos/cabin-billing/split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(50.00))
                .andExpect(jsonPath("$.data.splits[0].transactionUuid").value("TX-SPLIT-01"));
    }

    @Test
    @DisplayName("POST /api/pos/cabin-billing/{cabinNumber}/check-limit should return limit check response")
    void testCheckCreditLimit() throws Exception {
        CabinCreditLimitCheckRequest req = CabinCreditLimitCheckRequest.builder()
                .amount(BigDecimal.valueOf(75.00))
                .passengerId(101L)
                .build();

        CabinCreditLimitCheckResponse res = CabinCreditLimitCheckResponse.builder()
                .cabinNumber("CABIN-7012")
                .passengerId(101L)
                .isAllowed(true)
                .isLocked(false)
                .isOverLimit(false)
                .requestedAmount(BigDecimal.valueOf(75.00))
                .cabinAvailableLimit(BigDecimal.valueOf(1500.00))
                .build();

        when(creditLimitEngine.checkCreditLimit(eq("CABIN-7012"), any(CabinCreditLimitCheckRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/pos/cabin-billing/CABIN-7012/check-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.overLimit").value(false));
    }

    @Test
    @DisplayName("POST /api/pos/cabin-billing/signatures should archive digital signature")
    void testArchiveSignature() throws Exception {
        ArchiveSignatureRequest req = ArchiveSignatureRequest.builder()
                .transactionUuid("TX-SIG-01")
                .cabinNumber("CABIN-7012")
                .signerName("Nguyễn Văn An")
                .signatureImageBase64("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAUAAAACWAQMAAABp4")
                .build();

        SignatureResponse res = SignatureResponse.builder()
                .id(1L)
                .transactionUuid("TX-SIG-01")
                .signerName("Nguyễn Văn An")
                .sha256Checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .createdAt(Instant.now())
                .build();

        when(billingSplitService.archiveSignature(any(ArchiveSignatureRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/pos/cabin-billing/signatures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sha256Checksum").isString());
    }
}

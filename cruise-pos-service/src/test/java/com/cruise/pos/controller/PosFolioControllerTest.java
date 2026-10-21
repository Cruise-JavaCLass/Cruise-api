package com.cruise.pos.controller;

import com.cruise.pos.dto.*;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.service.PosFolioService;
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
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PosFolioController.class)
@Import(com.cruise.pos.config.PosConfig.class)
@DisplayName("PosFolioController WebMvc Tests")
class PosFolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PosFolioService folioService;

    @MockitoBean
    private com.cruise.pos.service.FolioSettlementService folioSettlementService;

    @Test
    @DisplayName("GET /api/pos/folios/{cabinNumber} returns folio account")
    void testGetFolio() throws Exception {
        FolioAccountResponse response = FolioAccountResponse.builder()
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Nguyễn Văn An")
                .creditLimit(BigDecimal.valueOf(2000.00))
                .currentBalance(BigDecimal.ZERO)
                .availableLimit(BigDecimal.valueOf(2000.00))
                .status(FolioStatus.ACTIVE)
                .build();

        when(folioService.getFolioByCabin("CABIN-7012")).thenReturn(response);

        mockMvc.perform(get("/api/pos/folios/CABIN-7012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cabinNumber").value("CABIN-7012"))
                .andExpect(jsonPath("$.data.primaryGuestName").value("Nguyễn Văn An"));
    }

    @Test
    @DisplayName("POST /api/pos/folios creates new account")
    void testCreateFolio() throws Exception {
        CreateFolioRequest request = CreateFolioRequest.builder()
                .cabinNumber("CABIN-8080")
                .primaryGuestName("Lê Hoàng")
                .creditLimit(BigDecimal.valueOf(1500.00))
                .build();

        FolioAccountResponse response = FolioAccountResponse.builder()
                .cabinNumber("CABIN-8080")
                .primaryGuestName("Lê Hoàng")
                .creditLimit(BigDecimal.valueOf(1500.00))
                .currentBalance(BigDecimal.ZERO)
                .availableLimit(BigDecimal.valueOf(1500.00))
                .status(FolioStatus.ACTIVE)
                .build();

        when(folioService.createFolio(any(CreateFolioRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/pos/folios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cabinNumber").value("CABIN-8080"));
    }

    @Test
    @DisplayName("GET /api/pos/folios returns paginated folios")
    void testGetFolios() throws Exception {
        FolioAccountResponse item = FolioAccountResponse.builder()
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Nguyễn Văn An")
                .creditLimit(BigDecimal.valueOf(2000.00))
                .currentBalance(BigDecimal.ZERO)
                .availableLimit(BigDecimal.valueOf(2000.00))
                .status(FolioStatus.ACTIVE)
                .build();

        when(folioService.getFolios(any(), any(), any())).thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/pos/folios?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].cabinNumber").value("CABIN-7012"));
    }

    @Test
    @DisplayName("PUT /api/pos/folios/{cabinNumber}/credit-limit updates limit")
    void testUpdateCreditLimit() throws Exception {
        UpdateCreditLimitRequest request = UpdateCreditLimitRequest.builder()
                .creditLimit(BigDecimal.valueOf(3500.00))
                .reason("Nâng hạn mức phòng VIP")
                .build();

        FolioAccountResponse response = FolioAccountResponse.builder()
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Nguyễn Văn An")
                .creditLimit(BigDecimal.valueOf(3500.00))
                .currentBalance(BigDecimal.valueOf(500.00))
                .availableLimit(BigDecimal.valueOf(3000.00))
                .status(FolioStatus.ACTIVE)
                .build();

        when(folioService.updateCreditLimit(eq("CABIN-7012"), any())).thenReturn(response);

        mockMvc.perform(put("/api/pos/folios/CABIN-7012/credit-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.creditLimit").value(3500.00));
    }

    @Test
    @DisplayName("PUT /api/pos/folios/{cabinNumber}/status updates status")
    void testUpdateStatus() throws Exception {
        UpdateFolioStatusRequest request = UpdateFolioStatusRequest.builder()
                .status(FolioStatus.FROZEN)
                .reason("Khóa thẻ phòng")
                .build();

        FolioAccountResponse response = FolioAccountResponse.builder()
                .cabinNumber("CABIN-7012")
                .status(FolioStatus.FROZEN)
                .build();

        when(folioService.updateStatus(eq("CABIN-7012"), any())).thenReturn(response);

        mockMvc.perform(put("/api/pos/folios/CABIN-7012/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("FROZEN"));
    }

    @Test
    @DisplayName("POST /api/pos/folios/{cabinNumber}/ledger posts expense item")
    void testPostExpense() throws Exception {
        PostExpenseRequest request = PostExpenseRequest.builder()
                .transactionId("TX-123")
                .description("Spa Massage Therapy")
                .serviceArea("Lotus Spa")
                .amount(BigDecimal.valueOf(120.00))
                .build();

        FolioLedgerItemDto response = FolioLedgerItemDto.builder()
                .id(1L)
                .transactionId("TX-123")
                .description("Spa Massage Therapy")
                .serviceArea("Lotus Spa")
                .amount(BigDecimal.valueOf(120.00))
                .isReversal(false)
                .postedAt(Instant.now())
                .runningBalance(BigDecimal.valueOf(120.00))
                .build();

        when(folioService.postExpense(eq("CABIN-7012"), any())).thenReturn(response);

        mockMvc.perform(post("/api/pos/folios/CABIN-7012/ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionId").value("TX-123"))
                .andExpect(jsonPath("$.data.runningBalance").value(120.00));
    }

    @Test
    @DisplayName("GET /api/pos/folios/{cabinNumber}/ledger returns statement")
    void testGetFolioStatement() throws Exception {
        FolioStatementResponse response = FolioStatementResponse.builder()
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Nguyễn Văn An")
                .creditLimit(BigDecimal.valueOf(2000.00))
                .currentBalance(BigDecimal.valueOf(150.00))
                .availableLimit(BigDecimal.valueOf(1850.00))
                .status(FolioStatus.ACTIVE)
                .totalCharges(BigDecimal.valueOf(150.00))
                .totalCredits(BigDecimal.ZERO)
                .netBalance(BigDecimal.valueOf(150.00))
                .items(List.of(
                        FolioLedgerItemDto.builder()
                                .id(1L)
                                .transactionId("TX-101")
                                .description("Buffet Lunch")
                                .serviceArea("Main Dining Room")
                                .amount(BigDecimal.valueOf(150.00))
                                .isReversal(false)
                                .postedAt(Instant.now())
                                .runningBalance(BigDecimal.valueOf(150.00))
                                .build()
                ))
                .build();

        when(folioService.getFolioStatement(eq("CABIN-7012"), any())).thenReturn(response);

        mockMvc.perform(get("/api/pos/folios/CABIN-7012/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.netBalance").value(150.00))
                .andExpect(jsonPath("$.data.items[0].transactionId").value("TX-101"));
    }
}

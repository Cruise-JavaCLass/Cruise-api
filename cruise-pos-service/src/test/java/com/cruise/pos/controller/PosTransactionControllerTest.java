package com.cruise.pos.controller;

import com.cruise.pos.config.PosConfig;
import com.cruise.pos.dto.*;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.enums.TransactionType;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.InsufficientFolioBalanceException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.service.PosFolioService;
import com.cruise.pos.service.PosShiftService;
import com.cruise.pos.service.PosTransactionService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PosTransactionController.class, PosShiftController.class, PosFolioController.class})
@Import(PosConfig.class)
@DisplayName("Pos Controllers MockMvc Tests")
class PosTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PosTransactionService transactionService;

    @MockitoBean
    private PosShiftService shiftService;

    @MockitoBean
    private PosFolioService folioService;

    @MockitoBean
    private com.cruise.pos.service.FolioSettlementService folioSettlementService;

    @Test
    @DisplayName("POST /api/pos/transactions creates charge transaction successfully (201 Created)")
    void testCreateTransactionSuccess() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-01")
                .cabinNumber("CABIN-7012")
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .items(List.of(
                        TransactionItemRequest.builder()
                                .itemCode("BEV-001")
                                .itemName("Cocktail Mojito")
                                .category("BEVERAGE")
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(15.00))
                                .build()
                ))
                .build();

        TransactionResponse response = TransactionResponse.builder()
                .id(1L)
                .transactionUuid("TX-UUID-123")
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-01")
                .cabinNumber("CABIN-7012")
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .status(TransactionStatus.COMPLETED)
                .totalAmount(BigDecimal.valueOf(15.00))
                .build();

        when(transactionService.processChargeTransaction(any(), any(CreateTransactionRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/pos/transactions")
                        .header("Idempotency-Key", "IDEM-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionUuid").value("TX-UUID-123"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /api/pos/transactions returns 422 when folio balance is insufficient")
    void testCreateTransactionInsufficientBalance() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-01")
                .cabinNumber("CABIN-7012")
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .items(List.of(
                        TransactionItemRequest.builder()
                                .itemCode("EXP-001")
                                .itemName("Expensive Champagne")
                                .category("BEVERAGE")
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(5000.00))
                                .build()
                ))
                .build();

        when(transactionService.processChargeTransaction(any(), any(CreateTransactionRequest.class)))
                .thenThrow(new InsufficientFolioBalanceException("Tài khoản cabin không đủ hạn mức tín dụng"));

        mockMvc.perform(post("/api/pos/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tài khoản cabin không đủ hạn mức tín dụng"));
    }

    @Test
    @DisplayName("POST /api/pos/transactions/{id}/void voids transaction successfully")
    void testVoidTransactionSuccess() throws Exception {
        VoidTransactionRequest voidReq = VoidTransactionRequest.builder()
                .performedBy("CASHIER-01")
                .voidReason("Khách hủy đơn")
                .build();

        TransactionResponse response = TransactionResponse.builder()
                .id(1L)
                .status(TransactionStatus.VOIDED)
                .voidReason("Khách hủy đơn")
                .build();

        when(transactionService.voidTransaction(eq(1L), any(VoidTransactionRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/pos/transactions/1/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voidReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("VOIDED"));
    }

    @Test
    @DisplayName("POST /api/pos/transactions/{id}/refund processes refund successfully (201 Created)")
    void testRefundTransactionSuccess() throws Exception {
        RefundTransactionRequest refundReq = RefundTransactionRequest.builder()
                .refundAmount(BigDecimal.valueOf(20.00))
                .refundReason("Khách hoàn lại món")
                .performedBy("CASHIER-01")
                .managerPin("8888")
                .managerId("MGR-01")
                .build();

        TransactionResponse response = TransactionResponse.builder()
                .id(1L)
                .status(TransactionStatus.PARTIALLY_REFUNDED)
                .refundedAmount(BigDecimal.valueOf(20.00))
                .build();

        when(transactionService.refundTransaction(eq(1L), any(RefundTransactionRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/pos/transactions/1/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_REFUNDED"));
    }

    @Test
    @DisplayName("GET /api/pos/transactions returns 400 when page < 0 or size < 1")
    void testGetTransactionsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/pos/transactions")
                        .param("page", "-1")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/pos/transactions returns paged transactions")
    void testGetTransactions() throws Exception {
        TransactionResponse tx = TransactionResponse.builder()
                .id(1L)
                .transactionUuid("TX-1")
                .totalAmount(BigDecimal.valueOf(25.00))
                .status(TransactionStatus.COMPLETED)
                .build();

        when(transactionService.getTransactionsWithFilters(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(tx)));

        mockMvc.perform(get("/api/pos/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].transactionUuid").value("TX-1"));
    }

    @Test
    @DisplayName("GET /api/pos/folios/{cabinNumber} returns cabin folio")
    void testGetFolio() throws Exception {
        FolioAccountResponse folio = FolioAccountResponse.builder()
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Nguyễn Văn An")
                .availableLimit(BigDecimal.valueOf(1500.00))
                .build();

        when(folioService.getFolioByCabin("CABIN-7012")).thenReturn(folio);

        mockMvc.perform(get("/api/pos/folios/CABIN-7012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cabinNumber").value("CABIN-7012"))
                .andExpect(jsonPath("$.data.primaryGuestName").value("Nguyễn Văn An"));
    }
}

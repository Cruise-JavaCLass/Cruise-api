package com.cruise.pos.controller;

import com.cruise.pos.dto.CreateFolioDisputeRequest;
import com.cruise.pos.dto.DisputeResponse;
import com.cruise.pos.dto.FolioTransactionDto;
import com.cruise.pos.dto.PassengerFolioSummaryResponse;
import com.cruise.pos.enums.DisputeReason;
import com.cruise.pos.enums.DisputeStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.security.AuthPrincipal;
import com.cruise.pos.service.PassengerFolioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PassengerFolioController.class)
@Import({com.cruise.pos.config.PosConfig.class, com.cruise.pos.exception.GlobalExceptionHandler.class})
@DisplayName("PassengerFolioController WebMvc Tests")
class PassengerFolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PassengerFolioService passengerFolioService;

    @Test
    @DisplayName("GET /api/passengers/{id}/folio should return 200 and summary response")
    void testGetFolioSummary() throws Exception {
        SecurityContextHolder.clearContext();
        PassengerFolioSummaryResponse summary = PassengerFolioSummaryResponse.builder()
                .passengerId(101L)
                .cabinNumber("CABIN-7012")
                .passengerName("Nguyễn Văn An")
                .currentBalance(BigDecimal.valueOf(450.00))
                .creditLimit(BigDecimal.valueOf(1000.00))
                .availableLimit(BigDecimal.valueOf(550.00))
                .spendingPercentage(BigDecimal.valueOf(45.00))
                .warningThresholdReached(false)
                .limitExceeded(false)
                .status("ACTIVE")
                .spendingByCategory(Map.of("FOOD_AND_BEVERAGE", BigDecimal.valueOf(150.00)))
                .build();

        when(passengerFolioService.getFolioSummary(101L)).thenReturn(summary);

        mockMvc.perform(get("/api/passengers/101/folio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passengerId").value(101))
                .andExpect(jsonPath("$.data.cabinNumber").value("CABIN-7012"))
                .andExpect(jsonPath("$.data.currentBalance").value(450.00))
                .andExpect(jsonPath("$.data.spendingPercentage").value(45.00));
    }

    @Test
    @DisplayName("Ownership check: authenticated passenger allowed when ID matches, forbidden when different")
    void testOwnershipCheck() throws Exception {
        AuthPrincipal matchingPrincipal = new AuthPrincipal("101", "ROLE_PASSENGER", "CABIN-7012");
        TestingAuthenticationToken authMatch = new TestingAuthenticationToken(matchingPrincipal, null, "ROLE_PASSENGER");
        authMatch.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authMatch);

        when(passengerFolioService.getFolioSummary(101L)).thenReturn(PassengerFolioSummaryResponse.builder().build());
        mockMvc.perform(get("/api/passengers/101/folio"))
                .andExpect(status().isOk());

        AuthPrincipal differentPrincipal = new AuthPrincipal("999", "ROLE_PASSENGER", "CABIN-9999");
        TestingAuthenticationToken authDiff = new TestingAuthenticationToken(differentPrincipal, null, "ROLE_PASSENGER");
        authDiff.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authDiff);

        mockMvc.perform(get("/api/passengers/101/folio"))
                .andExpect(status().isForbidden());

        // Staff / Admin can access any passenger
        AuthPrincipal staffPrincipal = new AuthPrincipal("admin_user", "ROLE_STAFF", "CABIN-0000");
        TestingAuthenticationToken authStaff = new TestingAuthenticationToken(staffPrincipal, null, "ROLE_STAFF");
        authStaff.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authStaff);

        mockMvc.perform(get("/api/passengers/101/folio"))
                .andExpect(status().isOk());

        // Non-numeric passenger username fails closed with 403 Forbidden
        AuthPrincipal guestPrincipal = new AuthPrincipal("non_numeric_guest", "ROLE_GUEST", "CABIN-1111");
        TestingAuthenticationToken authGuest = new TestingAuthenticationToken(guestPrincipal, null, "ROLE_GUEST");
        authGuest.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authGuest);

        mockMvc.perform(get("/api/passengers/101/folio"))
                .andExpect(status().isForbidden());

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/passengers/{id}/folio/transactions should return 200 with default pagination when params omitted")
    void testGetTransactionsDefaultPagination() throws Exception {
        SecurityContextHolder.clearContext();
        FolioTransactionDto dto = FolioTransactionDto.builder()
                .id(1L)
                .transactionUuid("TX-123")
                .time(Instant.now())
                .locationCounter("BAR-01")
                .posCode("POS-10")
                .swipedBy("Nguyễn Văn An")
                .amount(BigDecimal.valueOf(50.00))
                .currency("USD")
                .category("FOOD_AND_BEVERAGE")
                .complimentary(false)
                .status("COMPLETED")
                .items(List.of())
                .build();

        when(passengerFolioService.getTransactions(eq(101L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/passengers/101/folio/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].transactionUuid").value("TX-123"));

        verify(passengerFolioService).getTransactions(eq(101L), any(), eq(PageRequest.of(0, 10)));
    }

    @Test
    @DisplayName("GET /api/passengers/{id}/folio/transactions should return 400 when page < 0 or size invalid")
    void testGetTransactionsInvalidPagination() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/passengers/101/folio/transactions?page=-1&size=10"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/passengers/101/folio/transactions?page=0&size=0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/passengers/101/folio/transactions?page=0&size=101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/passengers/{id}/folio/transactions/{txId}/dispute should return 201 Created")
    void testDisputeTransaction() throws Exception {
        SecurityContextHolder.clearContext();
        CreateFolioDisputeRequest request = CreateFolioDisputeRequest.builder()
                .reason(DisputeReason.INCORRECT_AMOUNT)
                .disputeAmount(BigDecimal.valueOf(25.00))
                .disputeNote("Bị tính tiền 2 lần ly cocktail")
                .build();

        DisputeResponse response = DisputeResponse.builder()
                .disputeId(1L)
                .disputeNumber("DISP-999")
                .transactionId("TX-123")
                .passengerId(101L)
                .cabinNumber("CABIN-7012")
                .reason(DisputeReason.INCORRECT_AMOUNT)
                .disputeAmount(BigDecimal.valueOf(25.00))
                .status(DisputeStatus.SUBMITTED)
                .passengerNote("Bị tính tiền 2 lần ly cocktail")
                .createdAt(Instant.now())
                .build();

        when(passengerFolioService.disputeTransaction(eq(101L), eq(1L), any(CreateFolioDisputeRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/passengers/101/folio/transactions/1/dispute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.disputeNumber").value("DISP-999"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("POST dispute without reason or with blank note should return 400 Bad Request")
    void testDisputeValidationFailure() throws Exception {
        SecurityContextHolder.clearContext();
        CreateFolioDisputeRequest invalidRequest = CreateFolioDisputeRequest.builder()
                .reason(null)
                .disputeAmount(BigDecimal.valueOf(10.00))
                .disputeNote("")
                .build();

        mockMvc.perform(post("/api/passengers/101/folio/transactions/1/dispute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Service throwing ResourceNotFoundException should be mapped to 404")
    void testResourceNotFoundMappedTo404() throws Exception {
        SecurityContextHolder.clearContext();
        when(passengerFolioService.getFolioSummary(999L))
                .thenThrow(new ResourceNotFoundException("Folio không tồn tại"));

        mockMvc.perform(get("/api/passengers/999/folio"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Service throwing BusinessRuleException should be mapped to 400")
    void testBusinessRuleMappedTo400() throws Exception {
        SecurityContextHolder.clearContext();
        CreateFolioDisputeRequest request = CreateFolioDisputeRequest.builder()
                .reason(DisputeReason.INCORRECT_AMOUNT)
                .disputeAmount(BigDecimal.valueOf(25.00))
                .disputeNote("Lỗi tính tiền")
                .build();

        when(passengerFolioService.disputeTransaction(eq(101L), eq(1L), any(CreateFolioDisputeRequest.class)))
                .thenThrow(new BusinessRuleException("Giao dịch không thuộc về cabin"));

        mockMvc.perform(post("/api/passengers/101/folio/transactions/1/dispute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

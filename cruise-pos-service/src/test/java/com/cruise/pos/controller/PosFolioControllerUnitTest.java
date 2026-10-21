package com.cruise.pos.controller;

import com.cruise.pos.dto.SettleFolioRequest;
import com.cruise.pos.dto.SettlementResponse;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.security.AuthPrincipal;
import com.cruise.pos.service.FolioSettlementService;
import com.cruise.pos.service.SettleOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosFolioControllerUnitTest {

    @Mock
    private FolioSettlementService settlementService;

    @InjectMocks
    private PosFolioController posFolioController;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("settleFolio with AuthPrincipal extracts username and ROLE_ authorities")
    void settleFolio_withAuthPrincipal_resolvesActorAndRoles() {
        AuthPrincipal principal = new AuthPrincipal("staff-01", "FINANCE_FRONT_DESK", null);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_FINANCE_FRONT_DESK"), new SimpleGrantedAuthority("NON_ROLE_AUTH"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        SettlementResponse response = SettlementResponse.builder()
                .settlementUuid("FOLIO-1")
                .cabinNumber("101")
                .invoiceNumber("INV-001")
                .netAmount(BigDecimal.valueOf(100))
                .settledAt(Instant.now())
                .build();

        when(settlementService.settle(eq("101"), eq("key-1"), eq(PaymentMethod.CASH), eq(BigDecimal.valueOf(100)), eq("notes"), eq(false), eq("staff-01"), eq(Set.of("FINANCE_FRONT_DESK"))))
                .thenReturn(new SettleOutcome(response, false));

        SettleFolioRequest request = new SettleFolioRequest();
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setAmountTendered(BigDecimal.valueOf(100));
        request.setNotes("notes");
        request.setForceWithHold(false);

        ResponseEntity<?> result = posFolioController.settleFolio("101", "key-1", request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getFirst("Idempotent-Replay")).isEqualTo("false");
        verify(settlementService).settle(eq("101"), eq("key-1"), eq(PaymentMethod.CASH), eq(BigDecimal.valueOf(100)), eq("notes"), eq(false), eq("staff-01"), eq(Set.of("FINANCE_FRONT_DESK")));
    }

    @Test
    @DisplayName("settleFolio with generic Authentication fallback and replayed true")
    void settleFolio_withGenericAuthAndReplay() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "generic-user",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        SettlementResponse response = SettlementResponse.builder()
                .settlementUuid("FOLIO-2")
                .cabinNumber("102")
                .invoiceNumber("INV-002")
                .netAmount(BigDecimal.valueOf(200))
                .settledAt(Instant.now())
                .build();

        when(settlementService.settle(eq("102"), eq("key-2"), eq(PaymentMethod.CREDIT_CARD), any(), any(), eq(false), eq("generic-user"), eq(Set.of("MANAGER"))))
                .thenReturn(new SettleOutcome(response, true));

        SettleFolioRequest request = new SettleFolioRequest();
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);

        ResponseEntity<?> result = posFolioController.settleFolio("102", "key-2", request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getFirst("Idempotent-Replay")).isEqualTo("true");
    }

    @Test
    @DisplayName("settleFolio with null authentication defaults actor to system and empty roles")
    void settleFolio_withNullAuthentication() {
        SettlementResponse response = SettlementResponse.builder()
                .settlementUuid("FOLIO-3")
                .cabinNumber("103")
                .invoiceNumber("INV-003")
                .netAmount(BigDecimal.valueOf(50))
                .settledAt(Instant.now())
                .build();

        when(settlementService.settle(eq("103"), any(), eq(PaymentMethod.CASH), any(), any(), eq(false), eq("system"), eq(Set.of())))
                .thenReturn(new SettleOutcome(response, false));

        SettleFolioRequest request = new SettleFolioRequest();
        request.setPaymentMethod(PaymentMethod.CASH);
        ResponseEntity<?> result = posFolioController.settleFolio("103", null, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}

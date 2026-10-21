package com.cruise.pos.controller;

import com.cruise.pos.dto.InvoiceMetadataDto;
import com.cruise.pos.dto.SettlementResponse;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.FolioSettlement;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.security.AuthPrincipal;
import com.cruise.pos.service.FolioSettlementService;
import com.cruise.pos.service.InvoicePdfService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceController Unit Tests")
class InvoiceControllerUnitTest {

    @Mock
    private InvoicePdfService invoicePdfService;
    @Mock
    private FolioSettlementService settlementService;
    @Mock
    private CabinFolioAccountRepository folioRepository;

    @InjectMocks
    private InvoiceController invoiceController;

    private Authentication createAuth(String username, String role, String cabinNumber) {
        return new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(username, role, cabinNumber),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    @DisplayName("Hành khách tải hoá đơn của chính cabin mình thành công")
    void downloadInvoice_PassengerOwnCabin_Success() {
        Authentication auth = createAuth("guest1", "PASSENGER", "7012");
        InvoicePdfService.InvoicePdfResult result =
                new InvoicePdfService.InvoicePdfResult(new byte[]{1, 2, 3}, "INV-7012.pdf", "INV-001");

        when(invoicePdfService.generate(eq("7012"), eq(false), anySet())).thenReturn(result);

        ResponseEntity<byte[]> response = invoiceController.downloadInvoice("7012", false, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new byte[]{1, 2, 3});
        assertThat(response.getHeaders().getFirst("X-Invoice-Number")).isEqualTo("INV-001");
    }

    @Test
    @DisplayName("Hành khách tải hoá đơn của cabin khác bị ném AccessDeniedException (403)")
    void downloadInvoice_PassengerOtherCabin_ThrowsAccessDenied() {
        Authentication auth = createAuth("guest1", "PASSENGER", "9999");

        assertThatThrownBy(() -> invoiceController.downloadInvoice("7012", false, auth))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("FORBIDDEN_FOLIO_ACCESS");
    }

    @Test
    @DisplayName("Nhân viên tài vụ (Finance) có quyền xem mọi cabin")
    void downloadInvoice_StaffFinance_Success() {
        Authentication auth = createAuth("staff1", "FINANCE_FRONT_DESK", null);
        InvoicePdfService.InvoicePdfResult result =
                new InvoicePdfService.InvoicePdfResult(new byte[]{1, 2, 3}, "INV-7012.pdf", "INV-001");

        when(invoicePdfService.generate(eq("7012"), eq(false), anySet())).thenReturn(result);

        ResponseEntity<byte[]> response = invoiceController.downloadInvoice("7012", false, auth);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Tra cứu metadata hoá đơn thành công")
    void getInvoiceMetadata_Success() {
        Authentication auth = createAuth("guest1", "PASSENGER", "7012");
        InvoiceMetadataDto metadata = InvoiceMetadataDto.builder()
                .invoiceNumber("INV-001")
                .cabinNumber("7012")
                .sha256("dummy-sha")
                .build();

        when(invoicePdfService.getMetadata("INV-001")).thenReturn(metadata);

        var response = invoiceController.getInvoiceMetadata("INV-001", auth);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getInvoiceNumber()).isEqualTo("INV-001");
    }

    @Test
    @DisplayName("Tra cứu settlement theo UUID thành công")
    void getSettlement_Success() {
        Authentication auth = createAuth("guest1", "PASSENGER", "7012");
        FolioSettlement settlement = FolioSettlement.builder()
                .settlementUuid("uuid-123")
                .folioId(10L)
                .cabinNumber("7012")
                .build();
        CabinFolioAccount folio = CabinFolioAccount.builder().build();

        when(settlementService.getSettlement("uuid-123")).thenReturn(settlement);
        when(folioRepository.findById(10L)).thenReturn(Optional.of(folio));
        when(settlementService.toResponse(any(), any())).thenReturn(SettlementResponse.builder().settlementUuid("uuid-123").build());

        var response = invoiceController.getSettlement("uuid-123", auth);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getSettlementUuid()).isEqualTo("uuid-123");
    }
}

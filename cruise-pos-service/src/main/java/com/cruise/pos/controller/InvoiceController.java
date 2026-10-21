package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.InvoiceMetadataDto;
import com.cruise.pos.dto.SettlementResponse;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.FolioSettlement;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.service.FolioSettlementService;
import com.cruise.pos.service.InvoicePdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Endpoint hoá đơn PDF và tra cứu settlement.
 *
 * <p>Endpoint tải hoá đơn trả về bytes PDF thô (không bọc {@link ApiResponse})
 * vì đây là luồng tải file, các endpoint còn lại vẫn dùng envelope chuẩn.</p>
 */
@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoicePdfService invoicePdfService;
    private final FolioSettlementService settlementService;
    private final CabinFolioAccountRepository folioRepository;

    @GetMapping("/folios/{cabinNumber}/invoice")
    public ResponseEntity<byte[]> downloadInvoice(
            @PathVariable("cabinNumber") String cabinNumber,
            @RequestParam(value = "regenerate", defaultValue = "false") boolean regenerate,
            Authentication authentication) {

        assertPassengerOwnsCabin(authentication, cabinNumber);

        InvoicePdfService.InvoicePdfResult result =
                invoicePdfService.generate(cabinNumber, regenerate, resolveRoles(authentication));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(result.fileName(), StandardCharsets.UTF_8)
                .build());
        headers.set("X-Invoice-Number", result.invoiceNumber());
        headers.setContentLength(result.content().length);

        return new ResponseEntity<>(result.content(), headers, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/invoices/{invoiceNumber}")
    public ApiResponse<InvoiceMetadataDto> getInvoiceMetadata(
            @PathVariable("invoiceNumber") String invoiceNumber,
            Authentication authentication) {
        InvoiceMetadataDto metadata = invoicePdfService.getMetadata(invoiceNumber);
        assertPassengerOwnsCabin(authentication, metadata.getCabinNumber());
        return ApiResponse.ok(metadata);
    }

    @GetMapping("/settlements/{settlementUuid}")
    public ApiResponse<SettlementResponse> getSettlement(
            @PathVariable("settlementUuid") String settlementUuid,
            Authentication authentication) {
        FolioSettlement settlement = settlementService.getSettlement(settlementUuid);
        assertPassengerOwnsCabin(authentication, settlement.getCabinNumber());
        CabinFolioAccount folio = folioRepository.findById(settlement.getFolioId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy folio của settlement: " + settlementUuid));
        return ApiResponse.ok(settlementService.toResponse(settlement, folio.getStatus()));
    }

    /**
     * Hành khách chỉ được tải hoá đơn của cabin mình. Các role vận hành được xem tất cả.
     */
    private void assertPassengerOwnsCabin(Authentication authentication, String cabinNumber) {
        Authentication auth = resolveAuth(authentication);
        if (auth == null) {
            return;
        }
        Set<String> roles = resolveRoles(auth);
        boolean isPassenger = roles.contains("PASSENGER");
        if (!isPassenger) {
            return;
        }
        if (!(auth.getPrincipal() instanceof com.cruise.pos.security.AuthPrincipal principal)
                || principal.cabinNumber() == null
                || !principal.cabinNumber().equals(cabinNumber)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "FORBIDDEN_FOLIO_ACCESS: Bạn chỉ được tải hoá đơn của cabin mình");
        }
    }

    private Authentication resolveAuth(Authentication authentication) {
        if (authentication != null) {
            return authentication;
        }
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Set<String> resolveRoles(Authentication authentication) {
        Authentication auth = resolveAuth(authentication);
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toSet());
    }
}

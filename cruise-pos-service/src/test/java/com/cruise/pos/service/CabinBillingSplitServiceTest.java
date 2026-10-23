package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.DuplicateResourceException;
import com.cruise.pos.exception.InsufficientFolioBalanceException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CabinBillingSplitServiceTest {

    @Mock
    private CabinFolioAccountRepository folioRepository;

    @Mock
    private CabinMemberRepository memberRepository;

    @Mock
    private PosSignatureRepository signatureRepository;

    @Mock
    private PosTransactionRepository transactionRepository;

    @Mock
    private PosAuditLogRepository auditLogRepository;

    @Mock
    private PosFolioService folioService;

    @InjectMocks
    private CabinBillingSplitService billingSplitService;

    private CabinFolioAccount activeFolio;
    private CabinMember adultMember1;
    private CabinMember adultMember2;
    private CabinMember minorMember;

    @BeforeEach
    void setUp() {
        activeFolio = CabinFolioAccount.builder()
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Nguyễn Văn An")
                .passengerId(101L)
                .creditLimit(BigDecimal.valueOf(2000.00))
                .currentBalance(BigDecimal.valueOf(500.00))
                .status(FolioStatus.ACTIVE)
                .build();

        adultMember1 = CabinMember.builder()
                .cabinNumber("CABIN-7012")
                .passengerId(101L)
                .guestName("Nguyễn Văn An")
                .age(38)
                .isMinor(false)
                .isAuthorizedSigner(true)
                .individualCreditLimit(BigDecimal.valueOf(1000.00))
                .individualBalance(BigDecimal.valueOf(200.00))
                .build();

        adultMember2 = CabinMember.builder()
                .cabinNumber("CABIN-7012")
                .passengerId(102L)
                .guestName("Trần Thị Mai")
                .age(35)
                .isMinor(false)
                .isAuthorizedSigner(true)
                .individualCreditLimit(BigDecimal.valueOf(500.00))
                .individualBalance(BigDecimal.valueOf(50.00))
                .build();

        minorMember = CabinMember.builder()
                .cabinNumber("CABIN-7012")
                .passengerId(103L)
                .guestName("Nguyễn Bé")
                .age(10)
                .isMinor(true)
                .isAuthorizedSigner(false)
                .individualCreditLimit(BigDecimal.valueOf(100.00))
                .individualBalance(BigDecimal.ZERO)
                .build();
    }

    @Test
    @DisplayName("Should successfully archive valid signature with SHA-256 checksum and audit log")
    void testArchiveSignatureSuccess() {
        String base64 = "data:image/png;base64,VkFMSURfVEVTVF9ESUdJVEFMX1NJR05BVFVSRV9QQVlMT0FEX0ZPUl9VTklUX1RFU1RJTkc=";
        ArchiveSignatureRequest req = ArchiveSignatureRequest.builder()
                .transactionUuid("TX-SPLIT-001")
                .transactionId(999L)
                .cabinNumber("CABIN-7012")
                .passengerId(101L)
                .signerName("Nguyễn Văn An")
                .signatureImageBase64(base64)
                .capturedBy("CASHIER-01")
                .amount(BigDecimal.valueOf(150.00))
                .build();

        when(signatureRepository.existsByTransactionUuid("TX-SPLIT-001")).thenReturn(false);
        when(signatureRepository.save(any(PosSignature.class))).thenAnswer(inv -> {
            PosSignature s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        SignatureResponse res = billingSplitService.archiveSignature(req);

        assertThat(res).isNotNull();
        assertThat(res.getTransactionUuid()).isEqualTo("TX-SPLIT-001");
        assertThat(res.getSha256Checksum()).isNotBlank();
        verify(signatureRepository, times(1)).save(any(PosSignature.class));
        verify(auditLogRepository, times(1)).save(any(PosAuditLog.class));
    }

    @Test
    @DisplayName("Should reject blank or duplicate signature archive")
    void testArchiveSignatureValidation() {
        ArchiveSignatureRequest shortReq = ArchiveSignatureRequest.builder()
                .transactionUuid("TX-SPLIT-002")
                .cabinNumber("CABIN-7012")
                .signerName("Nguyễn Văn An")
                .signatureImageBase64("too_short")
                .build();

        when(signatureRepository.existsByTransactionUuid("TX-SPLIT-002")).thenReturn(false);

        assertThatThrownBy(() -> billingSplitService.archiveSignature(shortReq))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không hợp lệ");

        when(signatureRepository.existsByTransactionUuid("TX-SPLIT-DUP")).thenReturn(true);
        ArchiveSignatureRequest dupReq = ArchiveSignatureRequest.builder()
                .transactionUuid("TX-SPLIT-DUP")
                .cabinNumber("CABIN-7012")
                .signerName("Nguyễn Văn An")
                .signatureImageBase64("data:image/png;base64,valid_length_longer_than_30_bytes")
                .build();

        assertThatThrownBy(() -> billingSplitService.archiveSignature(dupReq))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Should accurately route split billing across adult room occupants with cent precision")
    void testProcessSplitBillingSuccess() {
        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(activeFolio));
        when(memberRepository.existsByCabinNumber("CABIN-7012")).thenReturn(true);
        when(memberRepository.findByCabinNumberAndPassengerId("CABIN-7012", 101L)).thenReturn(Optional.of(adultMember1));
        when(memberRepository.findByCabinNumberAndPassengerId("CABIN-7012", 102L)).thenReturn(Optional.of(adultMember2));
        when(transactionRepository.save(any(PosTransaction.class))).thenAnswer(inv -> {
            PosTransaction tx = inv.getArgument(0);
            tx.setId(123L);
            return tx;
        });

        // Split $100.55: $50.28 to guest 1, $50.27 to guest 2
        CabinSplitBillingRequest req = CabinSplitBillingRequest.builder()
                .cabinNumber("CABIN-7012")
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-01")
                .transactionUuid("TX-PARENT-01")
                .splitShares(List.of(
                        CabinSplitShareRequest.builder().passengerId(101L).amount(new BigDecimal("50.28")).build(),
                        CabinSplitShareRequest.builder().passengerId(102L).amount(new BigDecimal("50.27")).build()
                ))
                .build();

        CabinSplitBillingResponse res = billingSplitService.processSplitBilling(req);

        assertThat(res).isNotNull();
        assertThat(res.getTotalAmount()).isEqualByComparingTo("100.55");
        assertThat(res.getSplitCount()).isEqualTo(2);
        assertThat(res.getSplits()).hasSize(2);
        assertThat(activeFolio.getCurrentBalance()).isEqualByComparingTo("600.55"); // 500 + 100.55
        verify(folioRepository, times(1)).save(activeFolio);
    }

    @Test
    @DisplayName("Should lock transaction when split share is routed to unauthorized minor")
    void testMinorSignerLock() {
        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(activeFolio));
        when(memberRepository.existsByCabinNumber("CABIN-7012")).thenReturn(true);
        when(memberRepository.findByCabinNumberAndPassengerId("CABIN-7012", 103L)).thenReturn(Optional.of(minorMember));

        CabinSplitBillingRequest req = CabinSplitBillingRequest.builder()
                .cabinNumber("CABIN-7012")
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-01")
                .splitShares(List.of(
                        CabinSplitShareRequest.builder().passengerId(103L).amount(new BigDecimal("30.00")).build()
                ))
                .build();

        assertThatThrownBy(() -> billingSplitService.processSplitBilling(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Authorized Signer Lock");
    }

    @Test
    @DisplayName("Should lock transaction when individual member exceeds their credit limit")
    void testMemberOverLimitLock() {
        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(activeFolio));
        when(memberRepository.existsByCabinNumber("CABIN-7012")).thenReturn(true);
        when(memberRepository.findByCabinNumberAndPassengerId("CABIN-7012", 102L)).thenReturn(Optional.of(adultMember2));
        // adultMember2: limit 500, balance 50 -> avail 450. Request 500 -> exceeds.

        CabinSplitBillingRequest req = CabinSplitBillingRequest.builder()
                .cabinNumber("CABIN-7012")
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-01")
                .splitShares(List.of(
                        CabinSplitShareRequest.builder().passengerId(102L).amount(new BigDecimal("500.00")).build()
                ))
                .build();

        assertThatThrownBy(() -> billingSplitService.processSplitBilling(req))
                .isInstanceOf(InsufficientFolioBalanceException.class)
                .hasMessageContaining("Over-limit Lock");
    }

    @Test
    @DisplayName("Should return cabin members when getCabinMembers is called")
    void testGetCabinMembers() {
        when(memberRepository.existsByCabinNumber("CABIN-7012")).thenReturn(true);
        when(memberRepository.findByCabinNumber("CABIN-7012")).thenReturn(List.of(adultMember1, adultMember2));

        List<CabinMemberDto> result = billingSplitService.getCabinMembers("CABIN-7012");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getGuestName()).isEqualTo("Nguyễn Văn An");
        assertThat(result.get(0).getAvailableLimit()).isEqualByComparingTo("800.00"); // 1000 - 200
        assertThat(result.get(1).getGuestName()).isEqualTo("Trần Thị Mai");
    }

    @Test
    @DisplayName("Should seed primary guest when cabin has no members yet")
    void testEnsureSeededCabinMembersWhenEmpty() {
        when(memberRepository.existsByCabinNumber("CABIN-9999")).thenReturn(false);
        when(folioRepository.findByCabinNumber("CABIN-9999")).thenReturn(Optional.of(activeFolio));
        when(memberRepository.findByCabinNumber("CABIN-9999")).thenReturn(List.of());

        billingSplitService.getCabinMembers("CABIN-9999");

        verify(memberRepository, times(1)).save(any(CabinMember.class));
    }

    @Test
    @DisplayName("Should get signature by transaction UUID successfully")
    void testGetSignatureByTransactionUuidSuccess() {
        PosSignature sig = PosSignature.builder()
                .transactionUuid("TX-SIG-VALID")
                .signerName("Nguyễn Văn An")
                .signatureImageBase64("validbase64string...")
                .sha256Checksum("checksum123")
                .build();
        sig.setId(10L);

        when(signatureRepository.findByTransactionUuid("TX-SIG-VALID")).thenReturn(Optional.of(sig));

        SignatureResponse res = billingSplitService.getSignatureByTransactionUuid("TX-SIG-VALID");

        assertThat(res).isNotNull();
        assertThat(res.getTransactionUuid()).isEqualTo("TX-SIG-VALID");
        assertThat(res.getSignerName()).isEqualTo("Nguyễn Văn An");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when signature not found")
    void testGetSignatureByTransactionUuidNotFound() {
        when(signatureRepository.findByTransactionUuid("TX-NON-EXISTENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingSplitService.getSignatureByTransactionUuid("TX-NON-EXISTENT"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy chữ ký số");
    }

    @Test
    @DisplayName("Should throw InsufficientFolioBalanceException when total split exceeds cabin credit limit")
    void testProcessSplitBillingTotalExceedsCabinLimit() {
        // activeFolio limit 2000, balance 500 -> avail 1500. Request total 1600.
        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(activeFolio));
        when(memberRepository.existsByCabinNumber("CABIN-7012")).thenReturn(true);

        CabinSplitBillingRequest req = CabinSplitBillingRequest.builder()
                .cabinNumber("CABIN-7012")
                .splitShares(List.of(
                        CabinSplitShareRequest.builder().passengerId(101L).amount(new BigDecimal("1600.00")).build()
                ))
                .build();

        assertThatThrownBy(() -> billingSplitService.processSplitBilling(req))
                .isInstanceOf(InsufficientFolioBalanceException.class)
                .hasMessageContaining("không đủ hạn mức tín dụng");
    }

    @Test
    @DisplayName("Should reject invalid share amounts (<= 0 or null)")
    void testProcessSplitBillingInvalidShareAmount() {
        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(activeFolio));
        when(memberRepository.existsByCabinNumber("CABIN-7012")).thenReturn(true);

        CabinSplitBillingRequest req = CabinSplitBillingRequest.builder()
                .cabinNumber("CABIN-7012")
                .splitShares(List.of(
                        CabinSplitShareRequest.builder().passengerId(101L).amount(BigDecimal.ZERO).build()
                ))
                .build();

        assertThatThrownBy(() -> billingSplitService.processSplitBilling(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("lớn hơn 0");
    }

    @Test
    @DisplayName("Should reject split share when passenger does not belong to cabin")
    void testProcessSplitBillingMemberNotInCabin() {
        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(activeFolio));
        when(memberRepository.existsByCabinNumber("CABIN-7012")).thenReturn(true);
        when(memberRepository.findByCabinNumberAndPassengerId("CABIN-7012", 999L)).thenReturn(Optional.empty());

        CabinSplitBillingRequest req = CabinSplitBillingRequest.builder()
                .cabinNumber("CABIN-7012")
                .splitShares(List.of(
                        CabinSplitShareRequest.builder().passengerId(999L).amount(new BigDecimal("50.00")).build()
                ))
                .build();

        assertThatThrownBy(() -> billingSplitService.processSplitBilling(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không thuộc phòng");
    }

    @Test
    @DisplayName("Should process split billing with signature archival in share and null member individual limit")
    void testProcessSplitBillingWithSignatureAndNullIndividualLimit() {
        CabinMember memberNoLimit = CabinMember.builder()
                .cabinNumber("CABIN-7012")
                .passengerId(104L)
                .guestName("Lê Khách")
                .isMinor(false)
                .isAuthorizedSigner(true)
                .individualCreditLimit(null)
                .build();

        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(activeFolio));
        when(memberRepository.existsByCabinNumber("CABIN-7012")).thenReturn(true);
        when(memberRepository.findByCabinNumberAndPassengerId("CABIN-7012", 104L)).thenReturn(Optional.of(memberNoLimit));
        when(transactionRepository.save(any(PosTransaction.class))).thenAnswer(inv -> {
            PosTransaction tx = inv.getArgument(0);
            tx.setId(777L);
            return tx;
        });
        when(signatureRepository.existsByTransactionUuid(any())).thenReturn(false);
        when(signatureRepository.save(any(PosSignature.class))).thenAnswer(inv -> {
            PosSignature s = inv.getArgument(0);
            s.setId(888L);
            return s;
        });

        String sigBase64 = "data:image/png;base64,VkFMSURfVEVTVF9ESUdJVEFMX1NJR05BVFVSRV9QQVlMT0FEX0ZPUl9VTklUX1RFU1RJTkc=";
        CabinSplitBillingRequest req = CabinSplitBillingRequest.builder()
                .cabinNumber("CABIN-7012")
                .cashierId("CASHIER-99")
                .splitShares(List.of(
                        CabinSplitShareRequest.builder()
                                .passengerId(104L)
                                .amount(new BigDecimal("100.00"))
                                .signatureBase64(sigBase64)
                                .build()
                ))
                .build();

        CabinSplitBillingResponse res = billingSplitService.processSplitBilling(req);

        assertThat(res).isNotNull();
        assertThat(res.getSplits().get(0).isSignatureArchived()).isTrue();
        verify(signatureRepository, times(1)).save(any(PosSignature.class));
    }

    @Test
    @DisplayName("Should reject invalid non-base64 characters in signature archive")
    void testArchiveSignatureInvalidBase64() {
        ArchiveSignatureRequest invalidBase64Req = ArchiveSignatureRequest.builder()
                .transactionUuid("TX-SIG-BAD")
                .cabinNumber("CABIN-7012")
                .signerName("Nguyễn Văn An")
                .signatureImageBase64("this_is_a_very_long_string_with_invalid_characters_!!!@@@###$$$%%%")
                .build();

        when(signatureRepository.existsByTransactionUuid("TX-SIG-BAD")).thenReturn(false);

        assertThatThrownBy(() -> billingSplitService.archiveSignature(invalidBase64Req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Định dạng dữ liệu chữ ký Base64 không hợp lệ");
    }
}

package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.GuestFolioLedger;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.entity.ServiceRecoveryCase;
import com.cruise.pos.entity.ServiceRecoveryObcVoucher;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.enums.ObcVoucherStatus;
import com.cruise.pos.enums.ServiceRecoveryCaseStatus;
import com.cruise.pos.enums.ServiceRecoveryRootCause;
import com.cruise.pos.enums.ServiceRecoverySeverity;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.GuestFolioLedgerRepository;
import com.cruise.pos.repository.PosAuditLogRepository;
import com.cruise.pos.repository.ServiceRecoveryCaseRepository;
import com.cruise.pos.repository.ServiceRecoveryObcVoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceRecoveryServiceTest {

    @Mock
    private ServiceRecoveryCaseRepository caseRepository;

    @Mock
    private ServiceRecoveryObcVoucherRepository voucherRepository;

    @Mock
    private CabinFolioAccountRepository folioRepository;

    @Mock
    private GuestFolioLedgerRepository guestFolioLedgerRepository;

    @Mock
    private PosAuditLogRepository auditLogRepository;

    @InjectMocks
    private ServiceRecoveryService serviceRecoveryService;

    private ServiceRecoveryCase sampleCase;
    private CabinFolioAccount sampleFolio;

    @BeforeEach
    void setUp() {
        sampleCase = ServiceRecoveryCase.builder()
                .caseNumber("SRC-20261022-001")
                .passengerId(101L)
                .passengerName("Alexander Wright")
                .cabinNumber("CABIN-7012")
                .rootCause(ServiceRecoveryRootCause.STATEROOM_CABIN_NOISE)
                .severity(ServiceRecoverySeverity.HIGH)
                .status(ServiceRecoveryCaseStatus.OPEN)
                .complaintSummary("Excessive engine vibration noise at night")
                .assignedHandler("GUEST_RELATIONS_OFFICER")
                .slaDueAt(Instant.now().plus(6, ChronoUnit.HOURS))
                .build();
        sampleCase.setId(1L);

        sampleFolio = CabinFolioAccount.builder()
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Alexander Wright")
                .passengerId(101L)
                .creditLimit(BigDecimal.valueOf(2000.00))
                .currentBalance(BigDecimal.valueOf(350.00))
                .status(FolioStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("createCase: sets SLA due date and auto-escalates if CRITICAL")
    void testCreateCaseCriticalAutoEscalation() {
        CreateServiceRecoveryCaseRequest req = new CreateServiceRecoveryCaseRequest(
                101L,
                "Alexander Wright",
                "CABIN-7012",
                ServiceRecoveryRootCause.STATEROOM_PLUMBING_HVAC,
                ServiceRecoverySeverity.CRITICAL_DIRECTOR_ESCALATION,
                "Stateroom AC unit flooded floor",
                "Water ingress into carpet",
                null
        );

        when(caseRepository.save(any(ServiceRecoveryCase.class))).thenAnswer(inv -> {
            ServiceRecoveryCase c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        ServiceRecoveryCaseDto dto = serviceRecoveryService.createCase(req, "NIGHT_MANAGER");

        assertThat(dto).isNotNull();
        assertThat(dto.status()).isEqualTo(ServiceRecoveryCaseStatus.ESCALATED);
        assertThat(dto.escalationReason()).contains("Auto-escalated");
        assertThat(dto.slaDueAt()).isBeforeOrEqualTo(Instant.now().plus(3, ChronoUnit.HOURS)); // 2h SLA
        verify(auditLogRepository).save(any(PosAuditLog.class));
    }

    @Test
    @DisplayName("createCase: throws IllegalStateException when unique case number generation is exhausted")
    void testCreateCase_ExhaustedAttemptsThrows() {
        CreateServiceRecoveryCaseRequest req = new CreateServiceRecoveryCaseRequest(
                101L,
                "Alexander Wright",
                "CABIN-7012",
                ServiceRecoveryRootCause.STATEROOM_PLUMBING_HVAC,
                ServiceRecoverySeverity.LOW,
                "Minor issue",
                "Details",
                null
        );

        when(caseRepository.existsByCaseNumber(anyString())).thenReturn(true);

        assertThatThrownBy(() -> serviceRecoveryService.createCase(req, "STAFF"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Exhausted unique case number attempts");
    }

    @Test
    @DisplayName("escalateCase: updates case to ESCALATED and records escalation reason")
    void testEscalateCaseSuccess() {
        when(caseRepository.findById(1L)).thenReturn(Optional.of(sampleCase));
        when(caseRepository.save(any(ServiceRecoveryCase.class))).thenAnswer(inv -> inv.getArgument(0));

        EscalateCaseRequest req = new EscalateCaseRequest("Guest requested Director meeting regarding sleep disturbance");
        ServiceRecoveryCaseDto dto = serviceRecoveryService.escalateCase(1L, req, "DUTY_MANAGER");

        assertThat(dto.status()).isEqualTo(ServiceRecoveryCaseStatus.ESCALATED);
        assertThat(dto.severity()).isEqualTo(ServiceRecoverySeverity.CRITICAL_DIRECTOR_ESCALATION);
        assertThat(dto.escalatedAt()).isNotNull();
        assertThat(dto.escalationReason()).contains("Director meeting");
        assertThat(dto.slaDueAt()).isBeforeOrEqualTo(Instant.now().plus(3, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("escalateCase: throws IllegalStateException if case is already RESOLVED")
    void testEscalateCase_AlreadyResolvedThrows() {
        sampleCase.setStatus(ServiceRecoveryCaseStatus.RESOLVED);
        when(caseRepository.findById(1L)).thenReturn(Optional.of(sampleCase));

        EscalateCaseRequest req = new EscalateCaseRequest("Attempting escalation");
        assertThatThrownBy(() -> serviceRecoveryService.escalateCase(1L, req, "DUTY_MANAGER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot escalate case in status: RESOLVED");
    }

    @Test
    @DisplayName("issueObcVoucher: creates voucher and updates case status to COMPENSATION_OFFERED")
    void testIssueObcVoucher() {
        when(caseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(sampleCase));
        when(voucherRepository.findByCaseId(1L)).thenReturn(Collections.emptyList());
        when(voucherRepository.save(any(ServiceRecoveryObcVoucher.class))).thenAnswer(inv -> {
            ServiceRecoveryObcVoucher v = inv.getArgument(0);
            v.setId(99L);
            return v;
        });
        when(caseRepository.save(any(ServiceRecoveryCase.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueObcVoucherRequest req = new IssueObcVoucherRequest(
                BigDecimal.valueOf(150.00),
                "Sincere Apologies for Cabin Disturbance",
                "Please accept this $150 Onboard Credit for your specialty dining or spa experience.",
                48
        );

        ServiceRecoveryObcVoucherDto voucherDto = serviceRecoveryService.issueObcVoucher(1L, req, "DIRECTOR_MARCUS");

        assertThat(voucherDto).isNotNull();
        assertThat(voucherDto.creditAmount()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
        assertThat(voucherDto.status()).isEqualTo(ObcVoucherStatus.ISSUED);
        assertThat(voucherDto.voucherCode()).startsWith("OBC-REC-");
        assertThat(sampleCase.getStatus()).isEqualTo(ServiceRecoveryCaseStatus.COMPENSATION_OFFERED);

        verify(voucherRepository).save(any());
        verify(caseRepository).save(sampleCase);
    }

    @Test
    @DisplayName("issueObcVoucher: throws IllegalStateException if case is already resolved or closed")
    void testIssueObcVoucher_ResolvedCaseThrows() {
        sampleCase.setStatus(ServiceRecoveryCaseStatus.RESOLVED);
        when(caseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(sampleCase));

        IssueObcVoucherRequest req = new IssueObcVoucherRequest(
                BigDecimal.valueOf(50.00),
                "Apology",
                "Body",
                24
        );

        assertThatThrownBy(() -> serviceRecoveryService.issueObcVoucher(1L, req, "DIRECTOR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESOLVED");
    }

    @Test
    @DisplayName("issueObcVoucher: throws IllegalStateException if an active voucher already exists for the case")
    void testIssueObcVoucher_DuplicateActiveVoucherThrows() {
        when(caseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(sampleCase));
        ServiceRecoveryObcVoucher existing = ServiceRecoveryObcVoucher.builder()
                .status(ObcVoucherStatus.ISSUED)
                .build();
        when(voucherRepository.findByCaseId(1L)).thenReturn(List.of(existing));

        IssueObcVoucherRequest req = new IssueObcVoucherRequest(
                BigDecimal.valueOf(50.00),
                "Apology",
                "Body",
                24
        );

        assertThatThrownBy(() -> serviceRecoveryService.issueObcVoucher(1L, req, "DIRECTOR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been issued");
    }

    @Test
    @DisplayName("claimObcVoucher: credits guest folio ledger and reduces current balance using pessimistic lock")
    void testClaimObcVoucherToFolioSuccess() {
        ServiceRecoveryObcVoucher voucher = ServiceRecoveryObcVoucher.builder()
                .voucherCode("OBC-REC-778899")
                .caseId(1L)
                .caseNumber("SRC-20261022-001")
                .passengerId(101L)
                .passengerName("Alexander Wright")
                .cabinNumber("CABIN-7012")
                .creditAmount(BigDecimal.valueOf(100.00))
                .status(ObcVoucherStatus.ISSUED)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        voucher.setId(5L);

        when(voucherRepository.findByVoucherCodeWithLock("OBC-REC-778899")).thenReturn(Optional.of(voucher));
        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(sampleFolio));
        when(folioRepository.save(any(CabinFolioAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(guestFolioLedgerRepository.save(any(GuestFolioLedger.class))).thenAnswer(inv -> inv.getArgument(0));
        when(voucherRepository.save(any(ServiceRecoveryObcVoucher.class))).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal initialBalance = sampleFolio.getCurrentBalance(); // 350.00
        ClaimObcVoucherResponse res = serviceRecoveryService.claimObcVoucher("OBC-REC-778899", "Alexander Wright");

        assertThat(res).isNotNull();
        assertThat(res.creditedAmount()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(voucher.getStatus()).isEqualTo(ObcVoucherStatus.CLAIMED_TO_FOLIO);
        assertThat(sampleFolio.getCurrentBalance()).isEqualByComparingTo(initialBalance.subtract(BigDecimal.valueOf(100.00)));

        verify(guestFolioLedgerRepository).save(argThat(l -> 
                l.getServiceArea().equals("GUEST_RELATIONS_RECOVERY") &&
                Boolean.TRUE.equals(l.getIsReversal()) &&
                l.getAmount().compareTo(BigDecimal.valueOf(100.00)) == 0
        ));
    }

    @Test
    @DisplayName("claimObcVoucher: throws exception if voucher already claimed or expired")
    void testClaimObcVoucherAlreadyClaimedThrows() {
        ServiceRecoveryObcVoucher voucher = ServiceRecoveryObcVoucher.builder()
                .voucherCode("OBC-REC-ALREADY")
                .status(ObcVoucherStatus.CLAIMED_TO_FOLIO)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        when(voucherRepository.findByVoucherCodeWithLock("OBC-REC-ALREADY")).thenReturn(Optional.of(voucher));

        assertThatThrownBy(() -> serviceRecoveryService.claimObcVoucher("OBC-REC-ALREADY", "Guest"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been claimed");

        verify(folioRepository, never()).findByCabinNumberWithLock(any());
    }

    @Test
    @DisplayName("claimObcVoucher: throws exception if voucher has expired")
    void testClaimObcVoucherExpiredThrows() {
        ServiceRecoveryObcVoucher voucher = ServiceRecoveryObcVoucher.builder()
                .voucherCode("OBC-REC-EXPIRED")
                .status(ObcVoucherStatus.ISSUED)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(voucherRepository.findByVoucherCodeWithLock("OBC-REC-EXPIRED")).thenReturn(Optional.of(voucher));

        assertThatThrownBy(() -> serviceRecoveryService.claimObcVoucher("OBC-REC-EXPIRED", "Guest"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");

        assertThat(voucher.getStatus()).isEqualTo(ObcVoucherStatus.EXPIRED);
    }

    @Test
    @DisplayName("resolveCase: sets status to RESOLVED and stores resolution notes")
    void testResolveCaseSuccess() {
        when(caseRepository.findById(1L)).thenReturn(Optional.of(sampleCase));
        when(caseRepository.save(any(ServiceRecoveryCase.class))).thenAnswer(inv -> inv.getArgument(0));

        ResolveCaseRequest req = new ResolveCaseRequest("Offered $150 OBC and stateroom relocated to Deck 8");
        ServiceRecoveryCaseDto dto = serviceRecoveryService.resolveCase(1L, req, "DIRECTOR_MARCUS");

        assertThat(dto.status()).isEqualTo(ServiceRecoveryCaseStatus.RESOLVED);
        assertThat(dto.resolutionNotes()).contains("Offered $150 OBC");
        assertThat(dto.resolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("resolveCase: throws IllegalStateException if already resolved")
    void testResolveCase_AlreadyResolvedThrows() {
        sampleCase.setStatus(ServiceRecoveryCaseStatus.RESOLVED);
        when(caseRepository.findById(1L)).thenReturn(Optional.of(sampleCase));

        ResolveCaseRequest req = new ResolveCaseRequest("Already done");
        assertThatThrownBy(() -> serviceRecoveryService.resolveCase(1L, req, "DIRECTOR_MARCUS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in RESOLVED status");
    }

    @Test
    @DisplayName("searchCases, getCase, getVouchersForCase, and getPassengerVouchers delegate properly")
    void testQueries() {
        when(caseRepository.findById(1L)).thenReturn(Optional.of(sampleCase));
        assertThat(serviceRecoveryService.getCase(1L)).isNotNull();

        Page<ServiceRecoveryCase> page = new PageImpl<>(List.of(sampleCase));
        when(caseRepository.searchCases(any(), any(), any(), any())).thenReturn(page);
        Page<ServiceRecoveryCaseDto> searchRes = serviceRecoveryService.searchCases(null, null, null, PageRequest.of(0, 10));
        assertThat(searchRes.getTotalElements()).isEqualTo(1);

        ServiceRecoveryObcVoucher voucher = ServiceRecoveryObcVoucher.builder()
                .caseId(1L)
                .passengerId(101L)
                .creditAmount(BigDecimal.TEN)
                .build();
        when(voucherRepository.findByCaseId(1L)).thenReturn(List.of(voucher));
        when(voucherRepository.findByPassengerIdOrderByCreatedAtDesc(101L)).thenReturn(List.of(voucher));

        assertThat(serviceRecoveryService.getVouchersForCase(1L)).hasSize(1);
        assertThat(serviceRecoveryService.getPassengerVouchers(101L)).hasSize(1);
    }
}

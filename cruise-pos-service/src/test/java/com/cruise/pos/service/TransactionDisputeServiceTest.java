package com.cruise.pos.service;

import com.cruise.pos.entity.CabinFolio;
import com.cruise.pos.entity.FolioDispute;
import com.cruise.pos.entity.FolioDisputeAuditEntry;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.enums.CabinFolioStatus;
import com.cruise.pos.enums.FolioDisputeStatus;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.enums.TransactionType;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioRepository;
import com.cruise.pos.repository.FolioDisputeAuditEntryRepository;
import com.cruise.pos.repository.FolioDisputeRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionDisputeService Unit Tests")
class TransactionDisputeServiceTest {

    @Mock
    private FolioDisputeRepository disputeRepository;

    @Mock
    private FolioDisputeAuditEntryRepository auditRepository;

    @Mock
    private PosTransactionRepository transactionRepository;

    @Mock
    private CabinFolioRepository folioRepository;

    @InjectMocks
    private TransactionDisputeService service;

    private PosTransaction original;
    private CabinFolio folio;

    @BeforeEach
    void setUp() {
        original = PosTransaction.builder()
                .transactionUuid("TX-100")
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-1")
                .passengerId(7L)
                .cabinNumber("A-101")
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .type(TransactionType.CHARGE)
                .status(TransactionStatus.COMPLETED)
                .totalAmount(new BigDecimal("80.00"))
                .build();
        original.setId(100L);

        folio = CabinFolio.builder()
                .cabinNumber("A-101")
                .voyageId(1L)
                .creditLimit(new BigDecimal("500.00"))
                .currentBalance(new BigDecimal("80.00"))
                .status(CabinFolioStatus.OPEN)
                .build();
        folio.setId(1L);
    }

    private FolioDispute dispute(FolioDisputeStatus status, String amount) {
        FolioDispute dispute = FolioDispute.builder()
                .folio(folio)
                .transaction(original)
                .passengerId(7L)
                .disputeReason("Tính nhầm món")
                .disputedAmount(new BigDecimal(amount))
                .status(status)
                .build();
        dispute.setId(55L);
        return dispute;
    }

    /** Dựng các stub cần thiết để gửi khiếu nại thành công */
    private void stubRaisePath(BigDecimal approvedAmount) {
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(original));
        when(disputeRepository.existsByTransaction_IdAndStatusIn(eq(100L), anyCollection())).thenReturn(false);
        when(folioRepository.findFirstByCabinNumberAndStatusInOrderByIdDesc(eq("A-101"), anyCollection()))
                .thenReturn(Optional.of(folio));
        when(disputeRepository.sumDisputedAmount(eq(100L), anyCollection())).thenReturn(approvedAmount);
        when(disputeRepository.save(any(FolioDispute.class))).thenAnswer(inv -> {
            FolioDispute saved = inv.getArgument(0);
            saved.setId(55L);
            return saved;
        });
        when(auditRepository.save(any(FolioDisputeAuditEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubReviewPath(FolioDispute dispute) {
        when(disputeRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(FolioDispute.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any(FolioDisputeAuditEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------- raiseDispute

    @Test
    @DisplayName("Raising a dispute without an amount disputes the whole refundable amount")
    void shouldRaiseDisputeForFullRefundableAmount() {
        stubRaisePath(null);

        FolioDispute result = service.raiseDispute(7L, 100L, "  Món bị tính hai lần  ");

        assertThat(result.getStatus()).isEqualTo(FolioDisputeStatus.PENDING);
        assertThat(result.getDisputedAmount()).isEqualByComparingTo("80.00");
        assertThat(result.getDisputeReason()).isEqualTo("Món bị tính hai lần");
        assertThat(result.getFolio()).isSameAs(folio);

        ArgumentCaptor<FolioDisputeAuditEntry> audit = ArgumentCaptor.forClass(FolioDisputeAuditEntry.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("RAISED");
        assertThat(audit.getValue().getFromStatus()).isNull();
        assertThat(audit.getValue().getToStatus()).isEqualTo(FolioDisputeStatus.PENDING);
        assertThat(audit.getValue().getActorId()).isEqualTo("7");
    }

    @Test
    @DisplayName("A partial dispute amount is accepted")
    void shouldRaiseDisputeForPartialAmount() {
        stubRaisePath(null);

        FolioDispute result = service.raiseDispute(7L, 100L, "Một món bị tính nhầm", new BigDecimal("30"));

        assertThat(result.getDisputedAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("Disputed amount cannot exceed what is left to refund (after refunds and approved disputes)")
    void shouldRejectAmountAboveRefundable() {
        original.setRefundedAmount(new BigDecimal("20.00"));
        stubRaisePath(new BigDecimal("10.00")); // còn lại 80 - 20 - 10 = 50

        assertThatThrownBy(() -> service.raiseDispute(7L, 100L, "Tính nhầm", new BigDecimal("50.01")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("vượt");
        verify(disputeRepository, never()).save(any());

        assertThat(service.raiseDispute(7L, 100L, "Tính nhầm", new BigDecimal("50.00")).getDisputedAmount())
                .isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("A transaction can only have one unresolved dispute at a time")
    void shouldRejectSecondActiveDispute() {
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(original));
        when(disputeRepository.existsByTransaction_IdAndStatusIn(eq(100L), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.raiseDispute(7L, 100L, "Tính nhầm"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("chưa được xử lý");
    }

    @Test
    @DisplayName("Rejects disputes on someone else's purchase, non-charge transactions and unknown transactions")
    void shouldRejectInvalidTransactions() {
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(original));
        when(transactionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.raiseDispute(8L, 100L, "Không phải của tôi"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("không thuộc về");

        original.setType(TransactionType.REFUND_ADJUSTMENT);
        assertThatThrownBy(() -> service.raiseDispute(7L, 100L, "Tính nhầm"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("đã hoàn tất");

        original.setType(TransactionType.CHARGE);
        original.setStatus(TransactionStatus.VOIDED);
        assertThatThrownBy(() -> service.raiseDispute(7L, 100L, "Tính nhầm"))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> service.raiseDispute(7L, 404L, "Tính nhầm"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Rejects a blank reason or missing ids before touching the database")
    void shouldRejectBlankInput() {
        assertThatThrownBy(() -> service.raiseDispute(7L, 100L, "   "))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.raiseDispute(null, 100L, "Tính nhầm"))
                .isInstanceOf(BusinessRuleException.class);
        verify(transactionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Rejects a dispute when the cabin has no open folio")
    void shouldRejectWhenNoOpenFolio() {
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(original));
        when(disputeRepository.existsByTransaction_IdAndStatusIn(eq(100L), anyCollection())).thenReturn(false);
        when(folioRepository.findFirstByCabinNumberAndStatusInOrderByIdDesc(eq("A-101"), anyCollection()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.raiseDispute(7L, 100L, "Tính nhầm"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("A-101");
    }

    // ---------------------------------------------------------------- startReview

    @Test
    @DisplayName("Start review moves PENDING to IN_REVIEW and records an audit entry")
    void shouldStartReview() {
        FolioDispute dispute = dispute(FolioDisputeStatus.PENDING, "30.00");
        stubReviewPath(dispute);

        FolioDispute result = service.startReview(55L, "FIN-01");

        assertThat(result.getStatus()).isEqualTo(FolioDisputeStatus.IN_REVIEW);
        assertThat(result.getReviewerStaffId()).isEqualTo("FIN-01");

        ArgumentCaptor<FolioDisputeAuditEntry> audit = ArgumentCaptor.forClass(FolioDisputeAuditEntry.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("REVIEW_STARTED");
        assertThat(audit.getValue().getFromStatus()).isEqualTo(FolioDisputeStatus.PENDING);
        assertThat(audit.getValue().getToStatus()).isEqualTo(FolioDisputeStatus.IN_REVIEW);
    }

    @Test
    @DisplayName("Start review is only valid once (PENDING only)")
    void shouldRejectStartReviewOnNonPendingDispute() {
        when(disputeRepository.findByIdForUpdate(55L))
                .thenReturn(Optional.of(dispute(FolioDisputeStatus.IN_REVIEW, "30.00")));

        assertThatThrownBy(() -> service.startReview(55L, "FIN-01"))
                .isInstanceOf(BusinessRuleException.class);
        verify(auditRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- reviewDispute

    @Test
    @DisplayName("Approving creates a REFUND_ADJUSTMENT, reduces the folio balance and records an audit entry")
    void shouldApproveDisputeAndRefundFolio() {
        FolioDispute dispute = dispute(FolioDisputeStatus.IN_REVIEW, "30.00");
        stubReviewPath(dispute);
        when(disputeRepository.sumDisputedAmount(eq(100L), anyCollection())).thenReturn(null);
        when(folioRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(folio));
        when(transactionRepository.save(any(PosTransaction.class))).thenAnswer(inv -> {
            PosTransaction saved = inv.getArgument(0);
            saved.setId(900L);
            return saved;
        });
        when(folioRepository.save(any(CabinFolio.class))).thenAnswer(inv -> inv.getArgument(0));

        FolioDispute result = service.reviewDispute(55L, true, "Đã đối chiếu bill gốc", "FIN-01");

        assertThat(result.getStatus()).isEqualTo(FolioDisputeStatus.APPROVED);
        assertThat(result.getRefundTransactionId()).isEqualTo(900L);
        assertThat(result.getResolutionNotes()).isEqualTo("Đã đối chiếu bill gốc");
        assertThat(result.getReviewedAt()).isNotNull();
        assertThat(folio.getCurrentBalance()).isEqualByComparingTo("50.00");

        ArgumentCaptor<PosTransaction> refund = ArgumentCaptor.forClass(PosTransaction.class);
        verify(transactionRepository).save(refund.capture());
        assertThat(refund.getValue().getType()).isEqualTo(TransactionType.REFUND_ADJUSTMENT);
        assertThat(refund.getValue().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(refund.getValue().getTotalAmount()).isEqualByComparingTo("30.00");
        assertThat(refund.getValue().getOriginalTransactionId()).isEqualTo(100L);
        assertThat(refund.getValue().getCashierId()).isEqualTo("FIN-01");
        assertThat(refund.getValue().getCabinNumber()).isEqualTo("A-101");

        ArgumentCaptor<FolioDisputeAuditEntry> audit = ArgumentCaptor.forClass(FolioDisputeAuditEntry.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("APPROVED");
        assertThat(audit.getValue().getToStatus()).isEqualTo(FolioDisputeStatus.APPROVED);
    }

    @Test
    @DisplayName("A refund larger than the open balance floors the balance at zero")
    void shouldFloorBalanceAtZero() {
        folio.setCurrentBalance(new BigDecimal("10.00"));
        FolioDispute dispute = dispute(FolioDisputeStatus.IN_REVIEW, "30.00");
        stubReviewPath(dispute);
        when(disputeRepository.sumDisputedAmount(eq(100L), anyCollection())).thenReturn(null);
        when(folioRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(folio));
        when(transactionRepository.save(any(PosTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(folioRepository.save(any(CabinFolio.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reviewDispute(55L, true, null, "FIN-01");

        assertThat(folio.getCurrentBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Rejecting requires a note, leaves the folio untouched and records an audit entry")
    void shouldRejectDispute() {
        FolioDispute dispute = dispute(FolioDisputeStatus.IN_REVIEW, "30.00");
        stubReviewPath(dispute);

        FolioDispute result = service.reviewDispute(55L, false, "Giao dịch hợp lệ", "FIN-01");

        assertThat(result.getStatus()).isEqualTo(FolioDisputeStatus.REJECTED);
        assertThat(result.getResolutionNotes()).isEqualTo("Giao dịch hợp lệ");
        assertThat(folio.getCurrentBalance()).isEqualByComparingTo("80.00");
        verify(transactionRepository, never()).save(any());
        verify(folioRepository, never()).findByIdForUpdate(any());

        ArgumentCaptor<FolioDisputeAuditEntry> audit = ArgumentCaptor.forClass(FolioDisputeAuditEntry.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("Rejecting without a note, or without a reviewer, is refused before any change")
    void shouldRequireNotesAndReviewer() {
        assertThatThrownBy(() -> service.reviewDispute(55L, false, "  ", "FIN-01"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.reviewDispute(55L, true, "OK", " "))
                .isInstanceOf(BusinessRuleException.class);
        verify(disputeRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("State machine: a PENDING dispute cannot be approved without going through IN_REVIEW")
    void shouldNotReviewPendingDispute() {
        when(disputeRepository.findByIdForUpdate(55L))
                .thenReturn(Optional.of(dispute(FolioDisputeStatus.PENDING, "30.00")));

        assertThatThrownBy(() -> service.reviewDispute(55L, true, "OK", "FIN-01"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("IN_REVIEW");
        verify(transactionRepository, never()).save(any());
        verify(auditRepository, never()).save(any());
    }

    @Test
    @DisplayName("State machine: a resolved dispute cannot be reviewed again")
    void shouldNotReviewResolvedDispute() {
        when(disputeRepository.findByIdForUpdate(55L))
                .thenReturn(Optional.of(dispute(FolioDisputeStatus.APPROVED, "30.00")));

        assertThatThrownBy(() -> service.reviewDispute(55L, false, "Thử bác bỏ lại", "FIN-01"))
                .isInstanceOf(BusinessRuleException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Approval is refused when the folio is already settled, and nothing is created")
    void shouldNotApproveOnSettledFolio() {
        folio.setStatus(CabinFolioStatus.SETTLED);
        FolioDispute dispute = dispute(FolioDisputeStatus.IN_REVIEW, "30.00");
        when(disputeRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.sumDisputedAmount(eq(100L), anyCollection())).thenReturn(null);
        when(folioRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(folio));

        assertThatThrownBy(() -> service.reviewDispute(55L, true, "OK", "FIN-01"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("tất toán");
        verify(transactionRepository, never()).save(any());
        assertThat(dispute.getStatus()).isEqualTo(FolioDisputeStatus.IN_REVIEW);
    }

    @Test
    @DisplayName("Unknown dispute id raises ResourceNotFoundException")
    void shouldFailForUnknownDispute() {
        when(disputeRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startReview(404L, "FIN-01"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.reviewDispute(404L, true, "OK", "FIN-01"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Full lifecycle writes one audit entry per transition")
    void shouldWriteAuditEntryPerTransition() {
        stubRaisePath(null);
        FolioDispute raised = service.raiseDispute(7L, 100L, "Tính nhầm");

        when(disputeRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(raised));
        service.startReview(55L, "FIN-01");
        service.reviewDispute(55L, false, "Hợp lệ", "FIN-01");

        ArgumentCaptor<FolioDisputeAuditEntry> audit = ArgumentCaptor.forClass(FolioDisputeAuditEntry.class);
        verify(auditRepository, atLeastOnce()).save(audit.capture());
        List<String> actions = audit.getAllValues().stream().map(FolioDisputeAuditEntry::getAction).toList();
        assertThat(actions).containsExactly("RAISED", "REVIEW_STARTED", "REJECTED");
    }
}

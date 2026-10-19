package com.cruise.pos.service;

import com.cruise.pos.dto.CreateDisputeRequest;
import com.cruise.pos.dto.DisputeCaseDto;
import com.cruise.pos.dto.ResolveDisputeRequest;
import com.cruise.pos.entity.DisputeCase;
import com.cruise.pos.entity.GuestFolioLedger;
import com.cruise.pos.enums.DisputeReason;
import com.cruise.pos.enums.DisputeStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.DisputeCaseRepository;
import com.cruise.pos.repository.DisputeHistoryRepository;
import com.cruise.pos.repository.GuestFolioLedgerRepository;
import com.cruise.pos.repository.ReconciliationAuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock
    private DisputeCaseRepository disputeRepository;
    @Mock
    private DisputeHistoryRepository historyRepository;
    @Mock
    private GuestFolioLedgerRepository folioRepository;
    @Mock
    private ReconciliationAuditLogRepository auditLogRepository;

    @InjectMocks
    private DisputeService disputeService;

    @Test
    @DisplayName("Tạo khiếu nại thành công khi thông tin hợp lệ")
    void testCreateDispute_Success() {
        CreateDisputeRequest req = CreateDisputeRequest.builder()
                .transactionId("TX-2001")
                .folioId("FOLIO-202")
                .cabinNumber("CABIN-202")
                .passengerName("John Doe")
                .reason(DisputeReason.DUPLICATE_BILLING)
                .disputeAmount(new BigDecimal("50.00"))
                .passengerNote("Bị tính tiền 2 lần cho cùng 1 đơn đồ uống")
                .build();

        GuestFolioLedger originalLedger = GuestFolioLedger.builder()
                .transactionId("TX-2001")
                .amount(new BigDecimal("50.00"))
                .build();

        when(disputeRepository.existsByTransactionIdAndStatusIn(anyString(), anyList())).thenReturn(false);
        when(folioRepository.findByTransactionId("TX-2001")).thenReturn(Optional.of(originalLedger));
        when(disputeRepository.save(any(DisputeCase.class))).thenAnswer(inv -> {
            DisputeCase d = inv.getArgument(0);
            d.setId(100L);
            return d;
        });

        DisputeCaseDto result = disputeService.createDispute(req);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DisputeStatus.SUBMITTED);
        assertThat(result.getDisputeAmount()).isEqualByComparingTo("50.00");
        verify(historyRepository, times(1)).save(any());
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Từ chối tạo khiếu nại nếu giao dịch đã có khiếu nại đang xử lý")
    void testCreateDispute_DuplicateActiveDispute() {
        CreateDisputeRequest req = CreateDisputeRequest.builder()
                .transactionId("TX-EXISTING")
                .disputeAmount(new BigDecimal("30.00"))
                .build();

        when(disputeRepository.existsByTransactionIdAndStatusIn(eq("TX-EXISTING"), anyList())).thenReturn(true);

        assertThatThrownBy(() -> disputeService.createDispute(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("đang có khiếu nại đang xử lý");
    }

    @Test
    @DisplayName("Từ chối tạo khiếu nại nếu số tiền khiếu nại vượt quá số tiền giao dịch gốc")
    void testCreateDispute_AmountExceedsOriginal() {
        CreateDisputeRequest req = CreateDisputeRequest.builder()
                .transactionId("TX-ORIGINAL")
                .disputeAmount(new BigDecimal("150.00"))
                .build();

        GuestFolioLedger ledger = GuestFolioLedger.builder()
                .transactionId("TX-ORIGINAL")
                .amount(new BigDecimal("100.00"))
                .build();

        when(disputeRepository.existsByTransactionIdAndStatusIn(anyString(), anyList())).thenReturn(false);
        when(folioRepository.findByTransactionId("TX-ORIGINAL")).thenReturn(Optional.of(ledger));

        assertThatThrownBy(() -> disputeService.createDispute(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không được vượt quá số tiền giao dịch gốc");
    }

    @Test
    @DisplayName("Cập nhật trạng thái sang UNDER_INVESTIGATION thành công")
    void testUpdateStatus_ToUnderInvestigation() {
        DisputeCase dispute = DisputeCase.builder()
                .status(DisputeStatus.SUBMITTED)
                .disputeNumber("DISP-123")
                .build();
        dispute.setId(1L);

        when(disputeRepository.findById(1L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(DisputeCase.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeCaseDto result = disputeService.updateStatus(1L, DisputeStatus.UNDER_INVESTIGATION, "INVESTIGATOR_01", "Bắt đầu kiểm tra dữ liệu");

        assertThat(result.getStatus()).isEqualTo(DisputeStatus.UNDER_INVESTIGATION);
        assertThat(result.getReviewerId()).isEqualTo("INVESTIGATOR_01");
    }

    @Test
    @DisplayName("Giải quyết khiếu nại: Chấp thuận hoàn tiền và tạo bút toán bù trừ an toàn")
    void testResolveDispute_ApproveAndRefund() {
        DisputeCase dispute = DisputeCase.builder()
                .disputeNumber("DISP-456")
                .transactionId("TX-456")
                .folioId("FOLIO-456")
                .cabinNumber("CABIN-505")
                .passengerName("Bob")
                .disputeAmount(new BigDecimal("80.00"))
                .status(DisputeStatus.UNDER_INVESTIGATION)
                .build();
        dispute.setId(2L);

        when(disputeRepository.findById(2L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(DisputeCase.class))).thenAnswer(inv -> inv.getArgument(0));

        ResolveDisputeRequest req = ResolveDisputeRequest.builder()
                .decisionStatus(DisputeStatus.APPROVED_ADJUSTED)
                .approvedRefundAmount(new BigDecimal("80.00"))
                .reviewerId("PURSER_LEAD")
                .reviewerNote("Xác nhận nhân viên tính nhầm đồ uống, hoàn 100%")
                .build();

        DisputeCaseDto result = disputeService.resolveDispute(2L, req);

        assertThat(result.getStatus()).isEqualTo(DisputeStatus.APPROVED_ADJUSTED);
        assertThat(result.getAdjustmentTransactionId()).isNotNull();
        assertThat(result.getRefundAmount()).isEqualByComparingTo("80.00");
        verify(folioRepository, times(1)).save(argThat(folio ->
                folio.getIsReversal() &&
                        folio.getAmount().compareTo(new BigDecimal("-80.00")) == 0 &&
                        folio.getLinkedDisputeId().equals(2L)
        ));
    }

    @Test
    @DisplayName("Giải quyết khiếu nại: Từ chối khiếu nại không hợp lệ")
    void testResolveDispute_Reject() {
        DisputeCase dispute = DisputeCase.builder()
                .disputeNumber("DISP-789")
                .disputeAmount(new BigDecimal("50.00"))
                .status(DisputeStatus.UNDER_INVESTIGATION)
                .build();
        dispute.setId(3L);

        when(disputeRepository.findById(3L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(DisputeCase.class))).thenAnswer(inv -> inv.getArgument(0));

        ResolveDisputeRequest req = ResolveDisputeRequest.builder()
                .decisionStatus(DisputeStatus.REJECTED)
                .reviewerId("PURSER_LEAD")
                .reviewerNote("Khách hàng đã ký hóa đơn giấy tại quầy spa")
                .build();

        DisputeCaseDto result = disputeService.resolveDispute(3L, req);

        assertThat(result.getStatus()).isEqualTo(DisputeStatus.REJECTED);
        assertThat(result.getAdjustmentTransactionId()).isNull();
        verify(folioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Bảo vệ Anti-Double-Refund: Không cho phép hoàn tiền 2 lần")
    void testResolveDispute_AntiDoubleRefund() {
        DisputeCase dispute = DisputeCase.builder()
                .status(DisputeStatus.UNDER_INVESTIGATION)
                .disputeAmount(new BigDecimal("50.00"))
                .adjustmentTransactionId("ADJ-ALREADY-EXISTS")
                .build();
        dispute.setId(4L);

        when(disputeRepository.findById(4L)).thenReturn(Optional.of(dispute));

        ResolveDisputeRequest req = ResolveDisputeRequest.builder()
                .decisionStatus(DisputeStatus.APPROVED_ADJUSTED)
                .approvedRefundAmount(new BigDecimal("50.00"))
                .build();

        assertThatThrownBy(() -> disputeService.resolveDispute(4L, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("đã phát sinh bút toán hoàn tiền trước đó");
    }
}

package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.*;
import com.cruise.pos.exception.*;
import com.cruise.pos.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PosTransactionService Unit Tests")
class PosTransactionServiceTest {

    @Mock
    private PosTransactionRepository transactionRepository;

    @Mock
    private PosShiftRepository shiftRepository;

    @Mock
    private PosIdempotencyRepository idempotencyRepository;

    @Mock
    private PosAuditLogRepository auditLogRepository;

    @Mock
    private PosFolioService folioService;

    @Mock
    private PosShiftService shiftService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Spy
    private PosManagerAuthService managerAuthService = new PosManagerAuthService();

    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @InjectMocks
    private PosTransactionService transactionService;

    private PosShift mockShift;
    private CreateTransactionRequest chargeRequest;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(i -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = i.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().when(idempotencyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(idempotencyRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        mockShift = PosShift.builder()
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-01")
                .status(ShiftStatus.OPEN)
                .totalSales(BigDecimal.ZERO)
                .netRevenue(BigDecimal.ZERO)
                .build();
        mockShift.setId(1L);

        chargeRequest = CreateTransactionRequest.builder()
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-01")
                .cabinNumber("CABIN-7012")
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .tax(BigDecimal.valueOf(5.00))
                .gratuity(BigDecimal.valueOf(10.00))
                .discount(BigDecimal.valueOf(2.00))
                .items(List.of(
                        TransactionItemRequest.builder()
                                .itemCode("BEV-001")
                                .itemName("Cocktail Mojito")
                                .category("BEVERAGE")
                                .quantity(2)
                                .unitPrice(BigDecimal.valueOf(15.00)) // 30.00
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("processChargeTransaction calculates totals, charges folio, and creates transaction")
    void testProcessChargeTransactionSuccess() {
        when(idempotencyRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(shiftService.openShift(any(), any(), any())).thenReturn(mockShift);
        when(transactionRepository.save(any(PosTransaction.class))).thenAnswer(i -> {
            PosTransaction tx = i.getArgument(0);
            tx.setId(10L);
            return tx;
        });

        TransactionResponse response = transactionService.processChargeTransaction("IDEM-001", chargeRequest);

        assertThat(response).isNotNull();
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(30.00));
        // total = 30 + 5 (tax) + 10 (gratuity) - 2 (discount) = 43.00
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(43.00));
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        verify(folioService).chargeFolio("CABIN-7012", BigDecimal.valueOf(43.00));
        verify(auditLogRepository).save(any());
        verify(idempotencyRepository).save(any());
    }

    @Test
    @DisplayName("processChargeTransaction returns cached response when idempotency key completed")
    void testProcessChargeIdempotencyCached() throws Exception {
        TransactionResponse cached = TransactionResponse.builder()
                .id(99L)
                .transactionUuid("UUID-CACHED")
                .totalAmount(BigDecimal.valueOf(50.00))
                .build();
        PosIdempotencyRecord record = PosIdempotencyRecord.builder()
                .idempotencyKey("IDEM-CACHED")
                .status(IdempotencyStatus.COMPLETED)
                .responseBody(objectMapper.writeValueAsString(cached))
                .build();

        when(idempotencyRepository.findByIdempotencyKey("IDEM-CACHED")).thenReturn(Optional.of(record));

        TransactionResponse result = transactionService.processChargeTransaction("IDEM-CACHED", chargeRequest);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getTransactionUuid()).isEqualTo("UUID-CACHED");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("processChargeTransaction throws 409 conflict when idempotency key is in progress")
    void testProcessChargeIdempotencyInProgress() {
        PosIdempotencyRecord record = PosIdempotencyRecord.builder()
                .idempotencyKey("IDEM-LOCKED")
                .status(IdempotencyStatus.IN_PROGRESS)
                .build();

        when(idempotencyRepository.findByIdempotencyKey("IDEM-LOCKED")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> transactionService.processChargeTransaction("IDEM-LOCKED", chargeRequest))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("đang được xử lý song song");
    }

    @Test
    @DisplayName("voidTransaction voids same-shift transaction and restores folio")
    void testVoidTransactionSuccess() {
        PosTransaction tx = PosTransaction.builder()
                .status(TransactionStatus.COMPLETED)
                .settled(false)
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .cabinNumber("CABIN-7012")
                .totalAmount(BigDecimal.valueOf(43.00))
                .shiftId(1L)
                .build();
        tx.setId(10L);

        when(transactionRepository.findByIdWithLock(10L)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(PosTransaction.class))).thenAnswer(i -> i.getArgument(0));

        VoidTransactionRequest voidReq = VoidTransactionRequest.builder()
                .performedBy("CASHIER-01")
                .voidReason("Khách hủy món tại quầy")
                .build();

        TransactionResponse response = transactionService.voidTransaction(10L, voidReq);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.VOIDED);
        assertThat(response.getVoidReason()).isEqualTo("Khách hủy món tại quầy");
        verify(folioService).refundFolio("CABIN-7012", BigDecimal.valueOf(43.00));
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("voidTransaction rejects when transaction already settled")
    void testVoidTransactionAlreadySettled() {
        PosTransaction tx = PosTransaction.builder()
                .status(TransactionStatus.COMPLETED)
                .settled(true) // settled by Z-report
                .totalAmount(BigDecimal.valueOf(43.00))
                .build();
        tx.setId(10L);

        when(transactionRepository.findByIdWithLock(10L)).thenReturn(Optional.of(tx));

        VoidTransactionRequest voidReq = VoidTransactionRequest.builder()
                .performedBy("CASHIER-01")
                .voidReason("Khách hủy")
                .build();

        assertThatThrownBy(() -> transactionService.voidTransaction(10L, voidReq))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("đã được chốt quyết toán ca (Z-Report)");
    }

    @Test
    @DisplayName("refundTransaction requires valid manager PIN and processes partial/full refund")
    void testRefundTransactionSuccess() {
        PosTransaction tx = PosTransaction.builder()
                .status(TransactionStatus.COMPLETED)
                .settled(true)
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .cabinNumber("CABIN-7012")
                .totalAmount(BigDecimal.valueOf(100.00))
                .refundedAmount(BigDecimal.ZERO)
                .shiftId(1L)
                .build();
        tx.setId(20L);

        when(transactionRepository.findByIdWithLock(20L)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(PosTransaction.class))).thenAnswer(i -> i.getArgument(0));

        RefundTransactionRequest refundReq = RefundTransactionRequest.builder()
                .refundAmount(BigDecimal.valueOf(40.00))
                .refundReason("Khách đổi ý trả lại món")
                .performedBy("CASHIER-01")
                .managerPin("8888")
                .managerId("MGR-01")
                .build();

        TransactionResponse response = transactionService.refundTransaction(20L, refundReq);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PARTIALLY_REFUNDED);
        assertThat(response.getRefundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(40.00));
        assertThat(response.getRemainingRefundableAmount()).isEqualByComparingTo(BigDecimal.valueOf(60.00));

        verify(folioService).refundFolio("CABIN-7012", BigDecimal.valueOf(40.00));
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("refundTransaction rejects when manager PIN is missing or invalid")
    void testRefundInvalidManagerPin() {
        PosTransaction tx = PosTransaction.builder()
                .status(TransactionStatus.COMPLETED)
                .totalAmount(BigDecimal.valueOf(100.00))
                .refundedAmount(BigDecimal.ZERO)
                .build();
        tx.setId(20L);

        when(transactionRepository.findByIdWithLock(20L)).thenReturn(Optional.of(tx));

        RefundTransactionRequest refundReq = RefundTransactionRequest.builder()
                .refundAmount(BigDecimal.valueOf(40.00))
                .refundReason("Khách trả hàng")
                .performedBy("CASHIER-01")
                .managerPin("12") // invalid
                .build();

        assertThatThrownBy(() -> transactionService.refundTransaction(20L, refundReq))
                .isInstanceOf(UnauthorizedManagerOverrideException.class)
                .hasMessageContaining("Mã PIN Quản lý không hợp lệ");
    }

    @Test
    @DisplayName("refundTransaction rejects when refund amount exceeds original amount")
    void testRefundAmountExceedsRemaining() {
        PosTransaction tx = PosTransaction.builder()
                .status(TransactionStatus.COMPLETED)
                .totalAmount(BigDecimal.valueOf(50.00))
                .refundedAmount(BigDecimal.valueOf(30.00)) // remaining 20
                .build();
        tx.setId(20L);

        when(transactionRepository.findByIdWithLock(20L)).thenReturn(Optional.of(tx));

        RefundTransactionRequest refundReq = RefundTransactionRequest.builder()
                .refundAmount(BigDecimal.valueOf(25.00))
                .refundReason("Hoàn tiền")
                .performedBy("CASHIER-01")
                .managerId("MGR-01")
                .managerPin("8888")
                .build();

        assertThatThrownBy(() -> transactionService.refundTransaction(20L, refundReq))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vượt quá số dư còn lại");
    }

    @Test
    @DisplayName("processBatchSync processes offline batch and detects duplicates")
    void testProcessBatchSync() {
        BatchSyncRequest batch = BatchSyncRequest.builder()
                .deviceId("POS-HANDHELD-01")
                .transactions(List.of(
                        CreateTransactionRequest.builder()
                                .transactionUuid("TX-OFFLINE-01")
                                .stationId("BAR-DECK-9")
                                .cashierId("CASHIER-01")
                                .paymentMethod(PaymentMethod.CASH)
                                .items(List.of(
                                        TransactionItemRequest.builder()
                                                .itemCode("ITEM-1")
                                                .itemName("Water")
                                                .category("BEVERAGE")
                                                .quantity(1)
                                                .unitPrice(BigDecimal.valueOf(3.00))
                                                .build()
                                ))
                                .build()
                ))
                .build();

        when(transactionRepository.findByTransactionUuid("TX-OFFLINE-01")).thenReturn(Optional.empty());
        when(shiftService.openShift(any(), any(), any())).thenReturn(mockShift);
        when(transactionRepository.save(any())).thenAnswer(i -> {
            PosTransaction t = i.getArgument(0);
            t.setId(100L);
            return t;
        });

        BatchSyncResponse res = transactionService.processBatchSync(batch);

        assertThat(res.getTotalReceived()).isEqualTo(1);
        assertThat(res.getSuccessfullyProcessed()).isEqualTo(1);
        assertThat(res.getDuplicateCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("processBatchSync counts missing or blank UUID as failure")
    void testProcessBatchSyncMissingUuid() {
        BatchSyncRequest batch = BatchSyncRequest.builder()
                .deviceId("POS-HANDHELD-01")
                .transactions(List.of(
                        CreateTransactionRequest.builder()
                                .transactionUuid("") // blank UUID
                                .stationId("BAR-DECK-9")
                                .cashierId("CASHIER-01")
                                .build()
                ))
                .build();

        BatchSyncResponse res = transactionService.processBatchSync(batch);

        assertThat(res.getTotalReceived()).isEqualTo(1);
        assertThat(res.getSuccessfullyProcessed()).isEqualTo(0);
        assertThat(res.getFailedCount()).isEqualTo(1);
        assertThat(res.getFailedReasons().get(0)).contains("UUID null");
    }

    @Test
    @DisplayName("processChargeTransaction rejects missing supplied shift ID")
    void testChargeWithSuppliedShiftIdMissing() {
        chargeRequest.setShiftId(999L);
        when(shiftRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.processChargeTransaction("IDEM-MISSING", chargeRequest))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Không tìm thấy ca làm việc ID: 999");
    }

    @Test
    @DisplayName("processChargeTransaction rejects supplied shift ID when shift is CLOSED")
    void testChargeWithSuppliedShiftIdClosed() {
        chargeRequest.setShiftId(1L);
        mockShift.setStatus(ShiftStatus.CLOSED);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(mockShift));

        assertThatThrownBy(() -> transactionService.processChargeTransaction("IDEM-CLOSED", chargeRequest))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ca làm việc không còn mở");
    }

    @Test
    @DisplayName("processChargeTransaction rejects supplied shift ID belonging to different station")
    void testChargeWithSuppliedShiftIdDifferentStation() {
        chargeRequest.setShiftId(1L);
        mockShift.setStatus(ShiftStatus.OPEN);
        mockShift.setStationId("RESTAURANT-DECK-5");
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(mockShift));

        assertThatThrownBy(() -> transactionService.processChargeTransaction("IDEM-DIFF", chargeRequest))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không thuộc trạm này");
    }

    @Test
    @DisplayName("processChargeTransaction throws IdempotencyConflictException when request is in progress")
    void testChargeIdempotencyInProgress() {
        PosIdempotencyRecord inProgressRecord = PosIdempotencyRecord.builder()
                .idempotencyKey("IDEM-PROGRESS")
                .status(IdempotencyStatus.IN_PROGRESS)
                .build();
        when(idempotencyRepository.findByIdempotencyKey("IDEM-PROGRESS")).thenReturn(Optional.of(inProgressRecord));

        assertThatThrownBy(() -> transactionService.processChargeTransaction("IDEM-PROGRESS", chargeRequest))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("Giao dịch đang được xử lý song song");
    }
}

package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.PosShift;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.ShiftStatus;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Synchronization & Idempotency Rules Unit Tests (KAN-258)")
class PosSynchronizationIdempotencyTest {

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

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Mock
    private PosManagerAuthService managerAuthService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private PosTransactionService transactionService;

    private PosShift mockShift;

    @BeforeEach
    void setUp() {
        mockShift = PosShift.builder()
                .stationId("POS-DECK-9")
                .cashierId("CASHIER-01")
                .status(ShiftStatus.OPEN)
                .totalSales(BigDecimal.ZERO)
                .totalRefunds(BigDecimal.ZERO)
                .netRevenue(BigDecimal.ZERO)
                .transactionCount(0)
                .build();
        mockShift.setId(1L);

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    @DisplayName("Batch sync skips duplicate transaction UUIDs and tracks them in duplicateUuids")
    void testBatchSyncSkipsDuplicates() {
        String existingUuid = "TX-OFFLINE-DUPLICATE";
        String newUuid = "TX-OFFLINE-NEW";

        CreateTransactionRequest txDup = CreateTransactionRequest.builder()
                .transactionUuid(existingUuid)
                .stationId("POS-DECK-9")
                .cashierId("CASHIER-01")
                .paymentMethod(PaymentMethod.CASH)
                .items(List.of(
                        TransactionItemRequest.builder()
                                .itemCode("BEV-01")
                                .itemName("Mineral Water")
                                .category("BEVERAGE")
                                .quantity(2)
                                .unitPrice(BigDecimal.valueOf(3.50))
                                .build()
                ))
                .build();

        CreateTransactionRequest txNew = CreateTransactionRequest.builder()
                .transactionUuid(newUuid)
                .stationId("POS-DECK-9")
                .cashierId("CASHIER-01")
                .paymentMethod(PaymentMethod.CASH)
                .items(List.of(
                        TransactionItemRequest.builder()
                                .itemCode("BEV-02")
                                .itemName("Soda Can")
                                .category("BEVERAGE")
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(4.00))
                                .build()
                ))
                .build();

        BatchSyncRequest batch = BatchSyncRequest.builder()
                .deviceId("POS-HANDHELD-DECK-9")
                .transactions(List.of(txDup, txNew))
                .build();

        PosTransaction existingTx = PosTransaction.builder()
                .transactionUuid(existingUuid)
                .status(TransactionStatus.COMPLETED)
                .build();
        existingTx.setId(88L);

        when(transactionRepository.findByTransactionUuid(existingUuid)).thenReturn(Optional.of(existingTx));
        when(transactionRepository.findByTransactionUuid(newUuid)).thenReturn(Optional.empty());
        when(shiftService.openShift(any(), any(), any())).thenReturn(mockShift);
        when(transactionRepository.save(any(PosTransaction.class))).thenAnswer(inv -> {
            PosTransaction t = inv.getArgument(0);
            t.setId(99L);
            return t;
        });

        BatchSyncResponse response = transactionService.processBatchSync(batch);

        assertThat(response.getTotalReceived()).isEqualTo(2);
        assertThat(response.getSuccessfullyProcessed()).isEqualTo(1);
        assertThat(response.getDuplicateCount()).isEqualTo(1);
        assertThat(response.getFailedCount()).isEqualTo(0);
        assertThat(response.getProcessedUuids()).containsExactly(newUuid);
        assertThat(response.getDuplicateUuids()).containsExactly(existingUuid);
    }

    @Test
    @DisplayName("Batch sync handles empty batch payload gracefully")
    void testBatchSyncEmptyPayload() {
        BatchSyncRequest batch = BatchSyncRequest.builder()
                .deviceId("POS-HANDHELD-DECK-9")
                .transactions(new ArrayList<>())
                .build();

        BatchSyncResponse response = transactionService.processBatchSync(batch);

        assertThat(response.getTotalReceived()).isEqualTo(0);
        assertThat(response.getSuccessfullyProcessed()).isEqualTo(0);
        assertThat(response.getDuplicateCount()).isEqualTo(0);
        assertThat(response.getFailedCount()).isEqualTo(0);
        assertThat(response.getProcessedUuids()).isEmpty();
    }

    @Test
    @DisplayName("Batch sync captures transaction errors without rolling back entire batch")
    void testBatchSyncPartialFailure() {
        String validUuid = "TX-VALID-UUID";
        String failUuid = "TX-FAIL-UUID";

        CreateTransactionRequest txValid = CreateTransactionRequest.builder()
                .transactionUuid(validUuid)
                .stationId("POS-DECK-9")
                .cashierId("CASHIER-01")
                .paymentMethod(PaymentMethod.CASH)
                .items(List.of(
                        TransactionItemRequest.builder()
                                .itemCode("BEV-01")
                                .itemName("Mineral Water")
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(3.50))
                                .build()
                ))
                .build();

        CreateTransactionRequest txFail = CreateTransactionRequest.builder()
                .transactionUuid(failUuid)
                .stationId("POS-DECK-9")
                .cashierId("CASHIER-01")
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .cabinNumber("CABIN-NONEXISTENT")
                .items(List.of(
                        TransactionItemRequest.builder()
                                .itemCode("BEV-01")
                                .itemName("Mineral Water")
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(3.50))
                                .build()
                ))
                .build();

        BatchSyncRequest batch = BatchSyncRequest.builder()
                .deviceId("POS-HANDHELD-DECK-9")
                .transactions(List.of(txValid, txFail))
                .build();

        when(transactionRepository.findByTransactionUuid(validUuid)).thenReturn(Optional.empty());
        when(transactionRepository.findByTransactionUuid(failUuid)).thenReturn(Optional.empty());
        when(shiftService.openShift(any(), any(), any())).thenReturn(mockShift);
        when(transactionRepository.save(any(PosTransaction.class))).thenAnswer(inv -> {
            PosTransaction t = inv.getArgument(0);
            t.setId(101L);
            return t;
        });

        doThrow(new RuntimeException("Cabin folio not found: CABIN-NONEXISTENT"))
                .when(folioService).chargeFolio(eq("CABIN-NONEXISTENT"), any(BigDecimal.class));

        BatchSyncResponse response = transactionService.processBatchSync(batch);

        assertThat(response.getTotalReceived()).isEqualTo(2);
        assertThat(response.getSuccessfullyProcessed()).isEqualTo(1);
        assertThat(response.getFailedCount()).isEqualTo(1);
        assertThat(response.getDuplicateCount()).isEqualTo(0);
        assertThat(response.getProcessedUuids()).containsExactly(validUuid);
        assertThat(response.getFailedReasons()).hasSize(1);
        assertThat(response.getFailedReasons().get(0)).contains("Cabin folio not found");
    }

    @Test
    @DisplayName("Audit log is properly persisted when offline transaction is successfully synced")
    void testAuditLogRecordedOnSync() {
        String uuid = "TX-SYNC-AUDIT";

        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionUuid(uuid)
                .stationId("POS-DECK-9")
                .cashierId("CASHIER-AUDIT")
                .paymentMethod(PaymentMethod.CASH)
                .items(List.of(
                        TransactionItemRequest.builder()
                                .itemCode("SNK-01")
                                .itemName("Maritime Snack")
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(10.00))
                                .build()
                ))
                .build();

        BatchSyncRequest batch = BatchSyncRequest.builder()
                .deviceId("POS-DEVICE-AUDIT")
                .transactions(List.of(request))
                .build();

        when(transactionRepository.findByTransactionUuid(uuid)).thenReturn(Optional.empty());
        when(shiftService.openShift(any(), any(), any())).thenReturn(mockShift);
        when(transactionRepository.save(any(PosTransaction.class))).thenAnswer(inv -> {
            PosTransaction t = inv.getArgument(0);
            t.setId(202L);
            return t;
        });

        BatchSyncResponse res = transactionService.processBatchSync(batch);

        assertThat(res.getSuccessfullyProcessed()).isEqualTo(1);
        verify(auditLogRepository, times(1)).save(argThat(log ->
                log.getTransactionId().equals(202L) &&
                log.getAction().equals("CHARGE") &&
                log.getPerformedBy().equals("CASHIER-AUDIT")
        ));
    }
}

package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.dto.OfflineSyncDtos.*;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.*;
import com.cruise.pos.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:offline_replay;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000",
    "spring.datasource.hikari.maximum-pool-size=16","spring.datasource.hikari.connection-timeout=60000"})
class OfflineSyncReplayTest {
    @Autowired OfflineSyncProcessingService service;
    @Autowired CabinFolioAccountRepository folios;
    @Autowired PosTerminalRepository terminals;
    @Autowired PosTransactionRepository transactions;
    @Autowired IdempotencyRecordRepository keys;
    @Autowired SyncConflictLogRepository conflicts;
    @Autowired SyncBatchRepository batches;
    @Autowired PosAuditLogRepository audit;
    private String terminalId;
    private String stationId;
    private String cabin;
    @BeforeEach void setup() {
        String suffix=UUID.randomUUID().toString().substring(0,12);
        terminalId="TERM-"+suffix; stationId="POS-"+suffix; cabin="CAB-"+suffix;
        PosTerminal terminal=PosTerminal.builder().deviceId(terminalId).deviceName("Test POS").stationId(stationId).status(PosTerminalStatus.ACTIVE).build();
        terminals.saveAndFlush(terminal);
        folios.saveAndFlush(CabinFolioAccount.builder().cabinNumber(cabin).primaryGuestName("Test Guest")
            .creditLimit(new BigDecimal("1000.00")).currentBalance(BigDecimal.ZERO).status(FolioStatus.ACTIVE).build());
    }
    private OfflineTransactionDto item() {
        UUID random=UUID.randomUUID();
        UUID key=new UUID((System.currentTimeMillis()<<16)|0x7000L|(random.getMostSignificantBits()&0xfffL),random.getLeastSignificantBits());
        CreateTransactionRequest request=CreateTransactionRequest.builder().stationId(stationId).cashierId("cashier").cabinNumber(cabin)
            .paymentMethod(PaymentMethod.CABIN_FOLIO).items(List.of(TransactionItemRequest.builder().itemCode("WATER").itemName("Water")
                .category("BEVERAGE").quantity(1).unitPrice(new BigDecimal("10.00")).build())).build();
        return new OfflineTransactionDto(key,"OFFLINE-"+UUID.randomUUID(),request);
    }
    private BigDecimal balance() { return folios.findByCabinNumber(cabin).orElseThrow().getCurrentBalance(); }
    @Test void tenChargesSurviveLostResponseAndBatchReplayWithoutSecondDebit() {
        List<OfflineTransactionDto> items=new ArrayList<>(); for(int i=0;i<10;i++) items.add(item());
        BatchResult first=service.processBatch(terminalId,"batch-10",items);
        assertThat(first.successfulCount()).isEqualTo(10); assertThat(balance()).isEqualByComparingTo("100.00");
        BatchResult replay=service.processBatch(terminalId,"batch-10",items);
        assertThat(replay.duplicateCount()).isEqualTo(10); assertThat(balance()).isEqualByComparingTo("100.00");
        BatchResult repacked=service.processBatch(terminalId,"batch-repacked",items);
        assertThat(repacked.duplicateCount()).isEqualTo(10); assertThat(balance()).isEqualByComparingTo("100.00");
        assertThat(repacked.serverFolios()).singleElement().extracting(FolioState::currentBalance).isEqualTo(new BigDecimal("100.00"));
        for(OfflineTransactionDto item:items) assertThat(transactions.findByTransactionUuid(item.transactionReference())).isPresent();
        assertThat(service.getStatus(first.batchId(),terminalId).syncStatus()).isEqualTo("COMPLETED");
    }
    @Test void simultaneousReplayIsSerializedAtRegisteredTerminal() throws Exception {
        OfflineTransactionDto item=item(); CountDownLatch gate=new CountDownLatch(1);
        try (ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<BatchResult>> requests=new ArrayList<>();
            for(int i=0;i<20;i++) requests.add(executor.submit(()->{gate.await();return service.processBatch(terminalId,"parallel",List.of(item));}));
            gate.countDown(); int success=0; int duplicate=0;
            for(Future<BatchResult> request:requests){BatchResult response=request.get(60,TimeUnit.SECONDS); success+=response.successfulCount();duplicate+=response.duplicateCount();}
            assertThat(success).isEqualTo(1);assertThat(duplicate).isEqualTo(19);
        }
        assertThat(balance()).isEqualByComparingTo("10.00");
    }
    @Test void settledCabinQueuesConflictAndFinanceApprovalPostsExactlyOnceWithoutReopeningFolio() {
        CabinFolioAccount folio=folios.findByCabinNumber(cabin).orElseThrow();folio.setStatus(FolioStatus.SETTLED);folios.saveAndFlush(folio);
        OfflineTransactionDto item=item();BatchResult result=service.processBatch(terminalId,"settled",List.of(item));
        assertThat(result.syncStatus()).isEqualTo("CONFLICT_DETECTED");assertThat(result.failedCount()).isEqualTo(1);
        Long id=result.results().getFirst().conflictId();assertThat(id).isNotNull();
        assertThat(balance()).isEqualByComparingTo("0.00");assertThat(transactions.findByTransactionUuid(item.transactionReference())).isEmpty();
        assertThat(service.processBatch(terminalId,"settled",List.of(item)).results().getFirst().conflictId()).isEqualTo(id);
        RecordResult resolved=service.resolve(id,new ResolveRequest(ResolutionAction.APPROVE,"Guest confirmed late offline purchase"),"finance");
        assertThat(resolved.status()).isEqualTo("SUCCESS");assertThat(balance()).isEqualByComparingTo("10.00");
        assertThat(folios.findByCabinNumber(cabin).orElseThrow().getStatus()).isEqualTo(FolioStatus.SETTLED);
        service.resolve(id,new ResolveRequest(ResolutionAction.APPROVE,"Repeated decision"),"finance");
        assertThat(balance()).isEqualByComparingTo("10.00");
        assertThat(service.processBatch(terminalId,"settled",List.of(item)).duplicateCount()).isEqualTo(1);
        assertThat(conflicts.findById(id).orElseThrow().getResolutionStatus()).isEqualTo("APPROVED");
    }
    @Test void rejectingClosedFolioLeavesBalanceUntouchedAndReturnsStableDecision() {
        CabinFolioAccount folio=folios.findByCabinNumber(cabin).orElseThrow();folio.setStatus(FolioStatus.CLOSED);folios.saveAndFlush(folio);
        OfflineTransactionDto item=item();BatchResult batch=service.processBatch(terminalId,"closed",List.of(item));
        Long id=batch.results().getFirst().conflictId();
        assertThat(service.resolve(id,new ResolveRequest(ResolutionAction.REJECT,"Purchase not authorized"),"finance").errorCode()).isEqualTo("MANUAL_REJECTION");
        assertThat(balance()).isEqualByComparingTo("0.00");assertThat(transactions.findByTransactionUuid(item.transactionReference())).isEmpty();
        assertThat(service.processBatch(terminalId,"closed-again",List.of(item)).results().getFirst().errorCode()).isEqualTo("MANUAL_REJECTION");
    }
    @Test void payloadMismatchRollsBackEntireNewBatchIncludingEarlierCharge() {
        OfflineTransactionDto existing=item();service.processBatch(terminalId,"original",List.of(existing));
        OfflineTransactionDto fresh=item();existing.transaction().getItems().getFirst().setUnitPrice(new BigDecimal("99.00"));
        assertThatThrownBy(()->service.processBatch(terminalId,"must-rollback",List.of(fresh,existing))).hasMessageContaining("different payload");
        assertThat(balance()).isEqualByComparingTo("10.00");assertThat(transactions.findByTransactionUuid(fresh.transactionReference())).isEmpty();
        assertThat(keys.findByIdempotencyKey(fresh.idempotencyKey().toString())).isEmpty();
        assertThat(batches.findByTerminalIdAndBatchNumber(terminalId,"must-rollback")).isEmpty();
    }
    @Test void reusedBatchIdWithChangedPayloadFailsAndUuidV4IsRejected() {
        OfflineTransactionDto original=item();service.processBatch(terminalId,"immutable",List.of(original));
        assertThatThrownBy(()->service.processBatch(terminalId,"immutable",List.of(item()))).hasMessageContaining("different payload");
        OfflineTransactionDto invalid=new OfflineTransactionDto(UUID.randomUUID(),"bad-key",item().transaction());
        assertThatThrownBy(()->service.processBatch(terminalId,"v4",List.of(invalid))).hasMessageContaining("UUIDv7");
        assertThat(balance()).isEqualByComparingTo("10.00");
    }
    @Test void failedBusinessRecordDoesNotDebitAndExpiryNeverPermitsDoublePosting() {
        CabinFolioAccount folio=folios.findByCabinNumber(cabin).orElseThrow();folio.setCreditLimit(new BigDecimal("5.00"));folios.saveAndFlush(folio);
        OfflineTransactionDto denied=item();BatchResult response=service.processBatch(terminalId,"credit",List.of(denied));
        assertThat(response.results().getFirst().errorCode()).isEqualTo("INSUFFICIENT_CREDIT");assertThat(balance()).isEqualByComparingTo("0.00");
        folio=folios.findByCabinNumber(cabin).orElseThrow();folio.setCreditLimit(new BigDecimal("1000.00"));folios.saveAndFlush(folio);
        OfflineTransactionDto allowed=item();service.processBatch(terminalId,"allowed",List.of(allowed));
        IdempotencyRecord key=keys.findByIdempotencyKey(allowed.idempotencyKey().toString()).orElseThrow();key.setExpiresAt(Instant.now().minusSeconds(1));keys.saveAndFlush(key);
        assertThat(service.processBatch(terminalId,"expired-replay",List.of(allowed)).duplicateCount()).isEqualTo(1);
        assertThat(balance()).isEqualByComparingTo("10.00");
    }
    @Test void heartbeatAndTerminalScopeAreValidated() {
        assertThat(service.heartbeat(terminalId,new HeartbeatRequest("1.2.3")).lastSeenAt()).isNotNull();
        assertThat(terminals.findByDeviceId(terminalId).orElseThrow().getFirmwareVersion()).isEqualTo("1.2.3");
        OfflineTransactionDto wrongStation=item();wrongStation.transaction().setStationId("OTHER");
        assertThatThrownBy(()->service.processBatch(terminalId,"wrong-station",List.of(wrongStation))).hasMessageContaining("does not match");
        PosTerminal terminal=terminals.findByDeviceId(terminalId).orElseThrow();terminal.setStatus(PosTerminalStatus.SUSPENDED);terminals.saveAndFlush(terminal);
        assertThatThrownBy(()->service.heartbeat(terminalId,new HeartbeatRequest(null))).hasMessageContaining("not active");
    }
}

package com.cruise.pos.service;

import com.cruise.pos.dto.SubFolioStatement;
import com.cruise.pos.entity.CabinFolio;
import com.cruise.pos.entity.FolioDispute;
import com.cruise.pos.entity.FolioDisputeAuditEntry;
import com.cruise.pos.entity.FolioSplitRule;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.enums.CabinFolioStatus;
import com.cruise.pos.enums.FolioDisputeStatus;
import com.cruise.pos.enums.FolioSplitType;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.TransactionType;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.FolioLimitExceededException;
import com.cruise.pos.repository.CabinFolioRepository;
import com.cruise.pos.repository.FolioDisputeAuditEntryRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KAN-718: kiểm thử tính toán tiền tệ của folio cabin và kiểm soát thấu chi.
 * Chạy trên H2 (profile test), gồm cả kịch bản quẹt thẻ POS đồng thời với khóa lạc quan.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Folio Billing Integration Tests (split, overdraft, dispute, concurrency)")
class FolioBillingServiceTest {

    @Autowired
    private FolioSettlementSplitter splitter;

    @Autowired
    private FolioChargeService chargeService;

    @Autowired
    private FolioManagementService managementService;

    @Autowired
    private TransactionDisputeService disputeService;

    @Autowired
    private CabinFolioRepository folioRepository;

    @Autowired
    private PosTransactionRepository transactionRepository;

    @Autowired
    private FolioDisputeAuditEntryRepository auditRepository;

    // ------------------------------------------------------------------ helpers

    private CabinFolio newFolio(String creditLimit, String balance) {
        return folioRepository.saveAndFlush(CabinFolio.builder()
                .cabinNumber("CAB-" + UUID.randomUUID().toString().substring(0, 8))
                .voyageId(1L)
                .creditLimit(new BigDecimal(creditLimit))
                .currentBalance(new BigDecimal(balance))
                .status(CabinFolioStatus.OPEN)
                .build());
    }

    private BigDecimal balanceOf(Long folioId) {
        return folioRepository.findById(folioId).orElseThrow().getCurrentBalance();
    }

    private PosTransaction sharedCharge(long id, String amount) {
        PosTransaction tx = PosTransaction.builder()
                .transactionUuid("SPLIT-" + id)
                .stationId("ROOM-SERVICE")
                .cashierId("CASHIER-1")
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .totalAmount(new BigDecimal(amount))
                .build();
        tx.setId(id);
        return tx;
    }

    private FolioSplitRule percentageRule(long passengerId, String percentage) {
        return FolioSplitRule.builder()
                .passengerId(passengerId)
                .splitType(FolioSplitType.PERCENTAGE)
                .splitValue(new BigDecimal(percentage))
                .build();
    }

    private BigDecimal sharedOf(List<SubFolioStatement> statements, long passengerId) {
        return statements.stream().filter(s -> s.passengerId() == passengerId)
                .findFirst().orElseThrow().sharedTotal();
    }

    // ------------------------------------------------------------------ chia hóa đơn 3 người

    @Test
    @DisplayName("Splits a cabin bill between 3 guests by 50% - 25% - 25%")
    void shouldSplitBillBetweenThreeGuestsByPercentage() {
        List<FolioSplitRule> rules = List.of(
                percentageRule(1, "50"), percentageRule(2, "25"), percentageRule(3, "25"));

        List<SubFolioStatement> exact = splitter.split(
                List.of(sharedCharge(1, "120.00"), sharedCharge(2, "80.00")), rules);
        assertThat(sharedOf(exact, 1)).isEqualByComparingTo("100.00");
        assertThat(sharedOf(exact, 2)).isEqualByComparingTo("50.00");
        assertThat(sharedOf(exact, 3)).isEqualByComparingTo("50.00");

        // Số lẻ cent: 200.01 không được mất hoặc thừa cent nào
        List<SubFolioStatement> withRemainder = splitter.split(List.of(sharedCharge(3, "200.01")), rules);
        BigDecimal total = withRemainder.stream().map(SubFolioStatement::grandTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("200.01");
        assertThat(sharedOf(withRemainder, 1)).isEqualByComparingTo("100.01");
        assertThat(sharedOf(withRemainder, 2)).isEqualByComparingTo("50.00");
        assertThat(sharedOf(withRemainder, 3)).isEqualByComparingTo("50.00");
    }

    // ------------------------------------------------------------------ chặn thấu chi

    @Test
    @DisplayName("Overdraft is blocked above the cabin credit limit, unless a hold or manager approval covers it")
    void shouldBlockSpendingAboveCabinLimit() {
        CabinFolio folio = newFolio("100.00", "0.00");

        assertThat(chargeService.charge(folio.getId(), new BigDecimal("60.00")).getCurrentBalance())
                .isEqualByComparingTo("60.00");

        assertThatThrownBy(() -> chargeService.charge(folio.getId(), new BigDecimal("50.00")))
                .isInstanceOfSatisfying(FolioLimitExceededException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("FOLIO_LIMIT_EXCEEDED"));
        assertThat(balanceOf(folio.getId())).isEqualByComparingTo("60.00");

        // Khoản tạm giữ 100.00 nâng mức chi tiêu tối đa lên 200.00
        managementService.createPreAuthorization(folio.getId(), 7L, new BigDecimal("100.00"));
        assertThat(chargeService.charge(folio.getId(), new BigDecimal("50.00")).getCurrentBalance())
                .isEqualByComparingTo("110.00");

        // Vượt cả mức đó thì vẫn bị chặn, trừ khi quản lý cho phép thấu chi
        assertThatThrownBy(() -> chargeService.charge(folio.getId(), new BigDecimal("100.00")))
                .isInstanceOf(FolioLimitExceededException.class);
        assertThat(chargeService.charge(folio.getId(), new BigDecimal("100.00"), true).getCurrentBalance())
                .isEqualByComparingTo("210.00");
    }

    // ------------------------------------------------------------------ khiếu nại và hoàn tiền

    @Test
    @DisplayName("Approved dispute refunds the exact amount to the cabin folio with a full audit trail")
    void shouldRefundCabinBalanceWhenDisputeApproved() {
        CabinFolio folio = newFolio("500.00", "80.00");
        PosTransaction charge = transactionRepository.saveAndFlush(PosTransaction.builder()
                .transactionUuid("TX-" + UUID.randomUUID())
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-1")
                .passengerId(7L)
                .cabinNumber(folio.getCabinNumber())
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .totalAmount(new BigDecimal("80.00"))
                .build());

        FolioDispute dispute = disputeService.raiseDispute(7L, charge.getId(), "Bị tính nhầm món", new BigDecimal("30.00"));
        assertThat(dispute.getStatus()).isEqualTo(FolioDisputeStatus.PENDING);
        assertThat(balanceOf(folio.getId())).isEqualByComparingTo("80.00"); // chưa duyệt thì chưa hoàn

        disputeService.startReview(dispute.getId(), "FIN-01");
        FolioDispute approved = disputeService.reviewDispute(dispute.getId(), true, "Đã đối chiếu bill gốc", "FIN-01");

        assertThat(approved.getStatus()).isEqualTo(FolioDisputeStatus.APPROVED);
        assertThat(balanceOf(folio.getId())).isEqualByComparingTo("50.00");

        PosTransaction refund = transactionRepository.findById(approved.getRefundTransactionId()).orElseThrow();
        assertThat(refund.getType()).isEqualTo(TransactionType.REFUND_ADJUSTMENT);
        assertThat(refund.getTotalAmount()).isEqualByComparingTo("30.00");
        assertThat(refund.getOriginalTransactionId()).isEqualTo(charge.getId());

        List<FolioDisputeAuditEntry> trail = auditRepository.findByDispute_IdOrderByIdAsc(dispute.getId());
        assertThat(trail).extracting(FolioDisputeAuditEntry::getAction)
                .containsExactly("RAISED", "REVIEW_STARTED", "APPROVED");

        // Duyệt lần hai bị chặn bởi state machine, số dư không bị hoàn thêm
        assertThatThrownBy(() -> disputeService.reviewDispute(dispute.getId(), true, "Duyệt lại", "FIN-01"))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(balanceOf(folio.getId())).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Rejected dispute leaves the cabin balance unchanged")
    void shouldKeepBalanceWhenDisputeRejected() {
        CabinFolio folio = newFolio("500.00", "80.00");
        PosTransaction charge = transactionRepository.saveAndFlush(PosTransaction.builder()
                .transactionUuid("TX-" + UUID.randomUUID())
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-1")
                .passengerId(7L)
                .cabinNumber(folio.getCabinNumber())
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .totalAmount(new BigDecimal("80.00"))
                .build());

        FolioDispute dispute = disputeService.raiseDispute(7L, charge.getId(), "Tôi không dùng món này");
        disputeService.startReview(dispute.getId(), "FIN-01");
        FolioDispute rejected = disputeService.reviewDispute(dispute.getId(), false, "Bill gốc hợp lệ", "FIN-01");

        assertThat(rejected.getStatus()).isEqualTo(FolioDisputeStatus.REJECTED);
        assertThat(balanceOf(folio.getId())).isEqualByComparingTo("80.00");
        assertThat(rejected.getRefundTransactionId()).isNull();
    }

    // ------------------------------------------------------------------ quẹt thẻ đồng thời

    /** Chạy {@code threads} lần quẹt thẻ cùng lúc trên một cabin; trả về số lần thành công và lỗi vượt hạn mức. */
    private int[] runConcurrentCharges(Long folioId, int threads, BigDecimal amount, List<Throwable> unexpected)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    chargeService.charge(folioId, amount);
                    succeeded.incrementAndGet();
                } catch (FolioLimitExceededException ex) {
                    blocked.incrementAndGet();
                } catch (Throwable ex) {
                    unexpected.add(ex);
                }
            });
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        return new int[]{succeeded.get(), blocked.get()};
    }

    @Test
    @DisplayName("Concurrent POS swipes never exceed the cabin credit limit")
    void shouldNeverExceedLimitUnderConcurrentCharges() throws InterruptedException {
        CabinFolio folio = newFolio("100.00", "0.00");
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        int[] result = runConcurrentCharges(folio.getId(), 10, new BigDecimal("25.00"), unexpected);

        assertThat(unexpected).isEmpty();
        assertThat(result[0]).isEqualTo(4);  // 4 x 25.00 = 100.00
        assertThat(result[1]).isEqualTo(6);  // 6 lần còn lại bị chặn
        assertThat(balanceOf(folio.getId())).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Concurrent POS swipes lose no update thanks to optimistic locking with retry")
    void shouldLoseNoUpdateUnderConcurrentCharges() throws InterruptedException {
        CabinFolio folio = newFolio("1000.00", "0.00");
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        int[] result = runConcurrentCharges(folio.getId(), 20, new BigDecimal("10.00"), unexpected);

        assertThat(unexpected).isEmpty();
        assertThat(result[0]).isEqualTo(20);
        assertThat(result[1]).isZero();
        CabinFolio reloaded = folioRepository.findById(folio.getId()).orElseThrow();
        assertThat(reloaded.getCurrentBalance()).isEqualByComparingTo("200.00");
        assertThat(reloaded.getVersion()).isGreaterThanOrEqualTo(20L);
    }
}

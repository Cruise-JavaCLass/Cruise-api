package com.cruise.pos.service;

import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.exception.FolioAlreadySettledException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.FolioSettlementRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import com.cruise.pos.support.SettlementTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chứng minh tất toán nguyên tử: khoá bi quan trên folio + unique idempotency key
 * đảm bảo không bao giờ có hai bản ghi tất toán cho cùng một cabin.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Folio settlement concurrency")
class FolioSettlementConcurrencyTest {

    private static final Set<String> ROLES = Set.of("FINANCE_FRONT_DESK");

    @Autowired
    private FolioSettlementService settlementService;

    @Autowired
    private CabinFolioAccountRepository folioRepository;

    @Autowired
    private PosTransactionRepository transactionRepository;

    @Autowired
    private FolioSettlementRepository settlementRepository;

    @Test
    @DisplayName("Hai request đồng thời với key khác nhau: chỉ một thắng, một nhận 409")
    void concurrentSettlementsOnlyOneWins() throws Exception {
        String cabin = SettlementTestData.uniqueCabin("CABIN-RACE");
        CabinFolioAccount folio = SettlementTestData.newFolio(folioRepository, cabin, "Khách Race", new BigDecimal("50.00"));
        SettlementTestData.saveCharge(transactionRepository, folio.getCabinNumber(),
                new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "Đồ uống", "BEVERAGE", 1, new BigDecimal("50.00"),
                com.cruise.pos.enums.TransactionStatus.COMPLETED);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        List<UUID> keys = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID key = UUID.randomUUID();
            keys.add(key);
            executor.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    settlementService.settle(cabin, key.toString(), PaymentMethod.CASH,
                            new BigDecimal("50.00"), null, false, "cashier-race", ROLES);
                    success.incrementAndGet();
                } catch (FolioAlreadySettledException expected) {
                    // mong đợi ở thread thua
                } catch (Throwable unexpected) {
                    failures.add(unexpected);
                }
            });
        }

        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).as("không có lỗi bất ngờ: %s", failures).isEmpty();
        assertThat(success.get()).isEqualTo(1);
        assertThat(settlementRepository.countByCabinNumber(cabin)).isEqualTo(1);
    }

    @Test
    @DisplayName("Nhiều request đồng thời cùng một key trả cùng một settlement")
    void concurrentSettlementsSameKeyAreIdempotent() throws Exception {
        String cabin = SettlementTestData.uniqueCabin("CABIN-IDEM");
        SettlementTestData.newFolio(folioRepository, cabin, "Khách Idem", new BigDecimal("30.00"));
        SettlementTestData.saveCharge(transactionRepository, cabin,
                new BigDecimal("30.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "Spa", "SPA", 1, new BigDecimal("30.00"),
                com.cruise.pos.enums.TransactionStatus.COMPLETED);

        String key = UUID.randomUUID().toString();
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<String> uuids = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    SettleOutcome outcome = settlementService.settle(cabin, key, PaymentMethod.CASH,
                            new BigDecimal("30.00"), null, false, "cashier-idem", ROLES);
                    uuids.add(outcome.response().getSettlementUuid());
                } catch (Throwable unexpected) {
                    failures.add(unexpected);
                }
            });
        }

        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).as("không có lỗi bất ngờ: %s", failures).isEmpty();
        assertThat(uuids).hasSize(threads);
        assertThat(uuids.stream().distinct().count()).isEqualTo(1);
        assertThat(settlementRepository.countByCabinNumber(cabin)).isEqualTo(1);
    }
}

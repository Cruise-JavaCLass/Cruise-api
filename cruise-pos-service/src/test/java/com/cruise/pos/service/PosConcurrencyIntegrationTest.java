package com.cruise.pos.service;

import com.cruise.pos.dto.CreateTransactionRequest;
import com.cruise.pos.dto.TransactionItemRequest;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import com.cruise.pos.exception.InsufficientFolioBalanceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("POS Concurrency & Credit Limit Enforcement Integration Tests")
class PosConcurrencyIntegrationTest {

    @Autowired
    private PosTransactionService transactionService;

    @Autowired
    private CabinFolioAccountRepository folioRepository;

    @Autowired
    private PosTransactionRepository transactionRepository;

    @Test
    @DisplayName("Concurrent charges respect cabin credit limit and never exceed available balance")
    void testConcurrentChargesNeverExceedCreditLimit() throws InterruptedException {
        String testCabin = "CABIN-CONCURRENT-" + UUID.randomUUID().toString().substring(0, 8);
        BigDecimal initialLimit = BigDecimal.valueOf(100.00);

        CabinFolioAccount account = CabinFolioAccount.builder()
                .cabinNumber(testCabin)
                .primaryGuestName("Test Concurrent Passenger")
                .creditLimit(initialLimit)
                .currentBalance(BigDecimal.ZERO)
                .build();
        folioRepository.saveAndFlush(account);

        int numberOfThreads = 10;
        BigDecimal chargePerRequest = BigDecimal.valueOf(25.00); // 4 requests should succeed (4 * 25 = 100), remaining 6 must fail

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    CreateTransactionRequest req = CreateTransactionRequest.builder()
                            .stationId("BAR-DECK-9")
                            .cashierId("CASHIER-TEST")
                            .cabinNumber(testCabin)
                            .paymentMethod(PaymentMethod.CABIN_FOLIO)
                            .tax(BigDecimal.ZERO)
                            .gratuity(BigDecimal.ZERO)
                            .discount(BigDecimal.ZERO)
                            .items(List.of(
                                    TransactionItemRequest.builder()
                                            .itemCode("ITEM-CONCURRENT")
                                            .itemName("Drink")
                                            .category("BEVERAGE")
                                            .quantity(1)
                                            .unitPrice(chargePerRequest)
                                            .build()
                            ))
                            .build();

                    transactionService.processChargeTransaction(UUID.randomUUID().toString(), req);
                    successCount.incrementAndGet();
                } catch (InsufficientFolioBalanceException e) {
                    failureCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedExceptions.add(t);
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        boolean completed = executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(unexpectedExceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(4);
        assertThat(failureCount.get()).isEqualTo(6);

        CabinFolioAccount finalAccount = folioRepository.findByCabinNumber(testCabin).orElseThrow();
        assertThat(finalAccount.getCurrentBalance()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(finalAccount.getAvailableLimit()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

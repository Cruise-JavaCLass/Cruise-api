package com.cruise.pos.support;

import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.entity.PosTransactionItem;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.enums.TransactionType;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.PosTransactionRepository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dữ liệu mẫu dùng chung cho test tất toán.
 */
public final class SettlementTestData {

    private SettlementTestData() {
    }

    public static CabinFolioAccount newFolio(CabinFolioAccountRepository repository,
                                             String cabinNumber,
                                             String guestName,
                                             BigDecimal balance) {
        return repository.saveAndFlush(CabinFolioAccount.builder()
                .cabinNumber(cabinNumber)
                .primaryGuestName(guestName)
                .passengerId(1000L)
                .creditLimit(BigDecimal.valueOf(5000))
                .currentBalance(balance)
                .status(FolioStatus.ACTIVE)
                .build());
    }

    public static PosTransaction saveCharge(PosTransactionRepository repository,
                                            String cabinNumber,
                                            BigDecimal subtotal,
                                            BigDecimal tax,
                                            BigDecimal gratuity,
                                            BigDecimal discount,
                                            String itemName,
                                            String category,
                                            int quantity,
                                            BigDecimal unitPrice,
                                            TransactionStatus status) {

        BigDecimal total = subtotal.add(tax).add(gratuity).subtract(discount);
        PosTransaction transaction = PosTransaction.builder()
                .transactionUuid(UUID.randomUUID().toString())
                .stationId("STATION-1")
                .cashierId("cashier-1")
                .cabinNumber(cabinNumber)
                .guestName("Guest")
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .type(TransactionType.CHARGE)
                .status(status)
                .subtotal(subtotal)
                .tax(tax)
                .gratuity(gratuity)
                .discount(discount)
                .totalAmount(total)
                .refundedAmount(BigDecimal.ZERO)
                .currency("USD")
                .settled(false)
                .build();

        transaction.addItem(PosTransactionItem.builder()
                .itemCode("SKU-1")
                .itemName(itemName)
                .category(category)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalPrice(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .build());

        return repository.saveAndFlush(transaction);
    }

    public static String uniqueCabin(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

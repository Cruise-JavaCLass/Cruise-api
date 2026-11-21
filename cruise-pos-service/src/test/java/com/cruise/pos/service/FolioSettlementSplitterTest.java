package com.cruise.pos.service;

import com.cruise.pos.dto.SubFolioStatement;
import com.cruise.pos.entity.FolioSplitRule;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.enums.FolioSplitType;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.enums.TransactionType;
import com.cruise.pos.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FolioSettlementSplitter Unit Tests")
class FolioSettlementSplitterTest {

    private final FolioSettlementSplitter splitter = new FolioSettlementSplitter();

    private static PosTransaction tx(long id, Long passengerId, String amount) {
        PosTransaction tx = PosTransaction.builder()
                .transactionUuid("TX-" + id)
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-1")
                .passengerId(passengerId)
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .totalAmount(new BigDecimal(amount))
                .build();
        tx.setId(id);
        return tx;
    }

    private static FolioSplitRule rule(long passengerId, FolioSplitType type, String value) {
        return FolioSplitRule.builder()
                .passengerId(passengerId)
                .splitType(type)
                .splitValue(value == null ? null : new BigDecimal(value))
                .build();
    }

    private static SubFolioStatement statementOf(List<SubFolioStatement> statements, long passengerId) {
        return statements.stream().filter(s -> s.passengerId() == passengerId).findFirst().orElseThrow();
    }

    private static BigDecimal totalOf(List<SubFolioStatement> statements) {
        return statements.stream().map(SubFolioStatement::grandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("Splits shared charges 50% - 25% - 25% between three guests")
    void shouldSplitByPercentage() {
        List<SubFolioStatement> result = splitter.split(
                List.of(tx(1, null, "120.00"), tx(2, null, "80.00")),
                List.of(rule(1, FolioSplitType.PERCENTAGE, "50"),
                        rule(2, FolioSplitType.PERCENTAGE, "25"),
                        rule(3, FolioSplitType.PERCENTAGE, "25")));

        assertThat(statementOf(result, 1).sharedTotal()).isEqualByComparingTo("100.00");
        assertThat(statementOf(result, 2).sharedTotal()).isEqualByComparingTo("50.00");
        assertThat(statementOf(result, 3).sharedTotal()).isEqualByComparingTo("50.00");
        assertThat(totalOf(result)).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Equal split never loses a cent: 100.00 / 3 = 33.34 + 33.33 + 33.33")
    void shouldDistributeRoundingRemainder() {
        List<SubFolioStatement> result = splitter.split(
                List.of(tx(1, null, "100.00")),
                List.of(rule(1, FolioSplitType.EQUAL_SPLIT, null),
                        rule(2, FolioSplitType.EQUAL_SPLIT, null),
                        rule(3, FolioSplitType.EQUAL_SPLIT, null)));

        assertThat(statementOf(result, 1).sharedTotal()).isEqualByComparingTo("33.34");
        assertThat(statementOf(result, 2).sharedTotal()).isEqualByComparingTo("33.33");
        assertThat(statementOf(result, 3).sharedTotal()).isEqualByComparingTo("33.33");
        assertThat(totalOf(result)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Personal purchases go to the owner; shared charges are split by the rules")
    void shouldAssignPersonalChargesAndSplitShared() {
        List<SubFolioStatement> result = splitter.split(
                List.of(tx(1, 10L, "30.00"), tx(2, 20L, "20.00"), tx(3, null, "50.00")),
                List.of(rule(10, FolioSplitType.EQUAL_SPLIT, null), rule(20, FolioSplitType.EQUAL_SPLIT, null)));

        SubFolioStatement first = statementOf(result, 10);
        assertThat(first.personalTransactionIds()).containsExactly(1L);
        assertThat(first.personalTotal()).isEqualByComparingTo("30.00");
        assertThat(first.sharedTotal()).isEqualByComparingTo("25.00");
        assertThat(first.grandTotal()).isEqualByComparingTo("55.00");
        assertThat(statementOf(result, 20).grandTotal()).isEqualByComparingTo("45.00");
        assertThat(first.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Fixed amounts are taken first, then percentage, then the rest is split equally")
    void shouldCombineFixedPercentageAndEqualRules() {
        List<SubFolioStatement> result = splitter.split(
                List.of(tx(1, null, "100.00")),
                List.of(rule(1, FolioSplitType.FIXED_AMOUNT, "20.00"),
                        rule(2, FolioSplitType.PERCENTAGE, "50"),
                        rule(3, FolioSplitType.EQUAL_SPLIT, null),
                        rule(4, FolioSplitType.EQUAL_SPLIT, null)));

        assertThat(statementOf(result, 1).sharedTotal()).isEqualByComparingTo("20.00");
        assertThat(statementOf(result, 2).sharedTotal()).isEqualByComparingTo("40.00");
        assertThat(statementOf(result, 3).sharedTotal()).isEqualByComparingTo("20.00");
        assertThat(statementOf(result, 4).sharedTotal()).isEqualByComparingTo("20.00");
        assertThat(totalOf(result)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Falls back to an equal split between guests who made personal purchases when there are no rules")
    void shouldSplitEquallyWhenNoRules() {
        List<SubFolioStatement> result = splitter.split(
                List.of(tx(1, 10L, "10.00"), tx(2, 20L, "10.00"), tx(3, null, "20.00")), List.of());

        assertThat(statementOf(result, 10).grandTotal()).isEqualByComparingTo("20.00");
        assertThat(statementOf(result, 20).grandTotal()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Counts only completed charges, net of refunds and refund adjustments")
    void shouldUseNetAmountsOfBillableCharges() {
        PosTransaction partiallyRefunded = tx(1, null, "100.00");
        partiallyRefunded.setRefundedAmount(new BigDecimal("10.00"));

        PosTransaction adjustment = tx(9, null, "15.00");
        adjustment.setType(TransactionType.REFUND_ADJUSTMENT);
        adjustment.setOriginalTransactionId(1L);

        PosTransaction voided = tx(2, null, "999.00");
        voided.setStatus(TransactionStatus.VOIDED);
        PosTransaction fullyRefunded = tx(3, null, "50.00");
        fullyRefunded.setStatus(TransactionStatus.FULLY_REFUNDED);

        List<SubFolioStatement> result = splitter.split(
                List.of(partiallyRefunded, adjustment, voided, fullyRefunded),
                List.of(rule(1, FolioSplitType.EQUAL_SPLIT, null)));

        assertThat(totalOf(result)).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("Returns an empty list when there is nothing to bill")
    void shouldReturnEmptyWhenNoCharges() {
        assertThat(splitter.split(List.of(), List.of())).isEmpty();
        assertThat(splitter.split(null, null)).isEmpty();
    }

    @Test
    @DisplayName("Rejects invalid configurations instead of producing a wrong bill")
    void shouldRejectInvalidConfigurations() {
        List<PosTransaction> shared = List.of(tx(1, null, "10.00"));

        assertThatThrownBy(() -> splitter.split(shared,
                List.of(rule(1, FolioSplitType.PERCENTAGE, "60"), rule(2, FolioSplitType.PERCENTAGE, "30"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("100%");
        assertThatThrownBy(() -> splitter.split(shared,
                List.of(rule(1, FolioSplitType.PERCENTAGE, "70"), rule(2, FolioSplitType.PERCENTAGE, "70"))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> splitter.split(shared, List.of(rule(1, FolioSplitType.FIXED_AMOUNT, "20.00"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("vượt");
        assertThatThrownBy(() -> splitter.split(shared, List.of(rule(1, FolioSplitType.FIXED_AMOUNT, "5.00"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("chưa được phân bổ");
        assertThatThrownBy(() -> splitter.split(shared,
                List.of(rule(1, FolioSplitType.EQUAL_SPLIT, null), rule(1, FolioSplitType.EQUAL_SPLIT, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("nhiều hơn một quy tắc");
        assertThatThrownBy(() -> splitter.split(shared, List.of(rule(1, FolioSplitType.PERCENTAGE, null))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> splitter.split(shared, List.of()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Rejects bills that mix currencies")
    void shouldRejectMixedCurrencies() {
        PosTransaction euro = tx(2, null, "5.00");
        euro.setCurrency("EUR");

        assertThatThrownBy(() -> splitter.split(List.of(tx(1, null, "10.00"), euro),
                List.of(rule(1, FolioSplitType.EQUAL_SPLIT, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("tiền tệ");
    }
}

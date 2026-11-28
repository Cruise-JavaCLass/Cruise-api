package com.cruise.pos.service;

import com.cruise.pos.dto.SubFolioStatement;
import com.cruise.pos.entity.FolioSplitRule;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.enums.FolioSplitType;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.enums.TransactionType;
import com.cruise.pos.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Chia hóa đơn cabin cho từng khách (ở chung phòng hoặc đi theo đoàn).
 *
 * <p>Quy ước:
 * <ul>
 *   <li>Chi tiêu cá nhân: giao dịch có passengerId (quẹt thẻ riêng), gán đích danh cho khách đó.</li>
 *   <li>Phí chung: giao dịch không có passengerId (dịch vụ phòng, mini-bar...), chia theo quy tắc:
 *       FIXED_AMOUNT lấy trước; phần còn lại chia cho PERCENTAGE theo tỷ lệ,
 *       và phần phần trăm chưa dùng chia đều cho các khách EQUAL_SPLIT.</li>
 *   <li>Mọi phép tính làm trên đơn vị nhỏ nhất (cent), phần lẻ phân bổ theo phương pháp phần dư lớn nhất,
 *       nên tổng các sao kê con luôn bằng đúng tổng chi tiêu của cabin.</li>
 *   <li>Chỉ tính giao dịch CHARGE đã hoàn tất; trừ phần đã hoàn tiền và các bút toán REFUND_ADJUSTMENT.</li>
 * </ul>
 */
@Component
public class FolioSettlementSplitter {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DEFAULT_CURRENCY = "USD";

    private record BillableCharge(Long transactionId, Long passengerId, long cents, String currency) {
    }

    private record ShareCandidate(Long passengerId, long floorCents, BigDecimal fraction) {
    }

    public List<SubFolioStatement> split(List<PosTransaction> transactions, List<FolioSplitRule> rules) {
        List<BillableCharge> charges = collectBillableCharges(transactions == null ? List.of() : transactions);
        String currency = resolveCurrency(charges);

        Map<Long, Long> personalCents = new TreeMap<>();
        Map<Long, List<Long>> personalTransactionIds = new TreeMap<>();
        long sharedPoolCents = 0;
        long grandTotalCents = 0;
        for (BillableCharge charge : charges) {
            grandTotalCents = Math.addExact(grandTotalCents, charge.cents());
            if (charge.passengerId() != null) {
                personalCents.merge(charge.passengerId(), charge.cents(), Math::addExact);
                personalTransactionIds.computeIfAbsent(charge.passengerId(), id -> new ArrayList<>())
                        .add(charge.transactionId());
            } else {
                sharedPoolCents = Math.addExact(sharedPoolCents, charge.cents());
            }
        }

        Map<Long, FolioSplitRule> ruleByPassenger = indexRules(rules == null ? List.of() : rules);
        Map<Long, Long> sharedCents = allocateShared(sharedPoolCents, ruleByPassenger, personalCents.keySet());

        Set<Long> participants = new TreeSet<>(personalCents.keySet());
        participants.addAll(ruleByPassenger.keySet());

        List<SubFolioStatement> statements = new ArrayList<>();
        long allocatedTotal = 0;
        for (Long passengerId : participants) {
            long personal = personalCents.getOrDefault(passengerId, 0L);
            long shared = sharedCents.getOrDefault(passengerId, 0L);
            allocatedTotal = Math.addExact(allocatedTotal, Math.addExact(personal, shared));
            statements.add(new SubFolioStatement(
                    passengerId,
                    List.copyOf(personalTransactionIds.getOrDefault(passengerId, List.of())),
                    fromCents(personal),
                    fromCents(shared),
                    fromCents(Math.addExact(personal, shared)),
                    currency));
        }

        if (allocatedTotal != grandTotalCents) {
            throw new IllegalStateException(String.format(
                    "Sai lệch khi chia hóa đơn: tổng sao kê con %d cent khác tổng chi tiêu %d cent",
                    allocatedTotal, grandTotalCents));
        }
        return statements;
    }

    // ---------------------------------------------------------------------
    // Thu thập các khoản chi hợp lệ
    // ---------------------------------------------------------------------

    private List<BillableCharge> collectBillableCharges(List<PosTransaction> transactions) {
        Map<Long, Long> adjustmentCentsByOriginal = new HashMap<>();
        for (PosTransaction tx : transactions) {
            if (tx.getType() == TransactionType.REFUND_ADJUSTMENT
                    && isCompleted(tx.getStatus())
                    && tx.getOriginalTransactionId() != null) {
                adjustmentCentsByOriginal.merge(tx.getOriginalTransactionId(),
                        toCents(tx.getTotalAmount()), Math::addExact);
            }
        }

        List<BillableCharge> charges = new ArrayList<>();
        for (PosTransaction tx : transactions) {
            boolean isCharge = tx.getType() == null || tx.getType() == TransactionType.CHARGE;
            boolean isBillable = tx.getStatus() == null
                    || tx.getStatus() == TransactionStatus.COMPLETED
                    || tx.getStatus() == TransactionStatus.PARTIALLY_REFUNDED;
            if (!isCharge || !isBillable) {
                continue;
            }
            long net = toCents(tx.getTotalAmount())
                    - toCents(tx.getRefundedAmount())
                    - adjustmentCentsByOriginal.getOrDefault(tx.getId(), 0L);
            if (net > 0) {
                charges.add(new BillableCharge(tx.getId(), tx.getPassengerId(), net,
                        tx.getCurrency() == null ? DEFAULT_CURRENCY : tx.getCurrency()));
            }
        }
        return charges;
    }

    private boolean isCompleted(TransactionStatus status) {
        return status == null || status == TransactionStatus.COMPLETED;
    }

    private String resolveCurrency(List<BillableCharge> charges) {
        Set<String> currencies = new HashSet<>();
        for (BillableCharge charge : charges) {
            currencies.add(charge.currency());
        }
        if (currencies.size() > 1) {
            throw new BusinessRuleException("Không thể chia hóa đơn có nhiều loại tiền tệ: " + new TreeSet<>(currencies));
        }
        return currencies.isEmpty() ? DEFAULT_CURRENCY : currencies.iterator().next();
    }

    private Map<Long, FolioSplitRule> indexRules(List<FolioSplitRule> rules) {
        Map<Long, FolioSplitRule> byPassenger = new TreeMap<>();
        for (FolioSplitRule rule : rules) {
            if (rule.getPassengerId() == null || rule.getSplitType() == null) {
                throw new BusinessRuleException("Quy tắc chia thiếu mã hành khách hoặc kiểu chia");
            }
            if (byPassenger.put(rule.getPassengerId(), rule) != null) {
                throw new BusinessRuleException("Hành khách " + rule.getPassengerId() + " có nhiều hơn một quy tắc chia");
            }
        }
        return byPassenger;
    }

    // ---------------------------------------------------------------------
    // Phân bổ phí chung
    // ---------------------------------------------------------------------

    private Map<Long, Long> allocateShared(long poolCents, Map<Long, FolioSplitRule> ruleByPassenger,
                                           Set<Long> personalOwners) {
        Map<Long, Long> result = new TreeMap<>();
        if (poolCents == 0) {
            return result;
        }

        Map<Long, FolioSplitRule> effective = ruleByPassenger;
        if (effective.isEmpty()) {
            if (personalOwners.isEmpty()) {
                throw new BusinessRuleException("Có phí chung nhưng chưa có quy tắc chia hoặc khách nào để chia");
            }
            // Không có quy tắc: chia đều cho các khách có chi tiêu cá nhân
            effective = new TreeMap<>();
            for (Long passengerId : personalOwners) {
                effective.put(passengerId, FolioSplitRule.builder()
                        .passengerId(passengerId)
                        .splitType(FolioSplitType.EQUAL_SPLIT)
                        .build());
            }
        }

        long fixedTotal = 0;
        BigDecimal percentageSum = BigDecimal.ZERO;
        int percentageCount = 0;
        int equalCount = 0;
        for (Map.Entry<Long, FolioSplitRule> entry : effective.entrySet()) {
            FolioSplitRule rule = entry.getValue();
            switch (rule.getSplitType()) {
                case FIXED_AMOUNT -> {
                    long cents = toCents(requireValue(rule));
                    result.put(entry.getKey(), cents);
                    fixedTotal = Math.addExact(fixedTotal, cents);
                }
                case PERCENTAGE -> {
                    BigDecimal percentage = requireValue(rule);
                    if (percentage.compareTo(HUNDRED) > 0) {
                        throw new BusinessRuleException("Tỷ lệ phần trăm của hành khách " + entry.getKey() + " vượt quá 100%");
                    }
                    percentageSum = percentageSum.add(percentage);
                    percentageCount++;
                }
                case EQUAL_SPLIT -> equalCount++;
            }
        }

        if (fixedTotal > poolCents) {
            throw new BusinessRuleException("Tổng số tiền cố định vượt tổng phí chung của cabin");
        }
        if (percentageSum.compareTo(HUNDRED) > 0) {
            throw new BusinessRuleException("Tổng tỷ lệ phần trăm vượt quá 100%");
        }
        if (equalCount == 0 && percentageCount > 0 && percentageSum.compareTo(HUNDRED) != 0) {
            throw new BusinessRuleException("Tổng tỷ lệ phần trăm phải bằng 100% khi không có khách chia đều");
        }

        long remainder = poolCents - fixedTotal;
        if (remainder > 0 && percentageCount == 0 && equalCount == 0) {
            throw new BusinessRuleException("Phí chung chưa được phân bổ hết cho các khách");
        }
        if (remainder == 0) {
            effective.keySet().forEach(id -> result.putIfAbsent(id, 0L));
            return result;
        }

        // Phương pháp phần dư lớn nhất: tổng trọng số luôn bằng mẫu số nên không mất cent nào
        BigDecimal multiplier = BigDecimal.valueOf(Math.max(equalCount, 1));
        BigDecimal denominator = HUNDRED.multiply(multiplier);
        BigDecimal residualPercentage = HUNDRED.subtract(percentageSum);

        List<ShareCandidate> candidates = new ArrayList<>();
        long floorTotal = 0;
        for (Map.Entry<Long, FolioSplitRule> entry : effective.entrySet()) {
            FolioSplitRule rule = entry.getValue();
            if (rule.getSplitType() == FolioSplitType.FIXED_AMOUNT) {
                continue;
            }
            BigDecimal weight = rule.getSplitType() == FolioSplitType.PERCENTAGE
                    ? rule.getSplitValue().multiply(multiplier)
                    : residualPercentage;
            BigDecimal numerator = BigDecimal.valueOf(remainder).multiply(weight);
            BigDecimal floor = numerator.divide(denominator, 0, RoundingMode.DOWN);
            BigDecimal fraction = numerator.subtract(floor.multiply(denominator));
            long floorCents = floor.longValueExact();
            floorTotal = Math.addExact(floorTotal, floorCents);
            candidates.add(new ShareCandidate(entry.getKey(), floorCents, fraction));
        }

        long leftover = remainder - floorTotal;
        if (leftover < 0 || leftover > candidates.size()) {
            throw new IllegalStateException("Không thể phân bổ phần lẻ khi chia phí chung");
        }
        candidates.sort(Comparator.comparing(ShareCandidate::fraction).reversed()
                .thenComparing(ShareCandidate::passengerId));
        for (int i = 0; i < candidates.size(); i++) {
            ShareCandidate candidate = candidates.get(i);
            result.put(candidate.passengerId(), candidate.floorCents() + (i < leftover ? 1 : 0));
        }
        return result;
    }

    private BigDecimal requireValue(FolioSplitRule rule) {
        BigDecimal value = rule.getSplitValue();
        if (value == null || value.signum() < 0) {
            throw new BusinessRuleException("Quy tắc chia " + rule.getSplitType()
                    + " của hành khách " + rule.getPassengerId() + " thiếu giá trị hoặc giá trị âm");
        }
        return value;
    }

    // ---------------------------------------------------------------------
    // Tiền tệ
    // ---------------------------------------------------------------------

    private static long toCents(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}

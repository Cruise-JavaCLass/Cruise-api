package com.cruise.pos.service;

import com.cruise.pos.dto.ShiftSummaryResponse;
import com.cruise.pos.entity.PosShift;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.ShiftStatus;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.enums.TransactionType;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.PosShiftRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosShiftService {

    private final PosShiftRepository shiftRepository;
    private final PosTransactionRepository transactionRepository;

    @Transactional
    public PosShift openShift(String stationId, String cashierId, BigDecimal startingCash) {
        Optional<PosShift> existing = shiftRepository.findByStationIdAndStatusWithLock(stationId, ShiftStatus.OPEN);
        if (existing.isPresent()) {
            return existing.get();
        }

        synchronized (stationId.intern()) {
            existing = shiftRepository.findByStationIdAndStatus(stationId, ShiftStatus.OPEN);
            if (existing.isPresent()) {
                return existing.get();
            }

            PosShift shift = PosShift.builder()
                    .stationId(stationId)
                    .cashierId(cashierId)
                    .startingCash(startingCash != null ? startingCash : BigDecimal.ZERO)
                    .status(ShiftStatus.OPEN)
                    .openedAt(Instant.now())
                    .totalSales(BigDecimal.ZERO)
                    .totalRefunds(BigDecimal.ZERO)
                    .netRevenue(BigDecimal.ZERO)
                    .transactionCount(0)
                    .build();

            return shiftRepository.save(shift);
        }
    }

    @Transactional(readOnly = true)
    public Optional<PosShift> getActiveShift(String stationId) {
        return shiftRepository.findByStationIdAndStatus(stationId, ShiftStatus.OPEN);
    }

    @Transactional
    public PosShift closeShift(Long shiftId, String managerId, BigDecimal endingCash) {
        PosShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc ID: " + shiftId));

        if (shift.getStatus() == ShiftStatus.CLOSED) {
            throw new BusinessRuleException("Ca làm việc này đã được chốt quyết toán trước đó!");
        }

        shift.setStatus(ShiftStatus.CLOSED);
        shift.setClosedAt(Instant.now());
        shift.setClosedByManagerId(managerId != null ? managerId : "MANAGER-DEFAULT");
        shift.setEndingCash(endingCash != null ? endingCash : shift.getStartingCash());

        // Mark all transactions in this shift as settled
        List<PosTransaction> transactions = transactionRepository.findByShiftId(shiftId);
        for (PosTransaction tx : transactions) {
            tx.setSettled(true);
        }
        transactionRepository.saveAll(transactions);

        log.info("Shift ID {} closed by manager {}. Net revenue: ${}", shiftId, managerId, shift.getNetRevenue());
        return shiftRepository.save(shift);
    }

    @Transactional(readOnly = true)
    public ShiftSummaryResponse generateShiftSummary(Long shiftId, String reportType) {
        PosShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc ID: " + shiftId));

        List<PosTransaction> transactions = transactionRepository.findByShiftId(shiftId);

        Map<String, BigDecimal> byPaymentMethod = new HashMap<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            byPaymentMethod.put(method.name(), BigDecimal.ZERO);
        }

        Map<String, BigDecimal> byCategory = new HashMap<>();

        for (PosTransaction tx : transactions) {
            boolean isNonVoidedCharge = (tx.getType() == null || tx.getType() == TransactionType.CHARGE)
                    && tx.getStatus() != TransactionStatus.VOIDED
                    && tx.getStatus() != TransactionStatus.FAILED
                    && tx.getStatus() != TransactionStatus.PENDING;
            if (isNonVoidedCharge) {
                String pm = tx.getPaymentMethod().name();
                byPaymentMethod.put(pm, byPaymentMethod.getOrDefault(pm, BigDecimal.ZERO).add(tx.getTotalAmount()));

                if (tx.getItems() != null) {
                    tx.getItems().forEach(item -> {
                        String cat = item.getCategory();
                        byCategory.put(cat, byCategory.getOrDefault(cat, BigDecimal.ZERO).add(item.getTotalPrice()));
                    });
                }
            }
        }

        return ShiftSummaryResponse.builder()
                .shiftId(shift.getId())
                .stationId(shift.getStationId())
                .cashierId(shift.getCashierId())
                .status(shift.getStatus())
                .openedAt(shift.getOpenedAt())
                .closedAt(shift.getClosedAt())
                .reportType("Z_REPORT".equalsIgnoreCase(reportType) ? "Z_REPORT" : "X_REPORT")
                .startingCash(shift.getStartingCash())
                .endingCash(shift.getEndingCash())
                .totalSales(shift.getTotalSales())
                .totalRefunds(shift.getTotalRefunds())
                .netRevenue(shift.getNetRevenue())
                .transactionCount(shift.getTransactionCount())
                .revenueByPaymentMethod(byPaymentMethod)
                .revenueByCategory(byCategory)
                .build();
    }
}

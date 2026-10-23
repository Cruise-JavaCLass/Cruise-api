package com.cruise.pos.service;

import com.cruise.pos.dto.CashDropRequest;
import com.cruise.pos.dto.CashDropResponse;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.entity.PosCashDrop;
import com.cruise.pos.entity.PosSafeLedger;
import com.cruise.pos.entity.PosShift;
import com.cruise.pos.enums.ShiftStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.PosAuditLogRepository;
import com.cruise.pos.repository.PosCashDropRepository;
import com.cruise.pos.repository.PosSafeLedgerRepository;
import com.cruise.pos.repository.PosShiftRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosCashDropService {

    public static final String DEFAULT_SAFE_ID = "MAIN_SAFE";
    /**
     * Standard opening float allocated to the vessel main safe upon initial deployment.
     */
    public static final BigDecimal DEFAULT_INITIAL_SAFE_BALANCE = BigDecimal.valueOf(10000.00);

    private final PosShiftRepository shiftRepository;
    private final PosCashDropRepository cashDropRepository;
    private final PosSafeLedgerRepository safeLedgerRepository;
    private final PosAuditLogRepository auditLogRepository;
    private final PosManagerAuthService posManagerAuthService;
    private final EscPosReceiptService escPosReceiptService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CashDropResponse processCashDrop(Long shiftId, CashDropRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CashDropRequest must not be null");
        }

        PosShift shift = shiftRepository.findByIdWithLock(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found: " + shiftId));

        if (shift.getStatus() != ShiftStatus.OPEN) {
            throw new BusinessRuleException("Cannot perform cash drop on a closed shift: " + shiftId);
        }

        // Bind cashier identity from active shift
        String cashierId = shift.getCashierId();
        if (cashierId == null || cashierId.isBlank()) {
            throw new BusinessRuleException("Shift has no assigned cashier");
        }
        if (request.cashierId() != null && !cashierId.equalsIgnoreCase(request.cashierId().trim())) {
            throw new BusinessRuleException("Request cashier ID does not match active shift cashier: " + cashierId);
        }

        // Prevent cashier from being their own witness
        if (request.witnessId() != null && cashierId.equalsIgnoreCase(request.witnessId().trim())) {
            throw new BusinessRuleException("Witness must be a different person than the cashier");
        }

        // Dual signature verification: verify witness/manager PIN
        boolean witnessValid = posManagerAuthService.verifyRefundApproval(request.witnessId(), request.witnessPin());
        if (!witnessValid) {
            log.warn("Cash drop witness verification failed for witnessId: {}", request.witnessId());
            throw new BusinessRuleException("Witness/Supervisor authentication failed: invalid ID or PIN");
        }

        BigDecimal dropAmount = request.amount();
        if (dropAmount == null || dropAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Cash drop amount must be greater than zero");
        }

        // Validate currency breakdown if provided
        if (request.currencyBreakdown() != null && !request.currencyBreakdown().isEmpty()) {
            BigDecimal breakdownTotal = BigDecimal.ZERO;
            for (Map.Entry<String, Integer> entry : request.currencyBreakdown().entrySet()) {
                if (entry.getValue() == null || entry.getValue() < 0) {
                    throw new BusinessRuleException("Currency breakdown counts must be non-negative");
                }
                BigDecimal denom;
                try {
                    denom = new BigDecimal(entry.getKey().trim());
                } catch (NumberFormatException e) {
                    throw new BusinessRuleException("Currency breakdown denominations must be numeric");
                }
                if (denom.signum() <= 0) {
                    throw new BusinessRuleException("Currency breakdown denominations must be greater than zero");
                }
                breakdownTotal = breakdownTotal.add(denom.multiply(BigDecimal.valueOf(entry.getValue())));
            }
            if (breakdownTotal.compareTo(dropAmount) != 0) {
                throw new BusinessRuleException(String.format(
                        "Currency breakdown total ($%s) does not match drop amount ($%s)",
                        breakdownTotal, dropAmount));
            }
        }

        // Update ship safe balance under pessimistic lock
        PosSafeLedger safeLedger = safeLedgerRepository.findByIdWithLock(DEFAULT_SAFE_ID)
                .orElseGet(() -> safeLedgerRepository.save(PosSafeLedger.builder()
                        .safeId(DEFAULT_SAFE_ID)
                        .balance(DEFAULT_INITIAL_SAFE_BALANCE)
                        .build()));

        BigDecimal newSafeBalance = safeLedger.getBalance().add(dropAmount);
        safeLedger.setBalance(newSafeBalance);
        safeLedgerRepository.save(safeLedger);

        // Update shift total cash drops
        shift.addCashDrop(dropAmount);
        shiftRepository.save(shift);

        // Convert currency breakdown map to JSON string if present
        String breakdownJson = null;
        if (request.currencyBreakdown() != null && !request.currencyBreakdown().isEmpty()) {
            try {
                breakdownJson = objectMapper.writeValueAsString(request.currencyBreakdown());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize currency breakdown", e);
                breakdownJson = request.currencyBreakdown().toString();
            }
        }

        String receiptNumber = "SAFE-DROP-" + shiftId + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PosCashDrop cashDrop = PosCashDrop.builder()
                .shiftId(shift.getId())
                .stationId(shift.getStationId())
                .cashierId(cashierId)
                .witnessId(request.witnessId())
                .amount(dropAmount)
                .safeBalanceAfter(newSafeBalance)
                .receiptNumber(receiptNumber)
                .currencyBreakdown(breakdownJson)
                .droppedAt(Instant.now())
                .dualSignatureVerified(true)
                .notes(request.notes())
                .build();

        PosCashDrop saved = cashDropRepository.save(cashDrop);

        // Audit Trail
        PosAuditLog auditLog = PosAuditLog.builder()
                .transactionId(saved.getId())
                .action("MID_SHIFT_CASH_DROP")
                .performedBy(cashierId)
                .managerId(request.witnessId())
                .reason("CASH_DRAWER_LIMIT_EXCEEDED")
                .details(String.format("Cash drop of $%s to safe. Safe balance: $%s. Receipt: %s",
                        dropAmount, newSafeBalance, receiptNumber))
                .build();
        auditLogRepository.save(auditLog);

        String receiptSlip = escPosReceiptService.generateSafeDropSlip(saved);

        log.info("Mid-shift cash drop processed: shiftId={}, amount={}, cashier={}, witness={}, safeBalance={}",
                shiftId, dropAmount, cashierId, request.witnessId(), newSafeBalance);

        return new CashDropResponse(
                saved.getId(),
                saved.getShiftId(),
                saved.getStationId(),
                saved.getCashierId(),
                saved.getWitnessId(),
                saved.getAmount(),
                saved.getSafeBalanceAfter(),
                saved.getReceiptNumber(),
                saved.getCurrencyBreakdown(),
                saved.getDroppedAt(),
                saved.getDualSignatureVerified(),
                receiptSlip
        );
    }

    @Transactional(readOnly = true)
    public List<PosCashDrop> getCashDropsByShift(Long shiftId) {
        return cashDropRepository.findByShiftIdOrderByDroppedAtAsc(shiftId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getShipSafeBalance() {
        return safeLedgerRepository.findById(DEFAULT_SAFE_ID)
                .map(PosSafeLedger::getBalance)
                .orElse(DEFAULT_INITIAL_SAFE_BALANCE);
    }
}

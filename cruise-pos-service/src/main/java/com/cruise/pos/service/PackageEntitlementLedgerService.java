package com.cruise.pos.service;

import com.cruise.pos.dto.EntitlementDeductRequest;
import com.cruise.pos.dto.EntitlementRollbackRequest;
import com.cruise.pos.entity.GuestPackageEntitlement;
import com.cruise.pos.entity.PackageEntitlementAuditLedger;
import com.cruise.pos.enums.EntitlementStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.GuestPackageEntitlementRepository;
import com.cruise.pos.repository.PackageEntitlementAuditLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PackageEntitlementLedgerService {

    private final GuestPackageEntitlementRepository entitlementRepository;
    private final PackageEntitlementAuditLedgerRepository auditLedgerRepository;

    @Transactional
    public void deductEntitlementQuota(EntitlementDeductRequest request) {
        Objects.requireNonNull(request, "EntitlementDeductRequest must not be null");

        // Idempotency: check if whole transaction deduction was already applied
        if (request.getTransactionId() != null && !request.getTransactionId().isBlank()) {
            boolean alreadyProcessed = auditLedgerRepository.findByTransactionIdAndEntitlementId(
                    request.getTransactionId(), null != request.getItems() && !request.getItems().isEmpty() ? request.getItems().get(0).getEntitlementId() : -1L
            ).stream().anyMatch(e -> "DEDUCTION".equals(e.getOperationType()));

            if (alreadyProcessed) {
                log.info("Deduction transaction {} already processed, returning idempotently", request.getTransactionId());
                return;
            }
        }

        Instant now = Instant.now();

        for (EntitlementDeductRequest.DeductItem item : request.getItems()) {
            // Pessimistic lock first to serialize updates
            GuestPackageEntitlement entitlement = entitlementRepository.findByIdForUpdate(item.getEntitlementId())
                    .orElseThrow(() -> new ResourceNotFoundException("Entitlement not found: " + item.getEntitlementId()));

            // Passenger ownership verification
            if (!Objects.equals(entitlement.getPassengerId(), request.getPassengerId())) {
                throw new BusinessRuleException("Entitlement does not belong to passenger " + request.getPassengerId());
            }

            // Status verification
            if (entitlement.getStatus() != EntitlementStatus.ACTIVE) {
                throw new BusinessRuleException("Entitlement is not active (Status: " + entitlement.getStatus() + ")");
            }

            // Validity window check
            if (entitlement.getValidFrom() != null && now.isBefore(entitlement.getValidFrom())) {
                throw new BusinessRuleException("Entitlement is not yet valid");
            }
            if (entitlement.getValidTo() != null && now.isAfter(entitlement.getValidTo())) {
                throw new BusinessRuleException("Entitlement has expired");
            }

            if (entitlement.getRemainingQuota() < item.getUnits()) {
                throw new BusinessRuleException("Insufficient quota for entitlement: " + entitlement.getPackageName());
            }

            entitlement.setUsedQuota(entitlement.getUsedQuota() + item.getUnits());
            if (entitlement.getRemainingQuota() == 0) {
                entitlement.setStatus(EntitlementStatus.EXHAUSTED);
            }
            entitlementRepository.save(entitlement);

            PackageEntitlementAuditLedger logEntry = PackageEntitlementAuditLedger.builder()
                    .entitlementId(entitlement.getId())
                    .passengerId(request.getPassengerId())
                    .transactionId(request.getTransactionId())
                    .operationType("DEDUCTION")
                    .quotaChanged(item.getUnits())
                    .amountCovered(item.getAmountCovered() != null ? item.getAmountCovered() : BigDecimal.ZERO)
                    .productName(item.getProductName())
                    .timestamp(Instant.now())
                    .notes("Quota deducted during checkout")
                    .build();
            auditLedgerRepository.save(logEntry);
            log.info("Deducted quota {} for passenger {} on pkg {}", item.getUnits(), request.getPassengerId(), entitlement.getPackageName());
        }
    }

    @Transactional
    public void rollbackEntitlementQuota(EntitlementRollbackRequest request) {
        Objects.requireNonNull(request, "EntitlementRollbackRequest must not be null");

        for (EntitlementRollbackRequest.RollbackItem item : request.getItems()) {
            // Pessimistic lock first to serialize updates
            GuestPackageEntitlement entitlement = entitlementRepository.findByIdForUpdate(item.getEntitlementId())
                    .orElseThrow(() -> new ResourceNotFoundException("Entitlement not found: " + item.getEntitlementId()));

            // Passenger ownership verification
            if (!Objects.equals(entitlement.getPassengerId(), request.getPassengerId())) {
                throw new BusinessRuleException("Entitlement does not belong to passenger " + request.getPassengerId());
            }

            // Verify prior deduction units under this transaction
            int unitsToRollback = item.getUnits();
            if (request.getTransactionId() != null && !request.getTransactionId().isBlank()) {
                List<PackageEntitlementAuditLedger> logs = auditLedgerRepository
                        .findByTransactionIdAndEntitlementId(request.getTransactionId(), item.getEntitlementId());
                int netDeducted = 0;
                for (PackageEntitlementAuditLedger entry : logs) {
                    if ("DEDUCTION".equals(entry.getOperationType())) {
                        netDeducted += entry.getQuotaChanged();
                    } else if ("ROLLBACK".equals(entry.getOperationType())) {
                        netDeducted -= Math.abs(entry.getQuotaChanged());
                    }
                }
                if (netDeducted <= 0) {
                    log.info("No prior net deductions remaining for txId={}, entitlementId={}", request.getTransactionId(), item.getEntitlementId());
                    continue;
                }
                if (unitsToRollback > netDeducted) {
                    throw new BusinessRuleException("Cannot rollback " + unitsToRollback + " units; only " + netDeducted + " units were deducted in transaction " + request.getTransactionId());
                }
            }

            int actualRestored = Math.min(unitsToRollback, entitlement.getUsedQuota());
            entitlement.setUsedQuota(entitlement.getUsedQuota() - actualRestored);
            if (entitlement.getStatus() == EntitlementStatus.EXHAUSTED && entitlement.getRemainingQuota() > 0) {
                entitlement.setStatus(EntitlementStatus.ACTIVE);
            }
            entitlementRepository.save(entitlement);

            PackageEntitlementAuditLedger logEntry = PackageEntitlementAuditLedger.builder()
                    .entitlementId(entitlement.getId())
                    .passengerId(request.getPassengerId())
                    .transactionId(request.getTransactionId())
                    .operationType("ROLLBACK")
                    .quotaChanged(-actualRestored)
                    .amountCovered(BigDecimal.ZERO)
                    .timestamp(Instant.now())
                    .notes("Quota rolled back: " + item.getReason())
                    .build();
            auditLedgerRepository.save(logEntry);
            log.info("Rolled back quota {} for passenger {} on pkg {}", actualRestored, request.getPassengerId(), entitlement.getPackageName());
        }
    }

    @Transactional(readOnly = true)
    public List<GuestPackageEntitlement> resolvePackageCollision(Long passengerId) {
        // Multi-package collision resolver: returns active entitlements sorted deterministically by Priority Level desc, then expiry date asc
        List<GuestPackageEntitlement> activeList = entitlementRepository.findActiveEntitlementsOrderByPriority(passengerId);
        activeList.sort((a, b) -> {
            int pCompare = Integer.compare(b.getPriorityLevel(), a.getPriorityLevel());
            if (pCompare != 0) return pCompare;
            if (a.getValidTo() != null && b.getValidTo() != null) {
                return a.getValidTo().compareTo(b.getValidTo());
            }
            if (a.getValidTo() != null) return -1;
            if (b.getValidTo() != null) return 1;
            return Long.compare(a.getId(), b.getId());
        });
        return activeList;
    }
}

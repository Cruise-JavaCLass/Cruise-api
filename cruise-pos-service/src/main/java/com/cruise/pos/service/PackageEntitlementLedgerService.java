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

        for (EntitlementDeductRequest.DeductItem item : request.getItems()) {
            GuestPackageEntitlement entitlement = entitlementRepository.findById(item.getEntitlementId())
                    .orElseThrow(() -> new ResourceNotFoundException("Entitlement not found: " + item.getEntitlementId()));

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
            GuestPackageEntitlement entitlement = entitlementRepository.findById(item.getEntitlementId())
                    .orElseThrow(() -> new ResourceNotFoundException("Entitlement not found: " + item.getEntitlementId()));

            int newUsed = Math.max(0, entitlement.getUsedQuota() - item.getUnits());
            entitlement.setUsedQuota(newUsed);
            if (entitlement.getStatus() == EntitlementStatus.EXHAUSTED && entitlement.getRemainingQuota() > 0) {
                entitlement.setStatus(EntitlementStatus.ACTIVE);
            }
            entitlementRepository.save(entitlement);

            PackageEntitlementAuditLedger logEntry = PackageEntitlementAuditLedger.builder()
                    .entitlementId(entitlement.getId())
                    .passengerId(request.getPassengerId())
                    .transactionId(request.getTransactionId())
                    .operationType("ROLLBACK")
                    .quotaChanged(-item.getUnits())
                    .amountCovered(BigDecimal.ZERO)
                    .timestamp(Instant.now())
                    .notes("Quota rolled back: " + item.getReason())
                    .build();
            auditLedgerRepository.save(logEntry);
            log.info("Rolled back quota {} for passenger {} on pkg {}", item.getUnits(), request.getPassengerId(), entitlement.getPackageName());
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
            return Long.compare(a.getId(), b.getId());
        });
        return activeList;
    }
}

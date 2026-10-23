package com.cruise.pos.service;

import com.cruise.pos.dto.EntitlementDeductRequest;
import com.cruise.pos.dto.EntitlementRollbackRequest;
import com.cruise.pos.entity.GuestPackageEntitlement;
import com.cruise.pos.entity.PackageEntitlementAuditLedger;
import com.cruise.pos.enums.EntitlementStatus;
import com.cruise.pos.enums.PackageType;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.GuestPackageEntitlementRepository;
import com.cruise.pos.repository.PackageEntitlementAuditLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PackageEntitlementLedgerServiceTest {

    @Mock
    private GuestPackageEntitlementRepository entitlementRepository;

    @Mock
    private PackageEntitlementAuditLedgerRepository auditLedgerRepository;

    @InjectMocks
    private PackageEntitlementLedgerService ledgerService;

    private GuestPackageEntitlement entitlement;

    @BeforeEach
    void setUp() {
        entitlement = GuestPackageEntitlement.builder()
                .passengerId(101L)
                .packageName("Dining Package 3-Night")
                .packageType(PackageType.SPECIALTY_DINING)
                .totalQuota(3)
                .usedQuota(1)
                .priorityLevel(3)
                .status(EntitlementStatus.ACTIVE)
                .build();
        entitlement.setId(10L);
    }

    @Test
    @DisplayName("deductEntitlementQuota successfully updates used quota and writes audit ledger")
    void testDeductSuccess() {
        when(entitlementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entitlement));

        EntitlementDeductRequest request = EntitlementDeductRequest.builder()
                .passengerId(101L)
                .transactionId("TX-1234")
                .items(List.of(EntitlementDeductRequest.DeductItem.builder()
                        .entitlementId(10L)
                        .productName("Steakhouse Dinner")
                        .units(2)
                        .amountCovered(BigDecimal.valueOf(110.00))
                        .build()))
                .build();

        ledgerService.deductEntitlementQuota(request);

        assertThat(entitlement.getUsedQuota()).isEqualTo(3);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.EXHAUSTED);
        verify(entitlementRepository).save(entitlement);
        verify(auditLedgerRepository).save(any(PackageEntitlementAuditLedger.class));
    }

    @Test
    @DisplayName("deductEntitlementQuota retains ACTIVE status when quota is partially remaining")
    void testDeductSuccess_PartialQuotaRemaining() {
        when(entitlementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entitlement));

        EntitlementDeductRequest request = EntitlementDeductRequest.builder()
                .passengerId(101L)
                .transactionId("TX-1234")
                .items(List.of(EntitlementDeductRequest.DeductItem.builder()
                        .entitlementId(10L)
                        .productName("Cocktail")
                        .units(1)
                        .build()))
                .build();

        ledgerService.deductEntitlementQuota(request);

        assertThat(entitlement.getUsedQuota()).isEqualTo(2);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        verify(entitlementRepository).save(entitlement);
    }

    @Test
    @DisplayName("deductEntitlementQuota returns early if transaction was already processed idempotently")
    void testDeductIdempotentEarlyReturn() {
        PackageEntitlementAuditLedger existingEntry = PackageEntitlementAuditLedger.builder()
                .transactionId("TX-EXISTING")
                .entitlementId(10L)
                .operationType("DEDUCTION")
                .build();

        when(auditLedgerRepository.findByTransactionIdAndEntitlementId("TX-EXISTING", 10L))
                .thenReturn(List.of(existingEntry));

        EntitlementDeductRequest request = EntitlementDeductRequest.builder()
                .passengerId(101L)
                .transactionId("TX-EXISTING")
                .items(List.of(EntitlementDeductRequest.DeductItem.builder()
                        .entitlementId(10L)
                        .units(1)
                        .build()))
                .build();

        ledgerService.deductEntitlementQuota(request);

        verify(entitlementRepository, never()).findByIdForUpdate(any());
        verify(entitlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("deductEntitlementQuota throws ResourceNotFoundException when entitlement does not exist")
    void testDeductNotFound() {
        when(entitlementRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        EntitlementDeductRequest request = EntitlementDeductRequest.builder()
                .passengerId(101L)
                .transactionId("TX-99")
                .items(List.of(EntitlementDeductRequest.DeductItem.builder()
                        .entitlementId(99L)
                        .units(1)
                        .build()))
                .build();

        assertThatThrownBy(() -> ledgerService.deductEntitlementQuota(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Entitlement not found: 99");
    }

    @Test
    @DisplayName("deductEntitlementQuota throws BusinessRuleException when passenger does not own entitlement")
    void testDeductPassengerMismatch() {
        when(entitlementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entitlement));

        EntitlementDeductRequest request = EntitlementDeductRequest.builder()
                .passengerId(999L) // Wrong passenger
                .transactionId("TX-MISMATCH")
                .items(List.of(EntitlementDeductRequest.DeductItem.builder()
                        .entitlementId(10L)
                        .units(1)
                        .build()))
                .build();

        assertThatThrownBy(() -> ledgerService.deductEntitlementQuota(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Entitlement does not belong to passenger 999");
    }

    @Test
    @DisplayName("deductEntitlementQuota throws BusinessRuleException when status is not ACTIVE")
    void testDeductStatusNotActive() {
        entitlement.setStatus(EntitlementStatus.EXHAUSTED);
        when(entitlementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entitlement));

        EntitlementDeductRequest request = EntitlementDeductRequest.builder()
                .passengerId(101L)
                .transactionId("TX-INACTIVE")
                .items(List.of(EntitlementDeductRequest.DeductItem.builder()
                        .entitlementId(10L)
                        .units(1)
                        .build()))
                .build();

        assertThatThrownBy(() -> ledgerService.deductEntitlementQuota(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Entitlement is not active");
    }

    @Test
    @DisplayName("deductEntitlementQuota throws BusinessRuleException when before validFrom")
    void testDeductBeforeValidFrom() {
        entitlement.setValidFrom(Instant.now().plus(2, ChronoUnit.DAYS));
        when(entitlementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entitlement));

        EntitlementDeductRequest request = EntitlementDeductRequest.builder()
                .passengerId(101L)
                .transactionId("TX-NOT-YET")
                .items(List.of(EntitlementDeductRequest.DeductItem.builder()
                        .entitlementId(10L)
                        .units(1)
                        .build()))
                .build();

        assertThatThrownBy(() -> ledgerService.deductEntitlementQuota(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Entitlement is not yet valid");
    }

    @Test
    @DisplayName("deductEntitlementQuota throws BusinessRuleException when after validTo")
    void testDeductAfterValidTo() {
        entitlement.setValidTo(Instant.now().minus(2, ChronoUnit.DAYS));
        when(entitlementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entitlement));

        EntitlementDeductRequest request = EntitlementDeductRequest.builder()
                .passengerId(101L)
                .transactionId("TX-EXPIRED")
                .items(List.of(EntitlementDeductRequest.DeductItem.builder()
                        .entitlementId(10L)
                        .units(1)
                        .build()))
                .build();

        assertThatThrownBy(() -> ledgerService.deductEntitlementQuota(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Entitlement has expired");
    }

    @Test
    @DisplayName("deductEntitlementQuota throws BusinessRuleException when exceeding quota")
    void testDeductQuotaExceeded() {
        when(entitlementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entitlement));

        EntitlementDeductRequest request = EntitlementDeductRequest.builder()
                .passengerId(101L)
                .transactionId("TX-1234")
                .items(List.of(EntitlementDeductRequest.DeductItem.builder()
                        .entitlementId(10L)
                        .units(5) // only 2 left
                        .build()))
                .build();

        assertThatThrownBy(() -> ledgerService.deductEntitlementQuota(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient quota");
    }

    @Test
    @DisplayName("rollbackEntitlementQuota restores quota and reactivates exhausted package")
    void testRollbackSuccess() {
        entitlement.setUsedQuota(3);
        entitlement.setStatus(EntitlementStatus.EXHAUSTED);
        when(entitlementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entitlement));
        when(auditLedgerRepository.findByTransactionIdAndEntitlementId("TX-1234", 10L))
                .thenReturn(List.of(PackageEntitlementAuditLedger.builder()
                        .entitlementId(10L)
                        .operationType("DEDUCTION")
                        .quotaChanged(2)
                        .build()));

        EntitlementRollbackRequest request = EntitlementRollbackRequest.builder()
                .passengerId(101L)
                .transactionId("TX-1234")
                .items(List.of(EntitlementRollbackRequest.RollbackItem.builder()
                        .entitlementId(10L)
                        .units(1)
                        .reason("Order voided")
                        .build()))
                .build();

        ledgerService.rollbackEntitlementQuota(request);

        assertThat(entitlement.getUsedQuota()).isEqualTo(2);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        verify(entitlementRepository).save(entitlement);
        verify(auditLedgerRepository).save(any(PackageEntitlementAuditLedger.class));
    }

    @Test
    @DisplayName("rollbackEntitlementQuota throws ResourceNotFoundException when entitlement does not exist")
    void testRollbackNotFound() {
        when(entitlementRepository.findByIdForUpdate(88L)).thenReturn(Optional.empty());

        EntitlementRollbackRequest request = EntitlementRollbackRequest.builder()
                .passengerId(101L)
                .items(List.of(EntitlementRollbackRequest.RollbackItem.builder()
                        .entitlementId(88L)
                        .units(1)
                        .build()))
                .build();

        assertThatThrownBy(() -> ledgerService.rollbackEntitlementQuota(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Entitlement not found: 88");
    }

    @Test
    @DisplayName("rollbackEntitlementQuota throws BusinessRuleException when passenger does not match")
    void testRollbackPassengerMismatch() {
        when(entitlementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entitlement));

        EntitlementRollbackRequest request = EntitlementRollbackRequest.builder()
                .passengerId(999L)
                .items(List.of(EntitlementRollbackRequest.RollbackItem.builder()
                        .entitlementId(10L)
                        .units(1)
                        .build()))
                .build();

        assertThatThrownBy(() -> ledgerService.rollbackEntitlementQuota(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Entitlement does not belong to passenger 999");
    }

    @Test
    @DisplayName("rollbackEntitlementQuota skips item when no net prior deductions exist")
    void testRollbackNoPriorDeductions() {
        when(entitlementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entitlement));
        when(auditLedgerRepository.findByTransactionIdAndEntitlementId("TX-EMPTY", 10L))
                .thenReturn(List.of(
                        PackageEntitlementAuditLedger.builder()
                                .operationType("DEDUCTION")
                                .quotaChanged(1)
                                .build(),
                        PackageEntitlementAuditLedger.builder()
                                .operationType("ROLLBACK")
                                .quotaChanged(-1)
                                .build()
                ));

        EntitlementRollbackRequest request = EntitlementRollbackRequest.builder()
                .passengerId(101L)
                .transactionId("TX-EMPTY")
                .items(List.of(EntitlementRollbackRequest.RollbackItem.builder()
                        .entitlementId(10L)
                        .units(1)
                        .build()))
                .build();

        ledgerService.rollbackEntitlementQuota(request);

        // Does not save because netDeducted <= 0
        verify(entitlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("rollbackEntitlementQuota throws BusinessRuleException when unitsToRollback exceeds net deductions")
    void testRollbackExceedsNetDeductions() {
        when(entitlementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entitlement));
        when(auditLedgerRepository.findByTransactionIdAndEntitlementId("TX-1234", 10L))
                .thenReturn(List.of(PackageEntitlementAuditLedger.builder()
                        .operationType("DEDUCTION")
                        .quotaChanged(1)
                        .build()));

        EntitlementRollbackRequest request = EntitlementRollbackRequest.builder()
                .passengerId(101L)
                .transactionId("TX-1234")
                .items(List.of(EntitlementRollbackRequest.RollbackItem.builder()
                        .entitlementId(10L)
                        .units(2) // attempting to rollback 2 when only 1 deducted
                        .build()))
                .build();

        assertThatThrownBy(() -> ledgerService.rollbackEntitlementQuota(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot rollback 2 units; only 1 units were deducted");
    }

    @Test
    @DisplayName("resolvePackageCollision sorts active packages by Priority desc, then validTo asc, then id asc")
    void testResolvePackageCollision() {
        Instant t1 = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant t2 = Instant.now().plus(2, ChronoUnit.DAYS);

        GuestPackageEntitlement pLowPriority = GuestPackageEntitlement.builder()
                .packageName("Low Priority")
                .priorityLevel(1)
                .validTo(t1)
                .build();
        pLowPriority.setId(1L);

        GuestPackageEntitlement pHighEarlier = GuestPackageEntitlement.builder()
                .packageName("High Priority Earlier")
                .priorityLevel(5)
                .validTo(t1)
                .build();
        pHighEarlier.setId(2L);

        GuestPackageEntitlement pHighLater = GuestPackageEntitlement.builder()
                .packageName("High Priority Later")
                .priorityLevel(5)
                .validTo(t2)
                .build();
        pHighLater.setId(3L);

        GuestPackageEntitlement pHighNoExpiry = GuestPackageEntitlement.builder()
                .packageName("High Priority No Expiry")
                .priorityLevel(5)
                .validTo(null)
                .build();
        pHighNoExpiry.setId(4L);

        List<GuestPackageEntitlement> repoList = new ArrayList<>(List.of(pLowPriority, pHighNoExpiry, pHighLater, pHighEarlier));
        when(entitlementRepository.findActiveEntitlementsOrderByPriority(101L)).thenReturn(repoList);

        List<GuestPackageEntitlement> sorted = ledgerService.resolvePackageCollision(101L);

        assertThat(sorted).hasSize(4);
        assertThat(sorted.get(0).getPackageName()).isEqualTo("High Priority Earlier");
        assertThat(sorted.get(1).getPackageName()).isEqualTo("High Priority Later");
        assertThat(sorted.get(2).getPackageName()).isEqualTo("High Priority No Expiry");
        assertThat(sorted.get(3).getPackageName()).isEqualTo("Low Priority");
    }
}

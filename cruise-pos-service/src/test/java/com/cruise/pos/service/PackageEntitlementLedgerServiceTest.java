package com.cruise.pos.service;

import com.cruise.pos.dto.EntitlementDeductRequest;
import com.cruise.pos.dto.EntitlementRollbackRequest;
import com.cruise.pos.entity.GuestPackageEntitlement;
import com.cruise.pos.entity.PackageEntitlementAuditLedger;
import com.cruise.pos.enums.EntitlementStatus;
import com.cruise.pos.enums.PackageType;
import com.cruise.pos.exception.BusinessRuleException;
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
}

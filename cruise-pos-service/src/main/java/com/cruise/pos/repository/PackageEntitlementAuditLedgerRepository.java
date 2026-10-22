package com.cruise.pos.repository;

import com.cruise.pos.entity.PackageEntitlementAuditLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PackageEntitlementAuditLedgerRepository extends JpaRepository<PackageEntitlementAuditLedger, Long> {

    List<PackageEntitlementAuditLedger> findByPassengerIdOrderByTimestampDesc(Long passengerId);

    List<PackageEntitlementAuditLedger> findByEntitlementIdOrderByTimestampDesc(Long entitlementId);

    Page<PackageEntitlementAuditLedger> findAllByOrderByTimestampDesc(Pageable pageable);
}

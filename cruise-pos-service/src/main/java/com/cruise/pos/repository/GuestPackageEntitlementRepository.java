package com.cruise.pos.repository;

import com.cruise.pos.entity.GuestPackageEntitlement;
import com.cruise.pos.enums.EntitlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuestPackageEntitlementRepository extends JpaRepository<GuestPackageEntitlement, Long> {

    List<GuestPackageEntitlement> findByPassengerIdAndStatus(Long passengerId, EntitlementStatus status);

    List<GuestPackageEntitlement> findByPassengerId(Long passengerId);

    @Query("SELECT e FROM GuestPackageEntitlement e WHERE e.passengerId = :passengerId AND e.status = 'ACTIVE' ORDER BY e.priorityLevel DESC, e.id ASC")
    List<GuestPackageEntitlement> findActiveEntitlementsOrderByPriority(@Param("passengerId") Long passengerId);
}

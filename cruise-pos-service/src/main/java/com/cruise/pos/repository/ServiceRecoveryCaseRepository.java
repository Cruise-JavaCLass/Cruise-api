package com.cruise.pos.repository;

import com.cruise.pos.entity.ServiceRecoveryCase;
import com.cruise.pos.enums.ServiceRecoveryCaseStatus;
import com.cruise.pos.enums.ServiceRecoverySeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRecoveryCaseRepository extends JpaRepository<ServiceRecoveryCase, Long> {

    Optional<ServiceRecoveryCase> findByCaseNumber(String caseNumber);

    boolean existsByCaseNumber(String caseNumber);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ServiceRecoveryCase c WHERE c.id = :caseId")
    Optional<ServiceRecoveryCase> findByIdWithLock(@Param("caseId") Long caseId);

    List<ServiceRecoveryCase> findByCabinNumberOrderByCreatedAtDesc(String cabinNumber);

    List<ServiceRecoveryCase> findByPassengerIdOrderByCreatedAtDesc(Long passengerId);

    @Query("SELECT c FROM ServiceRecoveryCase c WHERE " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:severity IS NULL OR c.severity = :severity) AND " +
           "(:search IS NULL OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
           " OR LOWER(c.passengerName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           " OR LOWER(c.cabinNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY c.createdAt DESC")
    Page<ServiceRecoveryCase> searchCases(
            @Param("status") ServiceRecoveryCaseStatus status,
            @Param("severity") ServiceRecoverySeverity severity,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT c FROM ServiceRecoveryCase c WHERE c.status IN ('OPEN', 'UNDER_INVESTIGATION') AND c.slaDueAt < :now")
    List<ServiceRecoveryCase> findOverdueSlaCases(@Param("now") Instant now);
}

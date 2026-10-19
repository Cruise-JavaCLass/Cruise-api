package com.cruise.pos.repository;

import com.cruise.pos.entity.DisputeCase;
import com.cruise.pos.enums.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisputeCaseRepository extends JpaRepository<DisputeCase, Long> {
    Optional<DisputeCase> findByDisputeNumber(String disputeNumber);
    Page<DisputeCase> findByStatus(DisputeStatus status, Pageable pageable);
    Page<DisputeCase> findByCabinNumber(String cabinNumber, Pageable pageable);
    Page<DisputeCase> findByStatusAndCabinNumber(DisputeStatus status, String cabinNumber, Pageable pageable);
    List<DisputeCase> findByTransactionId(String transactionId);
    boolean existsByTransactionIdAndStatusIn(String transactionId, List<DisputeStatus> statuses);
    long countByStatus(DisputeStatus status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT d FROM DisputeCase d WHERE d.id = :id")
    Optional<DisputeCase> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}

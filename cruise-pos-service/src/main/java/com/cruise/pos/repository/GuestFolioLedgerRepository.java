package com.cruise.pos.repository;

import com.cruise.pos.entity.GuestFolioLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface GuestFolioLedgerRepository extends JpaRepository<GuestFolioLedger, Long> {
    Optional<GuestFolioLedger> findByTransactionId(String transactionId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT g FROM GuestFolioLedger g WHERE g.transactionId = :transactionId")
    Optional<GuestFolioLedger> findByTransactionIdForUpdate(@org.springframework.data.repository.query.Param("transactionId") String transactionId);

    Page<GuestFolioLedger> findByCabinNumber(String cabinNumber, Pageable pageable);
    List<GuestFolioLedger> findByCabinNumberOrderByPostedAtDesc(String cabinNumber);
    List<GuestFolioLedger> findByPostedAtBetween(Instant start, Instant end);
    boolean existsByTransactionId(String transactionId);
}

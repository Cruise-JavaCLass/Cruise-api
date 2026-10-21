package com.cruise.pos.repository;

import com.cruise.pos.entity.GuestFolioLedger;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface GuestFolioLedgerRepository extends JpaRepository<GuestFolioLedger, Long> {

    Optional<GuestFolioLedger> findByTransactionId(String transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GuestFolioLedger g WHERE g.transactionId = :transactionId")
    Optional<GuestFolioLedger> findByTransactionIdForUpdate(@Param("transactionId") String transactionId);

    Page<GuestFolioLedger> findByCabinNumber(String cabinNumber, Pageable pageable);

    List<GuestFolioLedger> findByCabinNumberOrderByPostedAtAsc(String cabinNumber);

    List<GuestFolioLedger> findByCabinNumberOrderByPostedAtDesc(String cabinNumber);

    List<GuestFolioLedger> findByCabinNumberAndServiceAreaOrderByPostedAtAsc(String cabinNumber, String serviceArea);

    List<GuestFolioLedger> findByPostedAtBetween(Instant start, Instant end);

    boolean existsByTransactionId(String transactionId);
}

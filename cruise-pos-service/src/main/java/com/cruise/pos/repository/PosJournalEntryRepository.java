package com.cruise.pos.repository;

import com.cruise.pos.entity.PosJournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PosJournalEntryRepository extends JpaRepository<PosJournalEntry, Long> {
    Optional<PosJournalEntry> findByPosTransactionId(String posTransactionId);
    boolean existsByPosTransactionId(String posTransactionId);
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<PosJournalEntry> findByTransactionTimeBetween(Instant start, Instant end);
    List<PosJournalEntry> findByIsSyncedFalse();
}

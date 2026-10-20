package com.cruise.pos.service;

import com.cruise.pos.entity.GuestFolioLedger;
import com.cruise.pos.entity.PosJournalEntry;
import com.cruise.pos.repository.GuestFolioLedgerRepository;
import com.cruise.pos.repository.PosJournalEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper writer that persists a single POS journal entry and its corresponding
 * folio ledger in an isolated REQUIRES_NEW transaction. This ensures that any
 * database constraint violation (duplicate transaction or idempotency key) is isolated
 * and does not mark the caller batch's transaction as rollback-only.
 */
@Service
@RequiredArgsConstructor
public class PosJournalItemWriter {

    private final PosJournalEntryRepository journalRepository;
    private final GuestFolioLedgerRepository folioRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeItem(PosJournalEntry entry, GuestFolioLedger ledger) {
        journalRepository.saveAndFlush(entry);
        if (ledger != null && !folioRepository.existsByTransactionId(entry.getPosTransactionId())) {
            folioRepository.saveAndFlush(ledger);
        }
    }
}

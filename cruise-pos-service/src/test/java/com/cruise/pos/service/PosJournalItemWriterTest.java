package com.cruise.pos.service;

import com.cruise.pos.entity.GuestFolioLedger;
import com.cruise.pos.entity.PosJournalEntry;
import com.cruise.pos.repository.GuestFolioLedgerRepository;
import com.cruise.pos.repository.PosJournalEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosJournalItemWriterTest {

    @Mock
    private PosJournalEntryRepository journalRepository;

    @Mock
    private GuestFolioLedgerRepository folioRepository;

    @InjectMocks
    private PosJournalItemWriter itemWriter;

    @Test
    @DisplayName("Lưu cả POS Journal và Guest Folio Ledger thành công")
    void testWriteItem_WithLedger() {
        PosJournalEntry entry = PosJournalEntry.builder()
                .posTransactionId("TX-100")
                .amount(new BigDecimal("20.00"))
                .transactionTime(Instant.now())
                .build();

        GuestFolioLedger ledger = GuestFolioLedger.builder()
                .transactionId("TX-100")
                .amount(new BigDecimal("20.00"))
                .build();

        when(folioRepository.existsByTransactionId("TX-100")).thenReturn(false);

        itemWriter.writeItem(entry, ledger);

        verify(journalRepository, times(1)).saveAndFlush(entry);
        verify(folioRepository, times(1)).saveAndFlush(ledger);
    }

    @Test
    @DisplayName("Chỉ lưu POS Journal khi Ledger đã tồn tại")
    void testWriteItem_WhenLedgerAlreadyExists() {
        PosJournalEntry entry = PosJournalEntry.builder()
                .posTransactionId("TX-200")
                .amount(new BigDecimal("30.00"))
                .transactionTime(Instant.now())
                .build();

        GuestFolioLedger ledger = GuestFolioLedger.builder()
                .transactionId("TX-200")
                .amount(new BigDecimal("30.00"))
                .build();

        when(folioRepository.existsByTransactionId("TX-200")).thenReturn(true);

        itemWriter.writeItem(entry, ledger);

        verify(journalRepository, times(1)).saveAndFlush(entry);
        verify(folioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Lưu POS Journal khi ledger là null")
    void testWriteItem_WhenLedgerIsNull() {
        PosJournalEntry entry = PosJournalEntry.builder()
                .posTransactionId("TX-300")
                .amount(new BigDecimal("40.00"))
                .transactionTime(Instant.now())
                .build();

        itemWriter.writeItem(entry, null);

        verify(journalRepository, times(1)).saveAndFlush(entry);
        verify(folioRepository, never()).saveAndFlush(any());
    }
}

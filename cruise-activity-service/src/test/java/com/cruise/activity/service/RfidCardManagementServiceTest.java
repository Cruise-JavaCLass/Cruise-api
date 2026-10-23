package com.cruise.activity.service;

import com.cruise.activity.dto.BlacklistRfidCardRequest;
import com.cruise.activity.dto.BlacklistRfidCardResponse;
import com.cruise.activity.dto.BulkRfidIngestionRequest;
import com.cruise.activity.dto.BulkRfidIngestionResponse;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.entity.RfidCardInventory;
import com.cruise.activity.enums.RfidCardStatus;
import com.cruise.activity.repository.PassengerRepository;
import com.cruise.activity.repository.RfidCardInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RfidCardManagementServiceTest {

    @Mock
    private RfidCardInventoryRepository rfidRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @InjectMocks
    private RfidCardManagementService rfidService;

    private Passenger passenger;

    @BeforeEach
    void setUp() {
        passenger = Passenger.builder()
                .tourId(10L)
                .fullName("Bruce Wayne")
                .cabinNumber("SUITE-007")
                .rfidTag("UID-BW-777")
                .active(true)
                .build();
        passenger.setId(88L);
    }

    @Test
    @DisplayName("ingestBlankCardBatch: ignores duplicates and ingests new cards")
    void ingestBlankCardBatch_Success() {
        when(rfidRepository.findByCardUid("UID-NEW-01")).thenReturn(Optional.empty());
        when(rfidRepository.findByCardUid("UID-EXISTING-02")).thenReturn(Optional.of(new RfidCardInventory()));

        BulkRfidIngestionRequest req = BulkRfidIngestionRequest.builder()
                .batchNumber("BATCH-TEST-001")
                .cardUids(List.of("UID-NEW-01", "UID-EXISTING-02"))
                .build();

        BulkRfidIngestionResponse res = rfidService.ingestBlankCardBatch(req);

        assertThat(res).isNotNull();
        assertThat(res.getTotalSubmitted()).isEqualTo(2);
        assertThat(res.getSuccessfullyIngested()).isEqualTo(1);
        assertThat(res.getDuplicateSkipped()).isEqualTo(1);
        assertThat(res.getSkippedUids()).contains("UID-EXISTING-02");
        verify(rfidRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("blacklistCard: quarantines card and auto-pairs replacement card for passenger")
    void blacklistCard_WithReplacementPairing() {
        RfidCardInventory oldCard = RfidCardInventory.builder()
                .cardUid("UID-BW-777")
                .status(RfidCardStatus.ISSUED)
                .assignedPassengerId(88L)
                .build();

        when(rfidRepository.findByCardUid("UID-BW-777")).thenReturn(Optional.of(oldCard));
        when(passengerRepository.findByRfidTag("UID-BW-777")).thenReturn(Optional.of(passenger));
        when(rfidRepository.findByCardUid("UID-BW-888-NEW")).thenReturn(Optional.empty());

        BlacklistRfidCardRequest request = BlacklistRfidCardRequest.builder()
                .cardUid("UID-BW-777")
                .reason("GUEST_REPORTED_LOST")
                .reportedBy("Reception Desk")
                .replacementCardUid("UID-BW-888-NEW")
                .build();

        BlacklistRfidCardResponse response = rfidService.blacklistCard(request);

        assertThat(response).isNotNull();
        assertThat(response.getCardUid()).isEqualTo("UID-BW-777");
        assertThat(response.getStatus()).isEqualTo(RfidCardStatus.BLACKLISTED);
        assertThat(response.isPosBlacklistBroadcasted()).isFalse();
        assertThat(response.getAffectedPassengerId()).isEqualTo(88L);
        assertThat(response.getReplacementCardUid()).isEqualTo("UID-BW-888-NEW");

        // Verify old card blacklisted and new card issued
        assertThat(oldCard.getStatus()).isEqualTo(RfidCardStatus.BLACKLISTED);
        assertThat(passenger.getRfidTag()).isEqualTo("UID-BW-888-NEW");
        verify(passengerRepository, times(1)).save(passenger);
    }

    @Test
    @DisplayName("ingestBlankCardBatch: skips in-batch duplicate UIDs and blank entries")
    void ingestBlankCardBatch_InBatchDuplicatesAndBlanks() {
        when(rfidRepository.findByCardUid("UID-UNIQUE-1")).thenReturn(Optional.empty());

        BulkRfidIngestionRequest req = BulkRfidIngestionRequest.builder()
                .batchNumber("BATCH-TEST-002")
                .cardUids(List.of("UID-UNIQUE-1", "UID-UNIQUE-1", "", "   "))
                .build();

        BulkRfidIngestionResponse res = rfidService.ingestBlankCardBatch(req);

        assertThat(res.getTotalSubmitted()).isEqualTo(4);
        assertThat(res.getSuccessfullyIngested()).isEqualTo(1);
        assertThat(res.getDuplicateSkipped()).isEqualTo(3);
    }

    @Test
    @DisplayName("blacklistCard: throws exception when replacement card equals blacklisted card")
    void blacklistCard_SameReplacementCard_ThrowsException() {
        RfidCardInventory oldCard = RfidCardInventory.builder()
                .cardUid("UID-SAME-1")
                .status(RfidCardStatus.ISSUED)
                .assignedPassengerId(88L)
                .build();

        when(rfidRepository.findByCardUid("UID-SAME-1")).thenReturn(Optional.of(oldCard));
        when(passengerRepository.findByRfidTag("UID-SAME-1")).thenReturn(Optional.of(passenger));

        BlacklistRfidCardRequest request = BlacklistRfidCardRequest.builder()
                .cardUid("UID-SAME-1")
                .reason("LOST")
                .replacementCardUid("UID-SAME-1")
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> rfidService.blacklistCard(request))
                .isInstanceOf(com.cruise.activity.exception.BusinessRuleException.class)
                .hasMessageContaining("Replacement card must differ");
    }

    @Test
    @DisplayName("blacklistCard: throws exception when replacement card is not available")
    void blacklistCard_ReplacementCardNotAvailable_ThrowsException() {
        RfidCardInventory oldCard = RfidCardInventory.builder()
                .cardUid("UID-OLD-1")
                .status(RfidCardStatus.ISSUED)
                .assignedPassengerId(88L)
                .build();

        RfidCardInventory unavailableCard = RfidCardInventory.builder()
                .cardUid("UID-BUSY-2")
                .status(RfidCardStatus.ISSUED)
                .build();
        unavailableCard.setId(999L);

        when(rfidRepository.findByCardUid("UID-OLD-1")).thenReturn(Optional.of(oldCard));
        when(passengerRepository.findByRfidTag("UID-OLD-1")).thenReturn(Optional.of(passenger));
        when(rfidRepository.findByCardUid("UID-BUSY-2")).thenReturn(Optional.of(unavailableCard));

        BlacklistRfidCardRequest request = BlacklistRfidCardRequest.builder()
                .cardUid("UID-OLD-1")
                .reason("LOST")
                .replacementCardUid("UID-BUSY-2")
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> rfidService.blacklistCard(request))
                .isInstanceOf(com.cruise.activity.exception.BusinessRuleException.class)
                .hasMessageContaining("Replacement card is not available");
    }
}

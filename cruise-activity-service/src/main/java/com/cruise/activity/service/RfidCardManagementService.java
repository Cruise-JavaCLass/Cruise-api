package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.entity.RfidCardInventory;
import com.cruise.activity.enums.RfidCardStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.PassengerRepository;
import com.cruise.activity.repository.RfidCardInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RfidCardManagementService {

    private final RfidCardInventoryRepository rfidRepository;
    private final PassengerRepository passengerRepository;

    @Transactional
    public BulkRfidIngestionResponse ingestBlankCardBatch(BulkRfidIngestionRequest request) {
        log.info("Ingesting RFID card batch: batchNumber={}, count={}",
                request.getBatchNumber(), request.getCardUids().size());

        List<String> skipped = new ArrayList<>();
        List<RfidCardInventory> toSave = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String rawUid : request.getCardUids()) {
            if (rawUid == null || rawUid.isBlank()) {
                skipped.add("BLANK_ENTRY");
                continue;
            }
            String uid = rawUid.trim().toUpperCase();
            if (!seen.add(uid)) {
                skipped.add(uid);
                continue;
            }

            Optional<RfidCardInventory> existing = rfidRepository.findByCardUid(uid);
            if (existing.isPresent()) {
                skipped.add(uid);
            } else {
                toSave.add(RfidCardInventory.builder()
                        .cardUid(uid)
                        .cardType(request.getCardType() != null ? request.getCardType() : "MIFARE_CLASSIC_1K")
                        .batchNumber(request.getBatchNumber())
                        .status(RfidCardStatus.AVAILABLE)
                        .notes(request.getNotes())
                        .build());
            }
        }

        if (!toSave.isEmpty()) {
            try {
                rfidRepository.saveAll(toSave);
            } catch (DataIntegrityViolationException ex) {
                log.warn("Concurrent duplicate card UID detected during batch save: {}", ex.getMessage());
                throw new BusinessRuleException("Card UID already exists in inventory");
            }
        }

        return BulkRfidIngestionResponse.builder()
                .batchNumber(request.getBatchNumber())
                .totalSubmitted(request.getCardUids().size())
                .successfullyIngested(toSave.size())
                .duplicateSkipped(skipped.size())
                .skippedUids(skipped)
                .build();
    }

    @Transactional
    public BlacklistRfidCardResponse blacklistCard(BlacklistRfidCardRequest request) {
        String uid = request.getCardUid().trim().toUpperCase();
        log.warn("Blacklisting RFID card: uid={}, reason={}", uid, request.getReason());

        RfidCardInventory card = rfidRepository.findByCardUid(uid)
                .orElseGet(() -> {
                    // Auto-register untracked card into inventory as blacklisted
                    return RfidCardInventory.builder()
                            .cardUid(uid)
                            .batchNumber("EMERGENCY-QUARANTINE")
                            .build();
                });

        card.setStatus(RfidCardStatus.BLACKLISTED);
        card.setBlacklistedAt(Instant.now());
        card.setBlacklistReason(request.getReason());
        card.setBlacklistedBy(request.getReportedBy() != null ? request.getReportedBy() : "Security / Front Desk");

        Long affectedPassengerId = card.getAssignedPassengerId();
        String replacementUid = null;

        // If this card is currently assigned to a passenger in Passenger entity (match all case variants)
        List<Passenger> passengers = passengerRepository.findAllByRfidTagIgnoreCase(uid);
        boolean hasReplacement = request.getReplacementCardUid() != null && !request.getReplacementCardUid().trim().isBlank();
        for (int i = 0; i < passengers.size(); i++) {
            Passenger passenger = passengers.get(i);
            if (i == 0) {
                affectedPassengerId = passenger.getId();
            }
            if (i == 0 && hasReplacement) {
                replacementUid = request.getReplacementCardUid().trim().toUpperCase();
                pairReplacementCard(passenger, card, replacementUid);
            } else {
                // Clear RFID from passenger to prevent unauthorized charges
                passenger.setRfidTag(null);
                passengerRepository.save(passenger);
            }
        }

        rfidRepository.save(card);

        // Broadcast to POS & Terminal networks
        broadcastCardBlacklistToReaders(uid, request.getReason());

        return BlacklistRfidCardResponse.builder()
                .cardUid(uid)
                .status(RfidCardStatus.BLACKLISTED)
                .blacklistedAt(card.getBlacklistedAt())
                .reason(request.getReason())
                .posBlacklistBroadcasted(false)
                .affectedPassengerId(affectedPassengerId)
                .replacementCardUid(replacementUid)
                .message("Card quarantined & blacklisted locally; reader sync not performed.")
                .build();
    }

    @Transactional(readOnly = true)
    public RfidInventoryDashboardDto getDashboardMetrics() {
        long available = rfidRepository.countByStatus(RfidCardStatus.AVAILABLE);
        long issued = rfidRepository.countByStatus(RfidCardStatus.ISSUED);
        long blacklisted = rfidRepository.countByStatus(RfidCardStatus.BLACKLISTED);
        long lost = rfidRepository.countByStatus(RfidCardStatus.LOST_REPORTED);
        long damaged = rfidRepository.countByStatus(RfidCardStatus.DAMAGED);
        long total = rfidRepository.count();

        List<RfidCardInventory> recentCards = rfidRepository.findAll(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        List<String> activeBlacklist = rfidRepository.findAllActiveBlacklistedUids();

        return RfidInventoryDashboardDto.builder()
                .totalCards(total)
                .availableCards(available)
                .issuedCards(issued)
                .lostOrBlacklistedCards(blacklisted + lost)
                .damagedCards(damaged)
                .recentCards(recentCards)
                .activeBlacklistUids(activeBlacklist)
                .build();
    }

    @Transactional(readOnly = true)
    public List<String> getActiveBlacklistedUids() {
        return rfidRepository.findAllActiveBlacklistedUids();
    }

    private void pairReplacementCard(Passenger passenger, RfidCardInventory oldCard, String newCardUid) {
        if (newCardUid.equals(oldCard.getCardUid())) {
            throw new BusinessRuleException("Replacement card must differ from the blacklisted card");
        }
        RfidCardInventory newCard = rfidRepository.findByCardUid(newCardUid)
                .orElseGet(() -> RfidCardInventory.builder()
                        .cardUid(newCardUid)
                        .batchNumber("REPLACEMENT-ONBOARD")
                        .build());

        if (newCard.getId() != null && newCard.getStatus() != RfidCardStatus.AVAILABLE) {
            throw new BusinessRuleException("Replacement card is not available: status=" + newCard.getStatus());
        }

        newCard.setStatus(RfidCardStatus.ISSUED);
        newCard.setAssignedPassengerId(passenger.getId());
        newCard.setAssignedPassengerName(passenger.getFullName());
        newCard.setAssignedCabinNumber(passenger.getCabinNumber());
        newCard.setIssuedAt(Instant.now());
        rfidRepository.save(newCard);

        passenger.setRfidTag(newCardUid);
        passengerRepository.save(passenger);

        log.info("Paired replacement RFID card: passengerId={}, oldUid={}, newUid={}",
                passenger.getId(), oldCard.getCardUid(), newCardUid);
    }

    private void broadcastCardBlacklistToReaders(String cardUid, String reason) {
        // High-speed broadcast log - in prod connects to Redis PUB/SUB or POS sync gateway
        log.warn("BROADCASTING RFID BLACKLIST TO ALL POS READERS: cardUid={}, reason={}", cardUid, reason);
    }
}

package com.cruise.activity.service;

import com.cruise.activity.dto.SolasMusterCheckinRequest;
import com.cruise.activity.dto.SolasMusterIncidentDto;
import com.cruise.activity.entity.SolasMusterCheckin;
import com.cruise.activity.entity.SolasMusterIncident;
import com.cruise.activity.enums.MusterStatus;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.PassengerRepository;
import com.cruise.activity.repository.SolasMusterCheckinRepository;
import com.cruise.activity.repository.SolasMusterIncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolasMusterEmergencyService {

    private final SolasMusterIncidentRepository incidentRepository;
    private final SolasMusterCheckinRepository checkinRepository;
    private final PassengerRepository passengerRepository;

    @Transactional
    public SolasMusterIncidentDto triggerEmergencyMuster(SolasMusterIncidentDto dto) {
        String incidentCode = "SOLAS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        int totalSouls = (dto.getTotalSoulsOnBoard() != null && dto.getTotalSoulsOnBoard() > 0)
                ? dto.getTotalSoulsOnBoard()
                : (int) passengerRepository.count();

        SolasMusterIncident incident = SolasMusterIncident.builder()
                .incidentCode(incidentCode)
                .emergencyType(dto.getEmergencyType())
                .status(MusterStatus.ACTIVE_MUSTER)
                .triggeredBy(dto.getTriggeredBy())
                .announcementText(dto.getAnnouncementText())
                .acousticSignalCode(dto.getAcousticSignalCode() != null ? dto.getAcousticSignalCode() : "7_SHORT_1_LONG")
                .totalSoulsOnBoard(totalSouls)
                .musteredSoulsCount(0)
                .triggeredAt(Instant.now())
                .build();

        incident = incidentRepository.save(incident);
        log.warn("🚨 SOLAS EMERGENCY MUSTER TRIGGERED! Code={}, Type={}, Officer={}, TotalSouls={}",
                incidentCode, dto.getEmergencyType(), dto.getTriggeredBy(), totalSouls);

        return mapIncidentToDto(incident);
    }

    @Transactional
    public SolasMusterCheckinRequest getCheckinDetails(Long checkinId) {
        return null;
    }

    @Transactional
    public SolasMusterIncidentDto checkinPassengerToMusterStation(SolasMusterCheckinRequest req) {
        SolasMusterIncident incident = incidentRepository.findByIdForUpdate(req.getIncidentId())
                .orElseThrow(() -> new ResourceNotFoundException("Muster incident not found: " + req.getIncidentId()));

        if (incident.getStatus() != MusterStatus.ACTIVE_MUSTER && incident.getStatus() != MusterStatus.DRILL_IN_PROGRESS) {
            throw new IllegalStateException("Muster incident is not active (current status: " + incident.getStatus() + ")");
        }

        if (!passengerRepository.existsById(req.getPassengerId())) {
            throw new ResourceNotFoundException("Passenger not found: " + req.getPassengerId());
        }

        Optional<SolasMusterCheckin> existing = checkinRepository.findByIncidentIdAndPassengerId(req.getIncidentId(), req.getPassengerId());
        if (existing.isEmpty()) {
            SolasMusterCheckin checkin = SolasMusterCheckin.builder()
                    .incidentId(req.getIncidentId())
                    .passengerId(req.getPassengerId())
                    .musterStationCode(req.getMusterStationCode().toUpperCase())
                    .lifejacketVerified(Boolean.TRUE.equals(req.getLifejacketVerified()))
                    .checkedInBy(req.getCheckedInBy() != null ? req.getCheckedInBy() : "RFID_GATE")
                    .checkedInAt(Instant.now())
                    .build();

            try {
                checkinRepository.saveAndFlush(checkin);
            } catch (DataIntegrityViolationException e) {
                log.info("Passenger {} already checked in concurrently for incident {}", req.getPassengerId(), req.getIncidentId());
            }

            int currentCount = (int) checkinRepository.countByIncidentId(incident.getId());
            incident.setMusteredSoulsCount(currentCount);
            incident = incidentRepository.save(incident);
            log.info("Passenger {} checked in at muster station {}. Progress: {}/{}",
                    req.getPassengerId(), req.getMusterStationCode(), currentCount, incident.getTotalSoulsOnBoard());
        }

        return mapIncidentToDto(incident);
    }

    @Transactional(readOnly = true)
    public Optional<SolasMusterIncidentDto> getActiveMusterIncident() {
        return incidentRepository.findFirstByStatusInOrderByCreatedAtDesc(
                List.of(MusterStatus.ACTIVE_MUSTER, MusterStatus.DRILL_IN_PROGRESS)
        ).map(this::mapIncidentToDto);
    }

    @Transactional
    public SolasMusterIncidentDto setAllClear(Long incidentId) {
        SolasMusterIncident incident = incidentRepository.findByIdForUpdate(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Muster incident not found: " + incidentId));

        if (incident.getStatus() != MusterStatus.ACTIVE_MUSTER && incident.getStatus() != MusterStatus.DRILL_IN_PROGRESS) {
            throw new IllegalStateException("Cannot declare ALL_CLEAR for incident in status: " + incident.getStatus());
        }

        incident.setStatus(MusterStatus.ALL_CLEAR);
        incident.setAllClearAt(Instant.now());
        incident = incidentRepository.save(incident);
        log.info("SOLAS Emergency Incident {} marked as ALL_CLEAR", incident.getIncidentCode());

        return mapIncidentToDto(incident);
    }

    private SolasMusterIncidentDto mapIncidentToDto(SolasMusterIncident e) {
        List<SolasMusterCheckin> checkins = checkinRepository.findByIncidentId(e.getId());
        Map<String, Long> stationCounts = checkins.stream()
                .collect(Collectors.groupingBy(SolasMusterCheckin::getMusterStationCode, Collectors.counting()));

        double rate = e.getTotalSoulsOnBoard() > 0
                ? Math.round(((double) e.getMusteredSoulsCount() / e.getTotalSoulsOnBoard()) * 1000.0) / 10.0
                : 0.0;

        return SolasMusterIncidentDto.builder()
                .id(e.getId())
                .incidentCode(e.getIncidentCode())
                .emergencyType(e.getEmergencyType())
                .status(e.getStatus())
                .triggeredBy(e.getTriggeredBy())
                .announcementText(e.getAnnouncementText())
                .acousticSignalCode(e.getAcousticSignalCode())
                .totalSoulsOnBoard(e.getTotalSoulsOnBoard())
                .musteredSoulsCount(e.getMusteredSoulsCount())
                .musterCompletionRate(rate)
                .triggeredAt(e.getTriggeredAt())
                .allClearAt(e.getAllClearAt())
                .headCountByStation(stationCounts)
                .build();
    }
}

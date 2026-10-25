package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.ShipDeckZoneDto;
import com.cruise.itinerary.dto.SystemCancellationPolicyDto;
import com.cruise.itinerary.entity.ShipDeckZone;
import com.cruise.itinerary.entity.SystemCancellationPolicy;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.repository.CruiseShipRepository;
import com.cruise.itinerary.repository.ShipDeckZoneRepository;
import com.cruise.itinerary.repository.SystemCancellationPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruiseAreaPolicyService {

    private final ShipDeckZoneRepository deckZoneRepository;
    private final SystemCancellationPolicyRepository cancellationPolicyRepository;
    private final CruiseShipRepository shipRepository;

    @Transactional(readOnly = true)
    public List<ShipDeckZoneDto> getDeckZonesByShip(Long shipId) {
        return deckZoneRepository.findByShipIdOrderByDeckNumberAsc(shipId).stream()
                .map(this::mapDeckZoneToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ShipDeckZoneDto createDeckZone(ShipDeckZoneDto dto) {
        if (!shipRepository.existsById(dto.getShipId())) {
            throw new ResourceNotFoundException("Tàu không tồn tại với ID: " + dto.getShipId());
        }

        ShipDeckZone entity = ShipDeckZone.builder()
                .shipId(dto.getShipId())
                .deckNumber(dto.getDeckNumber())
                .zoneName(dto.getZoneName())
                .zoneCode(dto.getZoneCode())
                .maxSafetyCapacity(dto.getMaxSafetyCapacity())
                .zoneType(dto.getZoneType())
                .hasLifeboatAccess(Boolean.TRUE.equals(dto.getHasLifeboatAccess()))
                .isRestricted(Boolean.TRUE.equals(dto.getIsRestricted()))
                .build();

        ShipDeckZone saved = deckZoneRepository.save(entity);
        log.info("Created ship deck zone: id={}, shipId={}, deck={}, zone={}", saved.getId(), saved.getShipId(), saved.getDeckNumber(), saved.getZoneName());
        return mapDeckZoneToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SystemCancellationPolicyDto> getCancellationPolicies(String serviceType) {
        List<SystemCancellationPolicy> policies = (serviceType != null && !serviceType.isBlank())
                ? cancellationPolicyRepository.findByServiceTypeAndIsActiveTrueOrderByHoursBeforeCutoffDesc(serviceType.trim().toUpperCase(Locale.ROOT))
                : cancellationPolicyRepository.findAll();

        return policies.stream()
                .map(this::mapPolicyToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SystemCancellationPolicyDto createCancellationPolicy(SystemCancellationPolicyDto dto) {
        SystemCancellationPolicy entity = SystemCancellationPolicy.builder()
                .policyName(dto.getPolicyName())
                .serviceType(dto.getServiceType().trim().toUpperCase(Locale.ROOT))
                .hoursBeforeCutoff(dto.getHoursBeforeCutoff())
                .refundPercentage(dto.getRefundPercentage())
                .cancellationFee(dto.getCancellationFee() != null ? dto.getCancellationFee() : BigDecimal.ZERO)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .description(dto.getDescription())
                .build();

        SystemCancellationPolicy saved = cancellationPolicyRepository.save(entity);
        log.info("Created cancellation policy: id={}, name={}, service={}", saved.getId(), saved.getPolicyName(), saved.getServiceType());
        return mapPolicyToDto(saved);
    }

    private ShipDeckZoneDto mapDeckZoneToDto(ShipDeckZone e) {
        return ShipDeckZoneDto.builder()
                .id(e.getId())
                .shipId(e.getShipId())
                .deckNumber(e.getDeckNumber())
                .zoneName(e.getZoneName())
                .zoneCode(e.getZoneCode())
                .maxSafetyCapacity(e.getMaxSafetyCapacity())
                .zoneType(e.getZoneType())
                .hasLifeboatAccess(e.getHasLifeboatAccess())
                .isRestricted(e.getIsRestricted())
                .build();
    }

    private SystemCancellationPolicyDto mapPolicyToDto(SystemCancellationPolicy e) {
        return SystemCancellationPolicyDto.builder()
                .id(e.getId())
                .policyName(e.getPolicyName())
                .serviceType(e.getServiceType())
                .hoursBeforeCutoff(e.getHoursBeforeCutoff())
                .refundPercentage(e.getRefundPercentage())
                .cancellationFee(e.getCancellationFee())
                .isActive(e.getIsActive())
                .description(e.getDescription())
                .build();
    }
}

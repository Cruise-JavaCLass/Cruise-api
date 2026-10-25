package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.ShipDeckZoneDto;
import com.cruise.itinerary.dto.SystemCancellationPolicyDto;
import com.cruise.itinerary.entity.ShipDeckZone;
import com.cruise.itinerary.entity.SystemCancellationPolicy;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.repository.CruiseShipRepository;
import com.cruise.itinerary.repository.ShipDeckZoneRepository;
import com.cruise.itinerary.repository.SystemCancellationPolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CruiseAreaPolicyService Unit Tests (KAN-282)")
class CruiseAreaPolicyServiceTest {

    @Mock
    private ShipDeckZoneRepository deckZoneRepository;

    @Mock
    private SystemCancellationPolicyRepository cancellationPolicyRepository;

    @Mock
    private CruiseShipRepository shipRepository;

    @InjectMocks
    private CruiseAreaPolicyService policyService;

    @Test
    @DisplayName("getDeckZonesByShip returns mapped deck zone DTOs")
    void testGetDeckZonesByShip() {
        ShipDeckZone zone = ShipDeckZone.builder()
                .shipId(10L)
                .deckNumber(7)
                .zoneName("Pool Deck")
                .zoneCode("DECK7-POOL")
                .maxSafetyCapacity(350)
                .zoneType("POOL")
                .hasLifeboatAccess(true)
                .isRestricted(false)
                .build();
        zone.setId(101L);

        when(deckZoneRepository.findByShipIdOrderByDeckNumberAsc(10L)).thenReturn(List.of(zone));

        List<ShipDeckZoneDto> result = policyService.getDeckZonesByShip(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(101L);
        assertThat(result.get(0).getZoneName()).isEqualTo("Pool Deck");
        assertThat(result.get(0).getMaxSafetyCapacity()).isEqualTo(350);
        assertThat(result.get(0).getHasLifeboatAccess()).isTrue();
    }

    @Test
    @DisplayName("createDeckZone saves and returns DTO when ship exists")
    void testCreateDeckZone_Success() {
        ShipDeckZoneDto input = ShipDeckZoneDto.builder()
                .shipId(5L)
                .deckNumber(3)
                .zoneName("Promenade Aft")
                .zoneCode("DECK3-PROM-AFT")
                .maxSafetyCapacity(200)
                .zoneType("PROMENADE")
                .hasLifeboatAccess(true)
                .isRestricted(false)
                .build();

        when(shipRepository.existsById(5L)).thenReturn(true);
        when(deckZoneRepository.save(any(ShipDeckZone.class))).thenAnswer(invocation -> {
            ShipDeckZone entity = invocation.getArgument(0);
            entity.setId(102L);
            return entity;
        });

        ShipDeckZoneDto created = policyService.createDeckZone(input);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(102L);
        assertThat(created.getZoneCode()).isEqualTo("DECK3-PROM-AFT");
        assertThat(created.getHasLifeboatAccess()).isTrue();
        assertThat(created.getIsRestricted()).isFalse();

        verify(shipRepository).existsById(5L);
        verify(deckZoneRepository).save(any(ShipDeckZone.class));
    }

    @Test
    @DisplayName("createDeckZone throws ResourceNotFoundException when ship does not exist")
    void testCreateDeckZone_ShipNotFound() {
        ShipDeckZoneDto input = ShipDeckZoneDto.builder()
                .shipId(999L)
                .deckNumber(1)
                .zoneName("Unknown Deck")
                .zoneCode("UNKNOWN")
                .maxSafetyCapacity(50)
                .zoneType("CABIN")
                .build();

        when(shipRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> policyService.createDeckZone(input))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");

        verify(deckZoneRepository, never()).save(any());
    }

    @Test
    @DisplayName("getCancellationPolicies with specific serviceType normalizes with Locale.ROOT")
    void testGetCancellationPolicies_WithServiceType() {
        SystemCancellationPolicy policy = SystemCancellationPolicy.builder()
                .policyName("Excursion Policy")
                .serviceType("EXCURSION")
                .hoursBeforeCutoff(24)
                .refundPercentage(100)
                .cancellationFee(BigDecimal.ZERO)
                .isActive(true)
                .description("Full refund 24h before")
                .build();
        policy.setId(201L);

        when(cancellationPolicyRepository.findByServiceTypeAndIsActiveTrueOrderByHoursBeforeCutoffDesc("EXCURSION"))
                .thenReturn(List.of(policy));

        List<SystemCancellationPolicyDto> result = policyService.getCancellationPolicies("  excursion  ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getServiceType()).isEqualTo("EXCURSION");
        assertThat(result.get(0).getRefundPercentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("getCancellationPolicies with null or blank serviceType returns all policies")
    void testGetCancellationPolicies_BlankServiceType() {
        SystemCancellationPolicy policy1 = SystemCancellationPolicy.builder()
                .policyName("Dining Policy")
                .serviceType("DINING")
                .hoursBeforeCutoff(12)
                .refundPercentage(80)
                .cancellationFee(BigDecimal.TEN)
                .isActive(true)
                .build();
        policy1.setId(202L);

        when(cancellationPolicyRepository.findAll()).thenReturn(List.of(policy1));

        List<SystemCancellationPolicyDto> resultNull = policyService.getCancellationPolicies(null);
        List<SystemCancellationPolicyDto> resultBlank = policyService.getCancellationPolicies("   ");

        assertThat(resultNull).hasSize(1);
        assertThat(resultBlank).hasSize(1);
        assertThat(resultNull.get(0).getPolicyName()).isEqualTo("Dining Policy");
    }

    @Test
    @DisplayName("createCancellationPolicy saves with normalized serviceType and default fallbacks")
    void testCreateCancellationPolicy_Defaults() {
        SystemCancellationPolicyDto input = SystemCancellationPolicyDto.builder()
                .policyName("Spa Standard")
                .serviceType(" spa ")
                .hoursBeforeCutoff(48)
                .refundPercentage(90)
                .cancellationFee(null)
                .isActive(null)
                .description(null)
                .build();

        when(cancellationPolicyRepository.save(any(SystemCancellationPolicy.class))).thenAnswer(invocation -> {
            SystemCancellationPolicy entity = invocation.getArgument(0);
            entity.setId(301L);
            return entity;
        });

        SystemCancellationPolicyDto created = policyService.createCancellationPolicy(input);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(301L);
        assertThat(created.getServiceType()).isEqualTo("SPA");
        assertThat(created.getCancellationFee()).isEqualTo(BigDecimal.ZERO);
        assertThat(created.getIsActive()).isTrue();

        verify(cancellationPolicyRepository).save(any(SystemCancellationPolicy.class));
    }
}

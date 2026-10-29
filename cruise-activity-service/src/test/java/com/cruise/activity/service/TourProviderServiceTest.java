package com.cruise.activity.service;

import com.cruise.activity.dto.CreateTourProviderRequest;
import com.cruise.activity.dto.TourProviderResponse;
import com.cruise.activity.dto.UpdateTourProviderRequest;
import com.cruise.activity.entity.TourProvider;
import com.cruise.activity.enums.TourProviderStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.DuplicateResourceException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.TourProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TourProviderServiceTest {

    @Mock
    private TourProviderRepository tourProviderRepository;

    @InjectMocks
    private TourProviderService tourProviderService;

    @Test
    void createTourProviderGeneratesCodeAndDefaultsStatus() {
        CreateTourProviderRequest request = createRequest();

        when(tourProviderRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(tourProviderRepository.save(any(TourProvider.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TourProviderResponse response = tourProviderService.createTourProvider(request);

        assertTrue(response.getCode().matches("TP-[A-F0-9]{32}"));
        assertEquals("Provider One", response.getName());
        assertEquals("+84 123 456 789", response.getEmergencyHotline());
        assertEquals("VN-HALONG", response.getPortId());
        assertEquals(TourProviderStatus.ACTIVE, response.getStatus());
        verify(tourProviderRepository).save(any(TourProvider.class));
    }

    @Test
    void createTourProviderNormalizesProvidedValuesAndKeepsRequestedStatus() {
        CreateTourProviderRequest request = createRequest();
        request.setCode("  tp-custom  ");
        request.setTaxCode(" TAX-123 ");
        request.setLicenseNumber(" LIC-123 ");
        request.setContactPerson(" Contact ");
        request.setEmail(" contact@example.com ");
        request.setPhone(" 123456 ");
        request.setStatus(TourProviderStatus.TERMINATED);
        when(tourProviderRepository.existsByCodeIgnoreCase("TP-CUSTOM")).thenReturn(false);
        when(tourProviderRepository.existsByTaxCodeIgnoreCase("TAX-123")).thenReturn(false);
        when(tourProviderRepository.existsByLicenseNumberIgnoreCase("LIC-123")).thenReturn(false);
        when(tourProviderRepository.save(any(TourProvider.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TourProviderResponse response = tourProviderService.createTourProvider(request);

        assertEquals("TP-CUSTOM", response.getCode());
        assertEquals("TAX-123", response.getTaxCode());
        assertEquals("LIC-123", response.getLicenseNumber());
        assertEquals("Contact", response.getContactPerson());
        assertEquals("contact@example.com", response.getEmail());
        assertEquals("123456", response.getPhone());
        assertEquals(TourProviderStatus.TERMINATED, response.getStatus());
    }

    @Test
    void createTourProviderGeneratesCodeWhenProvidedCodeIsBlank() {
        CreateTourProviderRequest request = createRequest();
        request.setCode("   ");
        when(tourProviderRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(tourProviderRepository.save(any(TourProvider.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TourProviderResponse response = tourProviderService.createTourProvider(request);

        assertTrue(response.getCode().matches("TP-[A-F0-9]{32}"));
    }

    @Test
    void createTourProviderRejectsDuplicateCode() {
        CreateTourProviderRequest request = createRequest();
        request.setCode("tp-duplicate");
        when(tourProviderRepository.existsByCodeIgnoreCase("TP-DUPLICATE")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> tourProviderService.createTourProvider(request));

        verify(tourProviderRepository, never()).save(any(TourProvider.class));
    }

    @Test
    void createTourProviderRejectsDuplicateTaxCode() {
        CreateTourProviderRequest request = createRequest();
        request.setTaxCode(" TAX-123 ");
        when(tourProviderRepository.existsByTaxCodeIgnoreCase("TAX-123")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> tourProviderService.createTourProvider(request)
        );

        assertTrue(exception.getMessage().contains("Mã số thuế"));
        verify(tourProviderRepository, never()).save(any(TourProvider.class));
    }

    @Test
    void createTourProviderRejectsDuplicateLicenseNumber() {
        CreateTourProviderRequest request = createRequest();
        request.setLicenseNumber(" LICENSE-123 ");
        when(tourProviderRepository.existsByLicenseNumberIgnoreCase("LICENSE-123")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> tourProviderService.createTourProvider(request)
        );

        assertTrue(exception.getMessage().contains("Số giấy phép"));
        verify(tourProviderRepository, never()).save(any(TourProvider.class));
    }

    @Test
    void updateTourProviderRejectsDuplicateTaxCodeOwnedByAnotherProvider() {
        UUID id = UUID.randomUUID();
        UpdateTourProviderRequest request = updateRequest();
        request.setCode("  ");
        request.setTaxCode("TAX-123");
        when(tourProviderRepository.findById(id)).thenReturn(Optional.of(existingProvider(id)));
        when(tourProviderRepository.existsByTaxCodeIgnoreCaseAndIdNot("TAX-123", id)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> tourProviderService.updateTourProvider(id, request));

        verify(tourProviderRepository, never()).save(any(TourProvider.class));
    }

    @Test
    void updateTourProviderRejectsDuplicateLicenseNumberOwnedByAnotherProvider() {
        UUID id = UUID.randomUUID();
        UpdateTourProviderRequest request = updateRequest();
        request.setLicenseNumber("LICENSE-123");
        when(tourProviderRepository.findById(id)).thenReturn(Optional.of(existingProvider(id)));
        when(tourProviderRepository.existsByLicenseNumberIgnoreCaseAndIdNot("LICENSE-123", id)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> tourProviderService.updateTourProvider(id, request));

        verify(tourProviderRepository, never()).save(any(TourProvider.class));
    }

    @Test
    void updateTourProviderChecksIdentifiersExcludingCurrentProvider() {
        UUID id = UUID.randomUUID();
        UpdateTourProviderRequest request = updateRequest();
        request.setCode("  ");
        request.setTaxCode("TAX-123");
        request.setLicenseNumber("LICENSE-123");
        when(tourProviderRepository.findById(id)).thenReturn(Optional.of(existingProvider(id)));
        when(tourProviderRepository.save(any(TourProvider.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        tourProviderService.updateTourProvider(id, request);

        verify(tourProviderRepository).existsByTaxCodeIgnoreCaseAndIdNot("TAX-123", id);
        verify(tourProviderRepository).existsByLicenseNumberIgnoreCaseAndIdNot("LICENSE-123", id);
        verify(tourProviderRepository).save(any(TourProvider.class));
    }

    @Test
    void updateTourProviderUpdatesValuesAndPreservesOptionalFieldsWhenOmitted() {
        UUID id = UUID.randomUUID();
        TourProvider provider = existingProvider(id);
        provider.setContactPerson("Existing Contact");
        provider.setEmail("existing@example.com");
        provider.setPhone("111");
        UpdateTourProviderRequest request = updateRequest();
        request.setCode(" tp-existing ");
        request.setTaxCode("  ");
        request.setLicenseNumber(null);
        request.setStatus(TourProviderStatus.TERMINATED);
        when(tourProviderRepository.findById(id)).thenReturn(Optional.of(provider));
        when(tourProviderRepository.save(any(TourProvider.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TourProviderResponse response = tourProviderService.updateTourProvider(id, request);

        assertEquals("TP-EXISTING", response.getCode());
        assertEquals("Provider One", response.getName());
        assertNull(response.getTaxCode());
        assertNull(response.getLicenseNumber());
        assertEquals("Existing Contact", response.getContactPerson());
        assertEquals("existing@example.com", response.getEmail());
        assertEquals("111", response.getPhone());
        assertEquals(TourProviderStatus.TERMINATED, response.getStatus());
        verify(tourProviderRepository, never()).existsByCodeIgnoreCaseAndIdNot(anyString(), any(UUID.class));
    }

    @Test
    void updateTourProviderChangesCodeAndUpdatesProvidedOptionalFields() {
        UUID id = UUID.randomUUID();
        TourProvider provider = existingProvider(id);
        UpdateTourProviderRequest request = updateRequest();
        request.setCode(" tp-new ");
        request.setTaxCode(" TAX-NEW ");
        request.setLicenseNumber(" LIC-NEW ");
        request.setContactPerson(" Contact ");
        request.setEmail(" new@example.com ");
        request.setPhone(" 222 ");
        request.setStatus(TourProviderStatus.SUSPENDED);
        when(tourProviderRepository.findById(id)).thenReturn(Optional.of(provider));
        when(tourProviderRepository.existsByCodeIgnoreCaseAndIdNot("TP-NEW", id)).thenReturn(false);
        when(tourProviderRepository.existsByTaxCodeIgnoreCaseAndIdNot("TAX-NEW", id)).thenReturn(false);
        when(tourProviderRepository.existsByLicenseNumberIgnoreCaseAndIdNot("LIC-NEW", id)).thenReturn(false);
        when(tourProviderRepository.save(any(TourProvider.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TourProviderResponse response = tourProviderService.updateTourProvider(id, request);

        assertEquals("TP-NEW", response.getCode());
        assertEquals("TAX-NEW", response.getTaxCode());
        assertEquals("LIC-NEW", response.getLicenseNumber());
        assertEquals("Contact", response.getContactPerson());
        assertEquals("new@example.com", response.getEmail());
        assertEquals("222", response.getPhone());
        assertEquals(TourProviderStatus.SUSPENDED, response.getStatus());
        verify(tourProviderRepository).existsByCodeIgnoreCaseAndIdNot("TP-NEW", id);
    }

    @Test
    void updateTourProviderRejectsDuplicateCode() {
        UUID id = UUID.randomUUID();
        UpdateTourProviderRequest request = updateRequest();
        request.setCode("TP-OTHER");
        when(tourProviderRepository.findById(id)).thenReturn(Optional.of(existingProvider(id)));
        when(tourProviderRepository.existsByCodeIgnoreCaseAndIdNot("TP-OTHER", id)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> tourProviderService.updateTourProvider(id, request));

        verify(tourProviderRepository, never()).save(any(TourProvider.class));
    }

    @Test
    void updateTourProviderRejectsWhenProviderDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(tourProviderRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tourProviderService.updateTourProvider(id, updateRequest()));

        verify(tourProviderRepository, never()).save(any(TourProvider.class));
    }

    @Test
    void getTourProviderByIdThrowsWhenProviderDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(tourProviderRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tourProviderService.getTourProviderById(id));
    }

    @Test
    void getTourProviderByIdReturnsProviderWhenFound() {
        UUID id = UUID.randomUUID();
        when(tourProviderRepository.findById(id)).thenReturn(Optional.of(existingProvider(id)));

        TourProviderResponse response = tourProviderService.getTourProviderById(id);

        assertEquals(id, response.getId());
        assertEquals("Existing Provider", response.getName());
    }

    @Test
    void getTourProvidersMapsPageToResponses() {
        UUID id = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 10);
        when(tourProviderRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(existingProvider(id)), pageable, 1));

        Page<TourProviderResponse> response = tourProviderService.getTourProviders(pageable);

        assertEquals(1, response.getTotalElements());
        assertEquals(id, response.getContent().get(0).getId());
    }

    @Test
    void getTourProvidersFiltersByPortWhenPortIdIsProvided() {
        UUID id = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 10);
        when(tourProviderRepository.findAllByPortId("VN-HALONG", pageable))
                .thenReturn(new PageImpl<>(List.of(existingProvider(id)), pageable, 1));

        Page<TourProviderResponse> response = tourProviderService.getTourProviders(pageable, " VN-HALONG ");

        assertEquals(1, response.getTotalElements());
        assertEquals("VN-HALONG", response.getContent().get(0).getPortId());
        verify(tourProviderRepository).findAllByPortId("VN-HALONG", pageable);
    }

    private CreateTourProviderRequest createRequest() {
        CreateTourProviderRequest request = new CreateTourProviderRequest();
        request.setName("  Provider One  ");
        request.setEmergencyHotline("  +84 123 456 789 ");
        request.setPortId("  VN-HALONG ");
        return request;
    }

    private UpdateTourProviderRequest updateRequest() {
        UpdateTourProviderRequest request = new UpdateTourProviderRequest();
        request.setName("Provider One");
        request.setEmergencyHotline("+84 123 456 789");
        request.setPortId("VN-HALONG");
        return request;
    }

    private TourProvider existingProvider(UUID id) {
        return TourProvider.builder()
                .id(id)
                .code("TP-EXISTING")
                .name("Existing Provider")
                .taxCode("TAX-123")
                .licenseNumber("LICENSE-123")
                .emergencyHotline("+84 123 456 789")
                .portId("VN-HALONG")
                .status(TourProviderStatus.ACTIVE)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}

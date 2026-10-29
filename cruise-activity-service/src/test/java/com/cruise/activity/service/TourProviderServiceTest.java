package com.cruise.activity.service;

import com.cruise.activity.dto.CreateTourProviderRequest;
import com.cruise.activity.dto.TourProviderResponse;
import com.cruise.activity.dto.UpdateTourProviderRequest;
import com.cruise.activity.entity.TourProvider;
import com.cruise.activity.enums.TourProviderStatus;
import com.cruise.activity.exception.DuplicateTourProviderException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.TourProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        CreateTourProviderRequest request = new CreateTourProviderRequest();
        request.setName("  Provider One  ");
        request.setEmergencyHotline("  +84 123 456 789 ");
        request.setPortId("  VN-HALONG ");

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
    void createTourProviderRejectsDuplicateTaxCode() {
        CreateTourProviderRequest request = createRequest();
        request.setTaxCode(" TAX-123 ");
        when(tourProviderRepository.existsByTaxCodeIgnoreCase("TAX-123")).thenReturn(true);

        DuplicateTourProviderException exception = assertThrows(
                DuplicateTourProviderException.class,
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

        DuplicateTourProviderException exception = assertThrows(
                DuplicateTourProviderException.class,
                () -> tourProviderService.createTourProvider(request)
        );

        assertTrue(exception.getMessage().contains("Số giấy phép"));
        verify(tourProviderRepository, never()).save(any(TourProvider.class));
    }

    @Test
    void updateTourProviderRejectsDuplicateTaxCodeOwnedByAnotherProvider() {
        UUID id = UUID.randomUUID();
        UpdateTourProviderRequest request = updateRequest();
        request.setTaxCode("TAX-123");
        when(tourProviderRepository.findById(id)).thenReturn(Optional.of(existingProvider(id)));
        when(tourProviderRepository.existsByTaxCodeIgnoreCaseAndIdNot("TAX-123", id)).thenReturn(true);

        assertThrows(DuplicateTourProviderException.class,
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

        assertThrows(DuplicateTourProviderException.class,
                () -> tourProviderService.updateTourProvider(id, request));

        verify(tourProviderRepository, never()).save(any(TourProvider.class));
    }

    @Test
    void updateTourProviderChecksIdentifiersExcludingCurrentProvider() {
        UUID id = UUID.randomUUID();
        UpdateTourProviderRequest request = updateRequest();
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
    void getTourProviderByIdThrowsWhenProviderDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(tourProviderRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tourProviderService.getTourProviderById(id));
    }

    private CreateTourProviderRequest createRequest() {
        CreateTourProviderRequest request = new CreateTourProviderRequest();
        request.setName("Provider One");
        request.setEmergencyHotline("+84 123 456 789");
        request.setPortId("VN-HALONG");
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
                .build();
    }
}

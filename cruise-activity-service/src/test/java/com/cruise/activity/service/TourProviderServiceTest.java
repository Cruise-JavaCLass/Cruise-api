package com.cruise.activity.service;

import com.cruise.activity.dto.CreateTourProviderRequest;
import com.cruise.activity.dto.TourProviderResponse;
import com.cruise.activity.entity.TourProvider;
import com.cruise.activity.enums.TourProviderStatus;
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
    void getTourProviderByIdThrowsWhenProviderDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(tourProviderRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tourProviderService.getTourProviderById(id));
    }
}

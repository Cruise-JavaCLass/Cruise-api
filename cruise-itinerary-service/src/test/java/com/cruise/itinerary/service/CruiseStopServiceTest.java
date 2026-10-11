package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.CruiseStopRequest;
import com.cruise.itinerary.dto.CruiseStopResponse;
import com.cruise.itinerary.entity.CruiseStop;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.entity.Port;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.repository.CruiseStopRepository;
import com.cruise.itinerary.repository.CruiseTourRepository;
import com.cruise.itinerary.repository.PortRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CruiseStopService Unit Tests (KAN-106)")
class CruiseStopServiceTest {

    @Mock
    private CruiseStopRepository stopRepository;
    @Mock
    private CruiseTourRepository tourRepository;
    @Mock
    private PortRepository portRepository;
    @Mock
    private StopValidator stopValidator;

    @InjectMocks
    private CruiseStopService stopService;

    private CruiseTour sampleTour;
    private Port samplePort;
    private CruiseStop sampleStop;
    private CruiseStopRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleTour = CruiseTour.builder()
                .startDate(LocalDate.of(2026, 10, 15))
                .endDate(LocalDate.of(2026, 10, 18))
                .name("Tour Hạ Long")
                .code("TOUR-01")
                .build();
        sampleTour.setId(10L);

        samplePort = Port.builder()
                .name("Cảng Tuần Châu")
                .code("VN-TCH")
                .city("Hạ Long")
                .build();
        samplePort.setId(2L);

        sampleStop = CruiseStop.builder()
                .tour(sampleTour)
                .port(samplePort)
                .stopOrder(1)
                .arrivalTime(LocalDateTime.of(2026, 10, 15, 8, 0))
                .departureTime(LocalDateTime.of(2026, 10, 15, 18, 0))
                .active(true)
                .build();
        sampleStop.setId(100L);

        sampleRequest = new CruiseStopRequest();
        sampleRequest.setPortId(2L);
        sampleRequest.setStopOrder(1);
        sampleRequest.setArrivalTime(LocalDateTime.of(2026, 10, 15, 8, 0));
        sampleRequest.setDepartureTime(LocalDateTime.of(2026, 10, 15, 18, 0));
        sampleRequest.setActive(true);
    }

    @Test
    @DisplayName("createStop thành công")
    void createStopSuccess() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(portRepository.findById(2L)).thenReturn(Optional.of(samplePort));
        when(stopRepository.existsByTourIdAndStopOrderAndActiveTrue(10L, 1)).thenReturn(false);
        when(stopRepository.save(any(CruiseStop.class))).thenReturn(sampleStop);

        CruiseStopResponse resp = stopService.createStop(10L, sampleRequest);

        assertThat(resp).isNotNull();
        assertThat(resp.getStopOrder()).isEqualTo(1);
        verify(stopValidator).validateStopSchedule(eq(sampleTour), eq(sampleRequest), isNull());
    }

    @Test
    @DisplayName("createStop ném lỗi khi tour hoặc port không tồn tại")
    void createStopNotFound() {
        when(tourRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> stopService.createStop(99L, sampleRequest))
                .isInstanceOf(ResourceNotFoundException.class);

        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(portRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> stopService.createStop(10L, sampleRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("createStop ném lỗi khi trùng stopOrder")
    void createStopDuplicateOrder() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(portRepository.findById(2L)).thenReturn(Optional.of(samplePort));
        when(stopRepository.existsByTourIdAndStopOrderAndActiveTrue(10L, 1)).thenReturn(true);

        assertThatThrownBy(() -> stopService.createStop(10L, sampleRequest))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("updateStop thành công")
    void updateStopSuccess() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(stopRepository.findById(100L)).thenReturn(Optional.of(sampleStop));
        when(portRepository.findById(2L)).thenReturn(Optional.of(samplePort));
        when(stopRepository.existsByTourIdAndStopOrderAndIdNotAndActiveTrue(10L, 1, 100L)).thenReturn(false);
        when(stopRepository.save(any(CruiseStop.class))).thenReturn(sampleStop);

        CruiseStopResponse resp = stopService.updateStop(10L, 100L, sampleRequest);

        assertThat(resp).isNotNull();
        verify(stopValidator).validateStopSchedule(eq(sampleTour), eq(sampleRequest), eq(100L));
    }

    @Test
    @DisplayName("updateStop ném lỗi khi tour không khớp hoặc trùng stopOrder")
    void updateStopValidationErrors() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));

        CruiseTour otherTour = new CruiseTour();
        otherTour.setId(99L);
        CruiseStop mismatchedStop = CruiseStop.builder().tour(otherTour).build();
        mismatchedStop.setId(100L);

        when(stopRepository.findById(100L)).thenReturn(Optional.of(mismatchedStop));

        assertThatThrownBy(() -> stopService.updateStop(10L, 100L, sampleRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thuộc về tour");

        // Duplicate order test
        when(stopRepository.findById(100L)).thenReturn(Optional.of(sampleStop));
        when(portRepository.findById(2L)).thenReturn(Optional.of(samplePort));
        when(stopRepository.existsByTourIdAndStopOrderAndIdNotAndActiveTrue(10L, 1, 100L)).thenReturn(true);

        assertThatThrownBy(() -> stopService.updateStop(10L, 100L, sampleRequest))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("getStopsByTourId và getStopById thành công")
    void getStopsSuccess() {
        when(tourRepository.existsById(10L)).thenReturn(true);
        when(stopRepository.findByTourIdAndActiveTrueOrderByStopOrderAsc(10L)).thenReturn(List.of(sampleStop));
        when(stopRepository.findById(100L)).thenReturn(Optional.of(sampleStop));

        List<CruiseStopResponse> list = stopService.getStopsByTourId(10L);
        CruiseStopResponse single = stopService.getStopById(10L, 100L);

        assertThat(list).hasSize(1);
        assertThat(single.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getStopsByTourId ném lỗi khi tour không tồn tại")
    void getStopsTourNotFound() {
        when(tourRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> stopService.getStopsByTourId(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getStopById và deleteStop ném lỗi khi stop không thuộc tour")
    void stopNotBelongToTour() {
        CruiseTour otherTour = new CruiseTour();
        otherTour.setId(99L);
        CruiseStop mismatchedStop = CruiseStop.builder().tour(otherTour).build();
        mismatchedStop.setId(100L);

        when(stopRepository.findById(100L)).thenReturn(Optional.of(mismatchedStop));

        assertThatThrownBy(() -> stopService.getStopById(10L, 100L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> stopService.deleteStop(10L, 100L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deleteStop thành công")
    void deleteStopSuccess() {
        when(stopRepository.findById(100L)).thenReturn(Optional.of(sampleStop));

        stopService.deleteStop(10L, 100L);

        assertThat(sampleStop.getActive()).isFalse();
        verify(stopRepository).save(sampleStop);
    }

    @Test
    @DisplayName("fromEntity trả về null khi đầu vào null")
    void fromEntityNull() {
        assertThat(CruiseStopResponse.fromEntity(null)).isNull();
    }
}

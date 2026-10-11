package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.CruiseStopRequest;
import com.cruise.itinerary.entity.CruiseStop;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.repository.CruiseStopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StopValidatorTest {

    @Mock
    private CruiseStopRepository stopRepository;

    @InjectMocks
    private StopValidator stopValidator;

    private CruiseTour sampleTour;

    @BeforeEach
    void setUp() {
        sampleTour = CruiseTour.builder()
                .startDate(LocalDate.of(2026, 10, 15))
                .endDate(LocalDate.of(2026, 10, 18))
                .build();
        sampleTour.setId(1L);
    }

    @Test
    @DisplayName("Ném lỗi khi thời gian cập cảng không trước thời gian rời cảng")
    void shouldThrowExceptionWhenArrivalNotBeforeDeparture() {
        CruiseStopRequest req = new CruiseStopRequest();
        req.setStopOrder(1);
        req.setArrivalTime(LocalDateTime.of(2026, 10, 15, 12, 0));
        req.setDepartureTime(LocalDateTime.of(2026, 10, 15, 11, 0));

        assertThatThrownBy(() -> stopValidator.validateStopSchedule(sampleTour, req, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Thời gian cập cảng (arrival) phải trước thời gian rời cảng (departure)");
    }

    @Test
    @DisplayName("Ném lỗi khi allAboardTime nằm ngoài khoảng arrival và departure")
    void shouldThrowExceptionWhenAllAboardTimeOutOfBounds() {
        CruiseStopRequest req = new CruiseStopRequest();
        req.setStopOrder(1);
        req.setArrivalTime(LocalDateTime.of(2026, 10, 15, 10, 0));
        req.setDepartureTime(LocalDateTime.of(2026, 10, 15, 18, 0));
        req.setAllAboardTime(LocalDateTime.of(2026, 10, 15, 19, 0)); // after departure

        assertThatThrownBy(() -> stopValidator.validateStopSchedule(sampleTour, req, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("all-aboard");
    }

    @Test
    @DisplayName("Ném lỗi khi thời gian cập cảng trước ngày bắt đầu tour")
    void shouldThrowExceptionWhenArrivalBeforeTourStart() {
        CruiseStopRequest req = new CruiseStopRequest();
        req.setStopOrder(1);
        req.setArrivalTime(LocalDateTime.of(2026, 10, 14, 23, 0));
        req.setDepartureTime(LocalDateTime.of(2026, 10, 15, 12, 0));

        assertThatThrownBy(() -> stopValidator.validateStopSchedule(sampleTour, req, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trước ngày bắt đầu tour");
    }

    @Test
    @DisplayName("Ném lỗi khi thời gian rời cảng sau ngày kết thúc tour")
    void shouldThrowExceptionWhenDepartureAfterTourEnd() {
        CruiseStopRequest req = new CruiseStopRequest();
        req.setStopOrder(1);
        req.setArrivalTime(LocalDateTime.of(2026, 10, 18, 12, 0));
        req.setDepartureTime(LocalDateTime.of(2026, 10, 19, 1, 0));

        assertThatThrownBy(() -> stopValidator.validateStopSchedule(sampleTour, req, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vượt quá ngày kết thúc tour");
    }

    @Test
    @DisplayName("Ném lỗi khi điểm dừng sau cập cảng trước khi điểm dừng trước rời cảng")
    void shouldThrowExceptionWhenStopScheduleOverlapsWithPreviousStop() {
        CruiseStop stop1 = CruiseStop.builder()
                .stopOrder(1)
                .arrivalTime(LocalDateTime.of(2026, 10, 15, 8, 0))
                .departureTime(LocalDateTime.of(2026, 10, 15, 18, 0))
                .build();
        stop1.setId(10L);

        when(stopRepository.findByTourIdAndActiveTrueOrderByStopOrderAsc(1L))
                .thenReturn(List.of(stop1));

        CruiseStopRequest req2 = new CruiseStopRequest();
        req2.setStopOrder(2);
        req2.setArrivalTime(LocalDateTime.of(2026, 10, 15, 17, 0)); // Before stop1 departs!
        req2.setDepartureTime(LocalDateTime.of(2026, 10, 16, 12, 0));

        assertThatThrownBy(() -> stopValidator.validateStopSchedule(sampleTour, req2, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thể cập cảng trước khi điểm dừng thứ tự 1 rời cảng");
    }

    @Test
    @DisplayName("Hợp lệ khi thứ tự và các mốc thời gian tuần tự đúng chuẩn")
    void shouldPassValidationWhenScheduleIsValid() {
        CruiseStop stop1 = CruiseStop.builder()
                .stopOrder(1)
                .arrivalTime(LocalDateTime.of(2026, 10, 15, 8, 0))
                .departureTime(LocalDateTime.of(2026, 10, 15, 16, 0))
                .build();
        stop1.setId(10L);

        when(stopRepository.findByTourIdAndActiveTrueOrderByStopOrderAsc(1L))
                .thenReturn(List.of(stop1));

        CruiseStopRequest req2 = new CruiseStopRequest();
        req2.setStopOrder(2);
        req2.setArrivalTime(LocalDateTime.of(2026, 10, 16, 8, 0));
        req2.setDepartureTime(LocalDateTime.of(2026, 10, 16, 18, 0));
        req2.setAllAboardTime(LocalDateTime.of(2026, 10, 16, 17, 30));

        assertThatCode(() -> stopValidator.validateStopSchedule(sampleTour, req2, null))
                .doesNotThrowAnyException();
    }
}

package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.DailyItineraryRequest;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.entity.DailyItinerary;
import com.cruise.itinerary.repository.DailyItineraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyItineraryValidator Scheduling Rules Tests (KAN-116)")
class DailyItineraryValidatorTest {

    @Mock
    private DailyItineraryRepository dailyItineraryRepository;

    @InjectMocks
    private DailyItineraryValidator validator;

    private CruiseTour sampleTour;
    private DailyItineraryRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleTour = CruiseTour.builder()
                .durationDays(5)
                .durationNights(4)
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 5))
                .build();
        sampleTour.setId(10L);

        sampleRequest = DailyItineraryRequest.builder()
                .dayNumber(1)
                .itineraryDate(LocalDate.of(2026, 11, 1))
                .title("Ngày khởi hành")
                .portId(2L)
                .arrivalTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(17, 0))
                .allAboardTime(LocalTime.of(16, 30))
                .isSeaDay(false)
                .build();
    }

    @Test
    @DisplayName("Hợp lệ khi các quy tắc xếp lịch được thỏa mãn")
    void validItineraryPasses() {
        when(dailyItineraryRepository.findByTourIdAndActiveTrueOrderByDayNumberAsc(10L)).thenReturn(List.of());
        assertThatCode(() -> validator.validateDailyItinerary(sampleTour, sampleRequest, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Ném lỗi khi số thứ tự ngày nhỏ hơn 1")
    void invalidDayNumberNegativeOrZero() {
        sampleRequest.setDayNumber(0);
        assertThatThrownBy(() -> validator.validateDailyItinerary(sampleTour, sampleRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Số thứ tự ngày phải lớn hơn hoặc bằng 1");
    }

    @Test
    @DisplayName("Ném lỗi khi số thứ tự ngày vượt quá thời lượng tour")
    void dayNumberExceedsTourDuration() {
        sampleRequest.setDayNumber(6);
        sampleRequest.setItineraryDate(LocalDate.of(2026, 11, 6));
        assertThatThrownBy(() -> validator.validateDailyItinerary(sampleTour, sampleRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được vượt quá tổng thời lượng tour");
    }

    @Test
    @DisplayName("Ném lỗi khi ngày trước ngày bắt đầu tour")
    void itineraryDateBeforeTourStart() {
        sampleRequest.setDayNumber(1);
        sampleRequest.setItineraryDate(LocalDate.of(2026, 10, 31));
        assertThatThrownBy(() -> validator.validateDailyItinerary(sampleTour, sampleRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được trước ngày bắt đầu tour");
    }

    @Test
    @DisplayName("Ném lỗi khi ngày sau ngày kết thúc tour")
    void itineraryDateAfterTourEnd() {
        sampleRequest.setDayNumber(5);
        sampleRequest.setItineraryDate(LocalDate.of(2026, 11, 10));
        assertThatThrownBy(() -> validator.validateDailyItinerary(sampleTour, sampleRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được sau ngày kết thúc tour");
    }

    @Test
    @DisplayName("Ném lỗi khi ngày lịch trình không khớp thứ tự ngày tính từ ngày bắt đầu")
    void itineraryDateMismatchesDayNumber() {
        sampleRequest.setDayNumber(2);
        sampleRequest.setItineraryDate(LocalDate.of(2026, 11, 1)); // Phải là 11-02
        assertThatThrownBy(() -> validator.validateDailyItinerary(sampleTour, sampleRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phải tương ứng với ngày thứ");
    }

    @Test
    @DisplayName("Ném lỗi khi arrival time không trước departure time")
    void arrivalTimeNotBeforeDepartureTime() {
        sampleRequest.setArrivalTime(LocalTime.of(18, 0));
        sampleRequest.setDepartureTime(LocalTime.of(17, 0));
        assertThatThrownBy(() -> validator.validateDailyItinerary(sampleTour, sampleRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Thời gian cập cảng (arrival) phải trước thời gian rời cảng (departure)");
    }

    @Test
    @DisplayName("Ném lỗi khi all-aboard không nằm trong khoảng arrival và departure")
    void allAboardOutOfRange() {
        sampleRequest.setArrivalTime(LocalTime.of(8, 0));
        sampleRequest.setDepartureTime(LocalTime.of(17, 0));
        sampleRequest.setAllAboardTime(LocalTime.of(17, 30));
        assertThatThrownBy(() -> validator.validateDailyItinerary(sampleTour, sampleRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Thời gian lên tàu (all-aboard) phải nằm giữa thời gian cập cảng và rời cảng");
    }

    @Test
    @DisplayName("Ném lỗi khi Sea Day nhưng có gán cảng dừng")
    void seaDayWithPortAssignedFails() {
        sampleRequest.setIsSeaDay(true);
        sampleRequest.setPortId(3L);
        assertThatThrownBy(() -> validator.validateDailyItinerary(sampleTour, sampleRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ngày lênh đênh trên biển (Sea Day) không thể chỉ định cảng dừng");
    }

    @Test
    @DisplayName("Ném lỗi khi trùng số thứ tự ngày với ngày khác trong tour")
    void duplicateDayNumberWithExisting() {
        DailyItinerary existing = DailyItinerary.builder()
                .dayNumber(1)
                .itineraryDate(LocalDate.of(2026, 11, 1))
                .build();
        existing.setId(101L);

        when(dailyItineraryRepository.findByTourIdAndActiveTrueOrderByDayNumberAsc(10L))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> validator.validateDailyItinerary(sampleTour, sampleRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lịch trình cho ngày thứ 1 đã tồn tại trong tour");
    }
}

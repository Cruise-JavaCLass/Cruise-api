package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.DailyItineraryRequest;
import com.cruise.itinerary.dto.DailyItineraryResponse;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.entity.DailyItinerary;
import com.cruise.itinerary.entity.Port;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.repository.CruiseTourRepository;
import com.cruise.itinerary.repository.DailyItineraryRepository;
import com.cruise.itinerary.repository.PortRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyItineraryService Unit Tests (KAN-114, KAN-116)")
class DailyItineraryServiceTest {

    @Mock
    private DailyItineraryRepository dailyItineraryRepository;

    @Mock
    private CruiseTourRepository tourRepository;

    @Mock
    private PortRepository portRepository;

    @Mock
    private DailyItineraryValidator dailyItineraryValidator;

    @Mock
    private ItineraryChangeHistoryService historyService;

    @InjectMocks
    private DailyItineraryService dailyItineraryService;

    private CruiseTour sampleTour;
    private Port samplePort;
    private DailyItinerary sampleItinerary;
    private DailyItineraryRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleTour = CruiseTour.builder()
                .durationDays(3)
                .durationNights(2)
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 3))
                .build();
        sampleTour.setId(10L);

        samplePort = Port.builder()
                .name("Cảng Tuần Châu")
                .code("VN-TCH")
                .city("Quảng Ninh")
                .build();
        samplePort.setId(2L);

        sampleItinerary = DailyItinerary.builder()
                .tour(sampleTour)
                .dayNumber(1)
                .itineraryDate(LocalDate.of(2026, 11, 1))
                .title("Khởi hành")
                .port(samplePort)
                .portName("Cảng Tuần Châu")
                .arrivalTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(12, 0))
                .isSeaDay(false)
                .active(true)
                .build();
        sampleItinerary.setId(1L);

        sampleRequest = DailyItineraryRequest.builder()
                .dayNumber(1)
                .itineraryDate(LocalDate.of(2026, 11, 1))
                .title("Khởi hành")
                .portId(2L)
                .portName("Cảng Tuần Châu")
                .arrivalTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(12, 0))
                .isSeaDay(false)
                .build();
    }

    @Test
    @DisplayName("Tạo lịch trình ngày thành công khi hợp lệ")
    void createDailyItinerarySuccess() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        doNothing().when(dailyItineraryValidator).validateDailyItinerary(sampleTour, sampleRequest, null);
        when(portRepository.findById(2L)).thenReturn(Optional.of(samplePort));
        when(dailyItineraryRepository.save(any(DailyItinerary.class))).thenReturn(sampleItinerary);

        DailyItineraryResponse response = dailyItineraryService.createDailyItinerary(10L, sampleRequest);

        assertThat(response).isNotNull();
        assertThat(response.getDayNumber()).isEqualTo(1);
        assertThat(response.getTitle()).isEqualTo("Khởi hành");
        assertThat(response.getPortName()).isEqualTo("Cảng Tuần Châu");
        verify(dailyItineraryRepository, times(1)).save(any(DailyItinerary.class));
    }

    @Test
    @DisplayName("Ném lỗi khi tour không tồn tại")
    void createFailsWhenTourNotFound() {
        when(tourRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyItineraryService.createDailyItinerary(99L, sampleRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("không tồn tại");
    }

    @Test
    @DisplayName("Ném lỗi khi validator ném ngoại lệ")
    void createFailsWhenValidatorFails() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        doThrow(new IllegalArgumentException("Lỗi xếp lịch"))
                .when(dailyItineraryValidator).validateDailyItinerary(sampleTour, sampleRequest, null);

        assertThatThrownBy(() -> dailyItineraryService.createDailyItinerary(10L, sampleRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lỗi xếp lịch");
    }

    @Test
    @DisplayName("Cập nhật lịch trình ngày thành công")
    void updateDailyItinerarySuccess() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(dailyItineraryRepository.findByIdAndTourIdAndActiveTrue(1L, 10L)).thenReturn(Optional.of(sampleItinerary));
        doNothing().when(dailyItineraryValidator).validateDailyItinerary(sampleTour, sampleRequest, 1L);
        when(portRepository.findById(2L)).thenReturn(Optional.of(samplePort));
        when(dailyItineraryRepository.save(any(DailyItinerary.class))).thenReturn(sampleItinerary);

        sampleRequest.setTitle("Tiêu đề mới");
        DailyItineraryResponse response = dailyItineraryService.updateDailyItinerary(10L, 1L, sampleRequest);

        assertThat(response).isNotNull();
        verify(dailyItineraryRepository).save(sampleItinerary);
    }

    @Test
    @DisplayName("Lấy danh sách và chi tiết lịch trình ngày")
    void getDailyItinerariesSuccess() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(dailyItineraryRepository.findByTourIdAndActiveTrueOrderByDayNumberAsc(10L))
                .thenReturn(List.of(sampleItinerary));

        List<DailyItineraryResponse> list = dailyItineraryService.getDailyItinerariesByTourId(10L);
        assertThat(list).hasSize(1);

        when(dailyItineraryRepository.findByIdAndTourIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.of(sampleItinerary));
        DailyItineraryResponse detail = dailyItineraryService.getDailyItineraryById(10L, 1L);
        assertThat(detail.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Xóa mềm lịch trình ngày thành công")
    void deleteDailyItinerarySuccess() {
        when(dailyItineraryRepository.findByIdAndTourIdAndActiveTrue(1L, 10L))
                .thenReturn(Optional.of(sampleItinerary));

        dailyItineraryService.deleteDailyItinerary(10L, 1L);

        assertThat(sampleItinerary.getActive()).isFalse();
        verify(dailyItineraryRepository).save(sampleItinerary);
    }

    @Test
    @DisplayName("Sắp xếp lại thứ tự lịch trình thành công")
    void reorderDailyItinerariesSuccess() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(dailyItineraryRepository.findByTourIdAndActiveTrueOrderByDayNumberAsc(10L))
                .thenReturn(List.of(sampleItinerary));

        com.cruise.itinerary.dto.ReorderItineraryRequest req = com.cruise.itinerary.dto.ReorderItineraryRequest.builder()
                .items(List.of(new com.cruise.itinerary.dto.ReorderItemDto(1L, 2)))
                .build();

        List<DailyItineraryResponse> result = dailyItineraryService.reorderDailyItineraries(10L, req);

        assertThat(result).isNotNull();
        assertThat(sampleItinerary.getDayNumber()).isEqualTo(2);
        assertThat(sampleItinerary.getItineraryDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        verify(dailyItineraryRepository).saveAll(any());
    }

    @Test
    @DisplayName("Sắp xếp thất bại khi có thứ tự trùng nhau")
    void reorderDailyItinerariesDuplicateOrder() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));

        com.cruise.itinerary.dto.ReorderItineraryRequest req = com.cruise.itinerary.dto.ReorderItineraryRequest.builder()
                .items(List.of(
                        new com.cruise.itinerary.dto.ReorderItemDto(1L, 2),
                        new com.cruise.itinerary.dto.ReorderItemDto(2L, 2)
                ))
                .build();

        assertThatThrownBy(() -> dailyItineraryService.reorderDailyItineraries(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được trùng lặp");
    }

    @Test
    @DisplayName("Sắp xếp thất bại khi thứ tự vượt quá ngày tour")
    void reorderDailyItinerariesExceedDuration() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));

        com.cruise.itinerary.dto.ReorderItineraryRequest req = com.cruise.itinerary.dto.ReorderItineraryRequest.builder()
                .items(List.of(new com.cruise.itinerary.dto.ReorderItemDto(1L, 10)))
                .build();

        assertThatThrownBy(() -> dailyItineraryService.reorderDailyItineraries(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được vượt quá thời lượng tour");
    }

    @Test
    @DisplayName("Tạo lịch trình khi không chọn cảng (portId null)")
    void createDailyItineraryWithoutPort() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        sampleRequest.setPortId(null);
        sampleRequest.setPortName(null);
        when(dailyItineraryRepository.save(any(DailyItinerary.class))).thenReturn(sampleItinerary);

        DailyItineraryResponse response = dailyItineraryService.createDailyItinerary(10L, sampleRequest);

        assertThat(response).isNotNull();
        verify(portRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Tạo lịch trình thất bại khi portId không tồn tại")
    void createDailyItineraryPortNotFound() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(portRepository.findById(999L)).thenReturn(Optional.empty());
        sampleRequest.setPortId(999L);

        assertThatThrownBy(() -> dailyItineraryService.createDailyItinerary(10L, sampleRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cảng với ID 999 không tồn tại");
    }

    @Test
    @DisplayName("Lấy tên cảng từ Port entity khi portName trong request rỗng hoặc null")
    void createDailyItineraryFallbackToPortName() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(portRepository.findById(2L)).thenReturn(Optional.of(samplePort));
        sampleRequest.setPortName("   ");
        when(dailyItineraryRepository.save(any(DailyItinerary.class))).thenReturn(sampleItinerary);

        DailyItineraryResponse response = dailyItineraryService.createDailyItinerary(10L, sampleRequest);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("updateAffectedSchedule - Cập nhật lịch trình bị ảnh hưởng do thời tiết thành công")
    void updateAffectedScheduleSuccess() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(dailyItineraryRepository.findByIdAndTourIdAndActiveTrue(100L, 10L)).thenReturn(Optional.of(sampleItinerary));
        when(portRepository.findById(2L)).thenReturn(Optional.of(samplePort));
        when(dailyItineraryRepository.save(any(DailyItinerary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.cruise.itinerary.dto.UpdateAffectedScheduleRequest req = com.cruise.itinerary.dto.UpdateAffectedScheduleRequest.builder()
                .changeType(com.cruise.itinerary.enums.ChangeType.WEATHER_UPDATE)
                .reason("Bão số 3 chuyển hướng tàu")
                .changedBy("CAPTAIN_AN")
                .portId(2L)
                .isSeaDay(true)
                .weatherForecast("Mưa bão lớn, gió giật cấp 9")
                .description("Hải trình chuyển sang đi trên biển tránh bão")
                .arrivalTime(LocalTime.of(10, 0))
                .departureTime(LocalTime.of(18, 0))
                .allAboardTime(LocalTime.of(17, 30))
                .build();

        DailyItineraryResponse response = dailyItineraryService.updateAffectedSchedule(10L, 100L, req);

        assertThat(response).isNotNull();
        assertThat(response.getIsAffected()).isTrue();
        assertThat(response.getChangeReason()).isEqualTo("Bão số 3 chuyển hướng tàu");
        assertThat(response.getChangeType()).isEqualTo(com.cruise.itinerary.enums.ChangeType.WEATHER_UPDATE);
        verify(historyService, times(1)).recordChange(
                eq(sampleTour), any(DailyItinerary.class), eq(com.cruise.itinerary.enums.ChangeType.WEATHER_UPDATE),
                eq("Bão số 3 chuyển hướng tàu"), eq("CAPTAIN_AN"), anyString(), anyString()
        );
    }

    @Test
    @DisplayName("updateAffectedSchedule - Cập nhật khi chỉ đổi portName mà không đổi portId")
    void updateAffectedSchedulePortNameOnly() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(dailyItineraryRepository.findByIdAndTourIdAndActiveTrue(100L, 10L)).thenReturn(Optional.of(sampleItinerary));
        when(dailyItineraryRepository.save(any(DailyItinerary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.cruise.itinerary.dto.UpdateAffectedScheduleRequest req = com.cruise.itinerary.dto.UpdateAffectedScheduleRequest.builder()
                .changeType(com.cruise.itinerary.enums.ChangeType.EMERGENCY_CHANGE)
                .reason("Cảng cũ bảo trì đột xuất")
                .portName("Cảng thay thế Cát Bà")
                .build();

        DailyItineraryResponse response = dailyItineraryService.updateAffectedSchedule(10L, 100L, req);

        assertThat(response.getPortName()).isEqualTo("Cảng thay thế Cát Bà");
        assertThat(response.getIsAffected()).isTrue();
    }
}

package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.ItineraryChangeHistoryResponse;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.entity.DailyItinerary;
import com.cruise.itinerary.entity.ItineraryChangeHistory;
import com.cruise.itinerary.enums.ChangeType;
import com.cruise.itinerary.repository.CruiseTourRepository;
import com.cruise.itinerary.repository.ItineraryChangeHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItineraryChangeHistoryService Unit Tests (KAN-124)")
class ItineraryChangeHistoryServiceTest {

    @Mock
    private ItineraryChangeHistoryRepository historyRepository;

    @Mock
    private CruiseTourRepository tourRepository;

    @InjectMocks
    private ItineraryChangeHistoryService historyService;

    private CruiseTour sampleTour;
    private DailyItinerary sampleDailyItinerary;
    private ItineraryChangeHistory sampleHistory;

    @BeforeEach
    void setUp() {
        sampleTour = CruiseTour.builder()
                .code("CRUISE-001")
                .name("Hạ Long Bay Cruise")
                .build();
        sampleTour.setId(10L);

        sampleDailyItinerary = DailyItinerary.builder()
                .tour(sampleTour)
                .dayNumber(1)
                .title("Ngày 1: Check-in")
                .build();
        sampleDailyItinerary.setId(100L);

        sampleHistory = ItineraryChangeHistory.builder()
                .tour(sampleTour)
                .dailyItinerary(sampleDailyItinerary)
                .changeType(ChangeType.CREATE)
                .reason("Khởi tạo lịch trình")
                .changedBy("ADMIN")
                .previousState(null)
                .newState("Ngày 1: Check-in")
                .build();
        sampleHistory.setId(1L);
        sampleHistory.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("recordChange - Lưu lịch sử thay đổi thành công")
    void recordChangeSuccess() {
        when(historyRepository.save(any(ItineraryChangeHistory.class))).thenReturn(sampleHistory);

        historyService.recordChange(
                sampleTour,
                sampleDailyItinerary,
                ChangeType.CREATE,
                "Khởi tạo",
                "ADMIN",
                null,
                "New State"
        );

        verify(historyRepository, times(1)).save(any(ItineraryChangeHistory.class));
    }

    @Test
    @DisplayName("recordChange - Gán mặc định changedBy là SYSTEM khi null")
    void recordChangeDefaultChangedBy() {
        when(historyRepository.save(any(ItineraryChangeHistory.class))).thenReturn(sampleHistory);

        historyService.recordChange(
                sampleTour,
                null,
                ChangeType.REORDER,
                "Sắp xếp lại",
                null,
                null,
                null
        );

        verify(historyRepository, times(1)).save(argThat(h -> "SYSTEM".equals(h.getChangedBy())));
    }

    @Test
    @DisplayName("getHistoriesByTourId - Lấy danh sách lịch sử theo tourId thành công")
    void getHistoriesByTourIdSuccess() {
        when(historyRepository.findByTourIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(sampleHistory));

        List<ItineraryChangeHistoryResponse> result = historyService.getHistoriesByTourId(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getTourId()).isEqualTo(10L);
        assertThat(result.get(0).getDailyItineraryId()).isEqualTo(100L);
        assertThat(result.get(0).getChangeType()).isEqualTo(ChangeType.CREATE);
    }

    @Test
    @DisplayName("getHistoriesByDailyItineraryId - Lấy danh sách lịch sử theo dailyItineraryId thành công")
    void getHistoriesByDailyItineraryIdSuccess() {
        when(historyRepository.findByDailyItineraryIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(sampleHistory));

        List<ItineraryChangeHistoryResponse> result = historyService.getHistoriesByDailyItineraryId(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getDailyItineraryId()).isEqualTo(100L);
    }
}

package com.cruise.activity.service;

import com.cruise.activity.dto.ExcursionReviewRequest;
import com.cruise.activity.dto.ExcursionReviewResponse;
import com.cruise.activity.dto.FleetOccupancyAnalyticsResponse;
import com.cruise.activity.dto.ExcursionOpsKpiResponse;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ExcursionReview;
import com.cruise.activity.entity.ExcursionVehicle;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.VehicleStatus;
import com.cruise.activity.exception.AccessDeniedException;
import com.cruise.activity.exception.DuplicateRegistrationException;
import com.cruise.activity.repository.ExcursionGuideRepository;
import com.cruise.activity.repository.ExcursionPassengerRepository;
import com.cruise.activity.repository.ExcursionReviewRepository;
import com.cruise.activity.repository.ExcursionVehicleRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcursionReviewAndAnalyticsTest {

    @Mock
    private ExcursionReviewRepository reviewRepository;

    @Mock
    private ShoreExcursionRepository excursionRepository;

    @Mock
    private ExcursionPassengerRepository passengerRepository;

    @Mock
    private ExcursionVehicleRepository vehicleRepository;

    @Mock
    private ExcursionGuideRepository guideRepository;

    @InjectMocks
    private ExcursionReviewService reviewService;

    private FleetOccupancyAggregator fleetAggregator;
    private ExcursionOpsAnalyticsService opsAnalyticsService;

    private ShoreExcursion mockExcursion;
    private ExcursionPassenger attendedPassenger;
    private ExcursionPassenger registeredPassenger;

    @BeforeEach
    void setUp() {
        fleetAggregator = new FleetOccupancyAggregator(excursionRepository, vehicleRepository);
        opsAnalyticsService = new ExcursionOpsAnalyticsService(excursionRepository, reviewRepository, guideRepository);

        mockExcursion = ShoreExcursion.builder()
                .tourId(10L)
                .portId(100L)
                .portName("Hạ Long Port")
                .dayNumber(1)
                .title("Hạ Long Bay Cruise & Kayak")
                .localProvider("Halong Heritage Tours")
                .excursionDate(LocalDate.now().plusDays(1))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(16, 0))
                .maxCapacity(50)
                .currentParticipants(20)
                .build();
        mockExcursion.setId(1L);

        attendedPassenger = ExcursionPassenger.builder()
                .excursionId(1L)
                .passengerId(201L)
                .passengerName("John Doe")
                .cabinNumber("A-101")
                .ticketCode("TCK-001")
                .status(ExcursionPassengerStatus.RETURNED_SAFE)
                .checkedInAt(Instant.now().minusSeconds(3600))
                .active(true)
                .build();
        attendedPassenger.setId(10L);

        registeredPassenger = ExcursionPassenger.builder()
                .excursionId(1L)
                .passengerId(202L)
                .passengerName("Jane Smith")
                .cabinNumber("B-202")
                .ticketCode("TCK-002")
                .status(ExcursionPassengerStatus.REGISTERED)
                .active(true)
                .build();
        registeredPassenger.setId(11L);
    }

    @Test
    @DisplayName("AC1 & AC2: Khách đã tham gia ATTENDED gửi review thành công")
    void submitReview_WhenAttended_ShouldSucceed() {
        ExcursionReviewRequest req = new ExcursionReviewRequest(201L, 5, 5, 4, 5, "Chuyến đi tuyệt vời!");

        when(excursionRepository.findById(1L)).thenReturn(Optional.of(mockExcursion));
        when(passengerRepository.findByExcursionIdAndActiveTrue(1L)).thenReturn(List.of(attendedPassenger));
        when(reviewRepository.existsByExcursionIdAndPassengerId(1L, 201L)).thenReturn(false);

        ExcursionReview saved = ExcursionReview.builder()
                .excursionId(1L)
                .passengerId(201L)
                .ratingOverall(5)
                .ratingGuide(5)
                .ratingBus(4)
                .ratingValue(5)
                .comments("Chuyến đi tuyệt vời!")
                .isFlagged(false)
                .build();
        saved.setId(99L);

        when(reviewRepository.save(any(ExcursionReview.class))).thenReturn(saved);

        ExcursionReviewResponse response = reviewService.submitReview(1L, req, "IDEM-1");

        assertThat(response).isNotNull();
        assertThat(response.ratingOverall()).isEqualTo(5);
        assertThat(response.isFlagged()).isFalse();
        verify(reviewRepository, times(1)).save(any(ExcursionReview.class));
    }

    @Test
    @DisplayName("AC2: Khách chưa tham gia (chỉ REGISTERED) bị từ chối 403 Forbidden")
    void submitReview_WhenNotAttended_ShouldThrowAccessDenied() {
        ExcursionReviewRequest req = new ExcursionReviewRequest(202L, 4, 4, 4, 4, "Chưa đi tour");

        when(excursionRepository.findById(1L)).thenReturn(Optional.of(mockExcursion));
        when(passengerRepository.findByExcursionIdAndActiveTrue(1L)).thenReturn(List.of(registeredPassenger));

        assertThatThrownBy(() -> reviewService.submitReview(1L, req, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ATTENDED");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC4: Đánh giá 1 sao hoặc phản ánh nguy hiểm tự động gắn cờ isFlagged=true")
    void submitReview_WhenOneStarOrDangerous_ShouldFlagReview() {
        ExcursionReviewRequest req = new ExcursionReviewRequest(201L, 1, 1, 1, 1, "Xe chạy quá nhanh, rất nguy hiểm!");

        when(excursionRepository.findById(1L)).thenReturn(Optional.of(mockExcursion));
        when(passengerRepository.findByExcursionIdAndActiveTrue(1L)).thenReturn(List.of(attendedPassenger));
        when(reviewRepository.existsByExcursionIdAndPassengerId(1L, 201L)).thenReturn(false);

        ExcursionReview saved = ExcursionReview.builder()
                .excursionId(1L)
                .passengerId(201L)
                .ratingOverall(1)
                .isFlagged(true)
                .flagReason("Đánh giá tiêu cực mức độ cao (1 sao)")
                .build();
        saved.setId(100L);

        when(reviewRepository.save(any(ExcursionReview.class))).thenReturn(saved);

        ExcursionReviewResponse response = reviewService.submitReview(1L, req, null);

        assertThat(response.isFlagged()).isTrue();
        assertThat(response.flagReason()).contains("1 sao");
    }

    @Test
    @DisplayName("Idempotency: Gửi lại cùng idempotencyKey trả về kết quả cũ")
    void submitReview_IdempotentKey_ReturnsExisting() {
        ExcursionReview existing = ExcursionReview.builder()
                .excursionId(1L)
                .passengerId(201L)
                .ratingOverall(5)
                .idempotencyKey("KEY-123")
                .isFlagged(false)
                .build();
        existing.setId(50L);

        when(reviewRepository.findByIdempotencyKey("KEY-123")).thenReturn(Optional.of(existing));

        ExcursionReviewRequest req = new ExcursionReviewRequest(201L, 5, 5, 5, 5, "Good");
        ExcursionReviewResponse response = reviewService.submitReview(1L, req, "KEY-123");

        assertThat(response.id()).isEqualTo(50L);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC3: Fleet Occupancy Aggregator tính toán chuẩn tỷ lệ lấp đầy ghế xe")
    void aggregateFleetOccupancy_ShouldCalculateCorrectly() {
        ExcursionVehicle v1 = ExcursionVehicle.builder()
                .excursionId(1L)
                .vehicleCode("BUS-01")
                .licensePlate("29B-12345")
                .vehicleType("45 Seats Bus")
                .driverName("Driver 1")
                .capacity(40)
                .assignedPassengers(30)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        v1.setId(501L);

        ExcursionVehicle v2 = ExcursionVehicle.builder()
                .excursionId(1L)
                .vehicleCode("BUS-02")
                .licensePlate("29B-67890")
                .vehicleType("45 Seats Bus")
                .driverName("Driver 2")
                .capacity(40)
                .assignedPassengers(10)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        v2.setId(502L);

        when(excursionRepository.findAll()).thenReturn(List.of(mockExcursion));
        when(vehicleRepository.findAll()).thenReturn(List.of(v1, v2));

        FleetOccupancyAnalyticsResponse response = fleetAggregator.aggregateFleetOccupancy(null, null);

        assertThat(response.totalFleetCount()).isEqualTo(2);
        assertThat(response.totalFleetCapacity()).isEqualTo(80);
        assertThat(response.totalPassengersSeated()).isEqualTo(40);
        assertThat(response.overallFleetOccupancyRate()).isEqualTo(50.0);
        assertThat(response.portSummaries()).hasSize(1);
        assertThat(response.portSummaries().get(0).occupancyRate()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("AC5: Ops KPI & Provider Scorecard phân loại đối tác chính xác")
    void opsKpi_ShouldGenerateScorecard() {
        ExcursionReview r1 = ExcursionReview.builder()
                .excursionId(1L)
                .passengerId(201L)
                .ratingOverall(5)
                .ratingGuide(5)
                .ratingBus(4)
                .ratingValue(5)
                .isFlagged(false)
                .build();
        r1.setId(1L);

        when(excursionRepository.findAll()).thenReturn(List.of(mockExcursion));
        when(reviewRepository.findAll()).thenReturn(List.of(r1));
        when(guideRepository.findAll()).thenReturn(Collections.emptyList());

        ExcursionOpsKpiResponse kpi = opsAnalyticsService.getOpsKpiMetrics();

        assertThat(kpi.totalExcursions()).isEqualTo(1L);
        assertThat(kpi.totalReviews()).isEqualTo(1L);
        assertThat(kpi.averageOverallRating()).isEqualTo(5.0);
        assertThat(kpi.providerScorecards()).hasSize(1);
        assertThat(kpi.providerScorecards().get(0).providerName()).isEqualTo("Halong Heritage Tours");
        assertThat(kpi.providerScorecards().get(0).performanceTier()).isEqualTo("EXCELLENT");
    }
}

package com.cruise.activity.service;

import com.cruise.activity.dto.OperationsKpiSummaryDto;
import com.cruise.activity.dto.OperationsLiveRadarDto;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ActivityAttendanceStatus;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationsDashboardService Unit Tests (KAN-272)")
class OperationsDashboardServiceTest {

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private EmbarkationCheckInLogRepository embarkationLogRepository;

    @Mock
    private OnboardActivityRepository activityRepository;

    @Mock
    private ActivityRegistrationRepository registrationRepository;

    @Mock
    private ShoreExcursionRepository excursionRepository;

    @Mock
    private ExcursionPassengerRepository excursionPassengerRepository;

    @Mock
    private ActivityFeedbackRepository feedbackRepository;

    @Mock
    private ActivityAttendanceRepository attendanceRepository;

    @InjectMocks
    private OperationsDashboardService dashboardService;

    @Test
    @DisplayName("getOperationsKpiSummary returns accurate aggregated KPIs with real feedback average and status-filtered activities")
    void testGetOperationsKpiSummary_Success() {
        when(passengerRepository.count()).thenReturn(2000L);
        when(embarkationLogRepository.count()).thenReturn(1500L);

        when(activityRepository.countByActiveTrueAndStatus(ActivityStatus.SCHEDULED)).thenReturn(1L);
        when(activityRepository.countByActiveTrueAndStatusIn(List.of(ActivityStatus.SCHEDULED, ActivityStatus.IN_PROGRESS))).thenReturn(2L);
        when(activityRepository.countActiveActivitiesGroupedByCategory()).thenReturn(List.of(
                new Object[]{"WELLNESS", 1L},
                new Object[]{"ENTERTAINMENT", 2L}
        ));
        when(registrationRepository.count()).thenReturn(450L);
        when(excursionRepository.count()).thenReturn(12L);
        when(excursionRepository.countByExcursionDateAndActiveTrue(any(LocalDate.class))).thenReturn(3L);
        when(excursionPassengerRepository.count()).thenReturn(800L);

        when(feedbackRepository.count()).thenReturn(25L);
        when(feedbackRepository.findAverageRating()).thenReturn(4.72);

        OperationsKpiSummaryDto summary = dashboardService.getOperationsKpiSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalPassengers()).isEqualTo(2000L);
        assertThat(summary.getCheckedInPassengers()).isEqualTo(1500L);
        assertThat(summary.getEmbarkationRate()).isEqualTo(75.0);

        // Only SCHEDULED activities
        assertThat(summary.getTotalActivitiesScheduled()).isEqualTo(1L);
        // SCHEDULED + IN_PROGRESS
        assertThat(summary.getTotalActivitiesActive()).isEqualTo(2L);

        assertThat(summary.getTotalActivityRegistrations()).isEqualTo(450L);
        assertThat(summary.getTotalShoreExcursions()).isEqualTo(12L);
        assertThat(summary.getDepartingExcursionsToday()).isEqualTo(3L);
        assertThat(summary.getTotalExcursionBookings()).isEqualTo(800L);

        // Feedback average rounded to 2 decimal places
        assertThat(summary.getAverageActivityRating()).isEqualTo(4.72);
        assertThat(summary.getTotalFeedbackSubmitted()).isEqualTo(25L);

        assertThat(summary.getActivitiesByCategory()).containsEntry("WELLNESS", 1L);
        assertThat(summary.getActivitiesByCategory()).containsEntry("ENTERTAINMENT", 2L);
    }

    @Test
    @DisplayName("getOperationsKpiSummary handles zero passenger and zero feedback edge cases")
    void testGetOperationsKpiSummary_ZeroFallbacks() {
        when(passengerRepository.count()).thenReturn(0L);
        when(embarkationLogRepository.count()).thenReturn(0L);
        when(activityRepository.countByActiveTrueAndStatus(ActivityStatus.SCHEDULED)).thenReturn(0L);
        when(activityRepository.countByActiveTrueAndStatusIn(List.of(ActivityStatus.SCHEDULED, ActivityStatus.IN_PROGRESS))).thenReturn(0L);
        when(activityRepository.countActiveActivitiesGroupedByCategory()).thenReturn(Collections.emptyList());
        when(registrationRepository.count()).thenReturn(0L);
        when(excursionRepository.count()).thenReturn(0L);
        when(excursionRepository.countByExcursionDateAndActiveTrue(any(LocalDate.class))).thenReturn(0L);
        when(excursionPassengerRepository.count()).thenReturn(0L);
        when(feedbackRepository.count()).thenReturn(0L);

        OperationsKpiSummaryDto summary = dashboardService.getOperationsKpiSummary();

        assertThat(summary.getEmbarkationRate()).isEqualTo(0.0);
        assertThat(summary.getAverageActivityRating()).isEqualTo(0.0);
        assertThat(summary.getTotalActivitiesScheduled()).isZero();
        assertThat(summary.getDepartingExcursionsToday()).isZero();
    }

    @Test
    @DisplayName("getLiveOperationsRadar derives actual attendance counts and departing soon flags")
    void testGetLiveOperationsRadar_Success() {
        OnboardActivity act1 = OnboardActivity.builder()
                .title("Cooking Masterclass")
                .location("Galley A")
                .startTime(LocalTime.of(14, 0))
                .maxCapacity(40)
                .currentParticipants(30)
                .build();
        act1.setId(101L);

        when(activityRepository.findLiveActivities(any())).thenReturn(List.of(act1));
        when(attendanceRepository.countCheckedInGroupedByActivityId(eq(List.of(101L)), eq(ActivityAttendanceStatus.CHECKED_IN)))
                .thenReturn(Collections.singletonList(new Object[]{101L, 25L}));

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime soonDateTime = now.plusMinutes(45);
        LocalDate today = now.toLocalDate();
        LocalTime farTime = now.toLocalTime().plusHours(5);

        ShoreExcursion excSoon = ShoreExcursion.builder()
                .title("Coral Reef Diving")
                .portName("Cozumel")
                .excursionDate(soonDateTime.toLocalDate())
                .departureTime(soonDateTime.toLocalTime())
                .status(ExcursionStatus.OPEN_FOR_BOOKING)
                .maxCapacity(50)
                .currentParticipants(45)
                .build();
        excSoon.setId(201L);

        ShoreExcursion excFar = ShoreExcursion.builder()
                .title("Mayan Ruins Tour")
                .portName("Progreso")
                .excursionDate(today.plusDays(1))
                .departureTime(farTime)
                .status(ExcursionStatus.SCHEDULED)
                .maxCapacity(80)
                .currentParticipants(60)
                .build();
        excFar.setId(202L);

        ShoreExcursion excMissingTime = ShoreExcursion.builder()
                .title("Mystery Island Tour")
                .portName("Private Island")
                .status(ExcursionStatus.SCHEDULED)
                .maxCapacity(30)
                .currentParticipants(10)
                .build();
        excMissingTime.setId(203L);

        when(excursionRepository.findLiveUpcomingExcursions(any(), any())).thenReturn(List.of(excSoon, excFar, excMissingTime));
        when(excursionPassengerRepository.countCheckedInGroupedByExcursionId(eq(List.of(201L, 202L, 203L))))
                .thenReturn(Collections.singletonList(new Object[]{201L, 40L}));

        OperationsLiveRadarDto radar = dashboardService.getLiveOperationsRadar();

        assertThat(radar).isNotNull();
        assertThat(radar.getActivityRadar()).hasSize(1);
        OperationsLiveRadarDto.ActivityAttendanceRadarItem actItem = radar.getActivityRadar().get(0);
        assertThat(actItem.getActivityId()).isEqualTo(101L);
        assertThat(actItem.getRegisteredCount()).isEqualTo(30);
        assertThat(actItem.getCheckedInCount()).isEqualTo(25);
        assertThat(actItem.getOccupancyRate()).isEqualTo(62.5);

        assertThat(radar.getExcursionRadar()).hasSize(3);

        // Soon departure
        OperationsLiveRadarDto.ExcursionDepartureRadarItem itemSoon = radar.getExcursionRadar().get(0);
        assertThat(itemSoon.getExcursionId()).isEqualTo(201L);
        assertThat(itemSoon.getBookedCount()).isEqualTo(45);
        assertThat(itemSoon.getPresentCount()).isEqualTo(40);
        assertThat(itemSoon.isDepartingSoon()).isTrue();

        // Far departure
        OperationsLiveRadarDto.ExcursionDepartureRadarItem itemFar = radar.getExcursionRadar().get(1);
        assertThat(itemFar.getExcursionId()).isEqualTo(202L);
        assertThat(itemFar.isDepartingSoon()).isFalse();

        // Missing departure time
        OperationsLiveRadarDto.ExcursionDepartureRadarItem itemMissing = radar.getExcursionRadar().get(2);
        assertThat(itemMissing.getExcursionId()).isEqualTo(203L);
        assertThat(itemMissing.getDepartureTime()).isEqualTo("TBD");
        assertThat(itemMissing.isDepartingSoon()).isFalse();
    }

    @Test
    @DisplayName("getLiveOperationsRadar and KPI summary handle null fallbacks gracefully")
    void testNullFallbacks() {
        OnboardActivity actWithNulls = OnboardActivity.builder()
                .title("Null Activity")
                .location("Lounge")
                .category(null)
                .maxCapacity(null)
                .currentParticipants(null)
                .startTime(null)
                .status(ActivityStatus.SCHEDULED)
                .build();
        actWithNulls.setId(999L);

        OnboardActivity actBlankCategory = OnboardActivity.builder()
                .title("Blank Cat")
                .location("Lounge")
                .category("   ")
                .maxCapacity(0)
                .currentParticipants(0)
                .startTime(LocalTime.of(10, 0))
                .status(ActivityStatus.SCHEDULED)
                .build();
        actBlankCategory.setId(998L);

        when(activityRepository.findLiveActivities(any())).thenReturn(List.of(actWithNulls, actBlankCategory));
        when(attendanceRepository.countCheckedInGroupedByActivityId(any(), any())).thenReturn(Collections.emptyList());

        ShoreExcursion excWithNulls = ShoreExcursion.builder()
                .title("Null Excursion")
                .portName("Port")
                .maxCapacity(null)
                .currentParticipants(null)
                .departureTime(null)
                .excursionDate(null)
                .status(null)
                .build();
        excWithNulls.setId(888L);

        when(excursionRepository.findLiveUpcomingExcursions(any(), any())).thenReturn(List.of(excWithNulls));
        when(excursionPassengerRepository.countCheckedInGroupedByExcursionId(any())).thenReturn(Collections.emptyList());

        OperationsLiveRadarDto radar = dashboardService.getLiveOperationsRadar();
        assertThat(radar.getActivityRadar()).hasSize(2);
        assertThat(radar.getActivityRadar().get(0).getCapacity()).isEqualTo(50);
        assertThat(radar.getActivityRadar().get(0).getScheduledTime()).isEqualTo("TBD");
        assertThat(radar.getActivityRadar().get(1).getOccupancyRate()).isEqualTo(0.0);

        assertThat(radar.getExcursionRadar()).hasSize(1);
        assertThat(radar.getExcursionRadar().get(0).getMaxParticipants()).isEqualTo(100);
        assertThat(radar.getExcursionRadar().get(0).getStatus()).isEqualTo("SCHEDULED");
        assertThat(radar.getExcursionRadar().get(0).isDepartingSoon()).isFalse();
    }
}

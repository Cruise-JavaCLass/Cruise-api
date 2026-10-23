package com.cruise.activity.service;

import com.cruise.activity.dto.OperationsKpiSummaryDto;
import com.cruise.activity.dto.OperationsLiveRadarDto;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ActivityAttendanceStatus;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationsDashboardService {

    private final PassengerRepository passengerRepository;
    private final EmbarkationCheckInLogRepository embarkationLogRepository;
    private final OnboardActivityRepository activityRepository;
    private final ActivityRegistrationRepository registrationRepository;
    private final ShoreExcursionRepository excursionRepository;
    private final ExcursionPassengerRepository excursionPassengerRepository;
    private final ActivityFeedbackRepository feedbackRepository;
    private final ActivityAttendanceRepository attendanceRepository;

    @Transactional(readOnly = true)
    public OperationsKpiSummaryDto getOperationsKpiSummary() {
        long totalPassengers = passengerRepository.count();
        long checkedInPassengers = embarkationLogRepository.count();
        double embarkationRate = totalPassengers > 0
                ? Math.round(((double) checkedInPassengers / totalPassengers) * 10000.0) / 100.0
                : 0.0;

        long scheduledActivities = activityRepository.countByActiveTrueAndStatus(ActivityStatus.SCHEDULED);
        long activeActivities = activityRepository.countByActiveTrueAndStatusIn(
                List.of(ActivityStatus.SCHEDULED, ActivityStatus.IN_PROGRESS)
        );

        long totalRegistrations = registrationRepository.count();
        long totalExcursions = excursionRepository.count();
        long totalExcursionBookings = excursionPassengerRepository.count();

        LocalDate today = getOperationalToday();
        long departingExcursionsToday = excursionRepository.countByExcursionDateAndActiveTrue(today);

        Map<String, Long> activitiesByCategory = activityRepository.countActiveActivitiesGroupedByCategory().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()
                ));

        long feedbackCount = feedbackRepository.count();
        Double dbAvg = feedbackCount > 0 ? feedbackRepository.findAverageRating() : null;
        double avgRating = dbAvg != null ? Math.round(dbAvg * 100.0) / 100.0 : 0.0;

        return OperationsKpiSummaryDto.builder()
                .totalPassengers(totalPassengers)
                .checkedInPassengers(checkedInPassengers)
                .embarkationRate(embarkationRate)
                .totalActivitiesScheduled(scheduledActivities)
                .totalActivitiesActive(activeActivities)
                .totalActivityRegistrations(totalRegistrations)
                .totalShoreExcursions(totalExcursions)
                .departingExcursionsToday(departingExcursionsToday)
                .totalExcursionBookings(totalExcursionBookings)
                .averageActivityRating(avgRating)
                .totalFeedbackSubmitted(feedbackCount)
                .activitiesByCategory(activitiesByCategory)
                .generatedAt(Instant.now())
                .build();
    }

    @Transactional(readOnly = true)
    public OperationsLiveRadarDto getLiveOperationsRadar() {
        List<OnboardActivity> activities = activityRepository.findLiveActivities(
                List.of(ActivityStatus.SCHEDULED, ActivityStatus.IN_PROGRESS)
        );
        List<Long> activityIds = activities.stream().map(OnboardActivity::getId).toList();
        Map<Long, Integer> checkedInByActivityId = activityIds.isEmpty() ? Collections.emptyMap() :
                attendanceRepository.countCheckedInGroupedByActivityId(activityIds, ActivityAttendanceStatus.CHECKED_IN).stream()
                        .collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> ((Number) row[1]).intValue()
                        ));

        List<OperationsLiveRadarDto.ActivityAttendanceRadarItem> activityItems = new ArrayList<>();

        for (OnboardActivity a : activities) {
            int capacity = a.getMaxCapacity() != null ? a.getMaxCapacity() : 50;
            int registered = a.getCurrentParticipants() != null ? a.getCurrentParticipants() : 0;
            int checkedIn = checkedInByActivityId.getOrDefault(a.getId(), 0);
            double occupancy = capacity > 0 ? Math.round(((double) checkedIn / capacity) * 1000.0) / 10.0 : 0.0;

            activityItems.add(OperationsLiveRadarDto.ActivityAttendanceRadarItem.builder()
                    .activityId(a.getId())
                    .title(a.getTitle())
                    .location(a.getLocation())
                    .scheduledTime(a.getStartTime() != null ? a.getStartTime().toString() : "TBD")
                    .capacity(capacity)
                    .registeredCount(registered)
                    .checkedInCount(checkedIn)
                    .occupancyRate(occupancy)
                    .build());
        }

        LocalDate today = getOperationalToday();
        List<ShoreExcursion> excursions = excursionRepository.findLiveUpcomingExcursions(
                List.of(ExcursionStatus.SCHEDULED, ExcursionStatus.OPEN_FOR_BOOKING, ExcursionStatus.IN_PROGRESS),
                today
        );
        List<Long> excursionIds = excursions.stream().map(ShoreExcursion::getId).toList();
        Map<Long, Integer> presentByExcursionId = excursionIds.isEmpty() ? Collections.emptyMap() :
                excursionPassengerRepository.countCheckedInGroupedByExcursionId(excursionIds).stream()
                        .collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> ((Number) row[1]).intValue()
                        ));

        List<OperationsLiveRadarDto.ExcursionDepartureRadarItem> excursionItems = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(getOperationalZoneId());

        for (ShoreExcursion e : excursions) {
            int maxPart = e.getMaxCapacity() != null ? e.getMaxCapacity() : 100;
            int booked = e.getCurrentParticipants() != null ? e.getCurrentParticipants() : 0;
            int present = presentByExcursionId.getOrDefault(e.getId(), 0);
            boolean departingSoon = calculateIsDepartingSoon(e, now);

            excursionItems.add(OperationsLiveRadarDto.ExcursionDepartureRadarItem.builder()
                    .excursionId(e.getId())
                    .title(e.getTitle())
                    .portName(e.getPortName())
                    .departureTime(e.getDepartureTime() != null ? e.getDepartureTime().toString() : "TBD")
                    .status(e.getStatus() != null ? e.getStatus().name() : "SCHEDULED")
                    .maxParticipants(maxPart)
                    .bookedCount(booked)
                    .presentCount(present)
                    .isDepartingSoon(departingSoon)
                    .build());
        }

        return OperationsLiveRadarDto.builder()
                .timestamp(Instant.now())
                .activityRadar(activityItems)
                .excursionRadar(excursionItems)
                .build();
    }

    private boolean calculateIsDepartingSoon(ShoreExcursion e, LocalDateTime now) {
        if (e.getDepartureTime() == null || e.getExcursionDate() == null) {
            return false;
        }
        LocalDateTime departure = LocalDateTime.of(e.getExcursionDate(), e.getDepartureTime());
        return !departure.isBefore(now.minusMinutes(15)) && !departure.isAfter(now.plusHours(2));
    }

    private LocalDate getOperationalToday() {
        return LocalDate.now(getOperationalZoneId());
    }

    private ZoneId getOperationalZoneId() {
        return ZoneId.systemDefault();
    }
}

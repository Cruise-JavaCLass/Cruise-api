package com.cruise.activity.service;

import com.cruise.activity.dto.ConfigureTimeAndLocationRequest;
import com.cruise.activity.dto.CreateActivityRequest;
import com.cruise.activity.dto.OnboardActivityResponse;
import com.cruise.activity.dto.UpdateActivityRequest;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.OnboardActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardActivityService {

    private final OnboardActivityRepository activityRepository;

    @Transactional
    public OnboardActivityResponse createActivity(CreateActivityRequest request) {
        validateTimeAndLocation(
                request.getLocation(),
                request.getActivityDate(),
                request.getStartTime(),
                request.getEndTime(),
                null
        );

        OnboardActivity activity = OnboardActivity.builder()
                .tourId(request.getTourId())
                .dayNumber(request.getDayNumber())
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory() != null ? request.getCategory() : "General")
                .location(request.getLocation().trim())
                .activityDate(request.getActivityDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .maxCapacity(request.getMaxCapacity() != null ? request.getMaxCapacity() : 50)
                .currentParticipants(0)
                .price(request.getPrice() != null ? request.getPrice() : java.math.BigDecimal.ZERO)
                .isIncluded(request.getIsIncluded() != null ? request.getIsIncluded() : true)
                .status(request.getStatus() != null ? request.getStatus() : ActivityStatus.SCHEDULED)
                .active(true)
                .build();

        OnboardActivity saved = activityRepository.save(activity);
        log.info("Created onboard activity: ID={}, Title={}, TourID={}", saved.getId(), saved.getTitle(), saved.getTourId());
        return OnboardActivityResponse.fromEntity(saved);
    }

    @Transactional
    public OnboardActivityResponse updateActivity(Long activityId, UpdateActivityRequest request) {
        OnboardActivity activity = activityRepository.findById(activityId)
                .filter(OnboardActivity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hoạt động với ID: " + activityId));

        validateTimeAndLocation(
                request.getLocation(),
                request.getActivityDate(),
                request.getStartTime(),
                request.getEndTime(),
                activityId
        );

        if (request.getDayNumber() != null) {
            activity.setDayNumber(request.getDayNumber());
        }
        activity.setTitle(request.getTitle().trim());
        activity.setDescription(request.getDescription());
        if (request.getCategory() != null) {
            activity.setCategory(request.getCategory());
        }
        activity.setLocation(request.getLocation().trim());
        activity.setActivityDate(request.getActivityDate());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        if (request.getMaxCapacity() != null) {
            activity.setMaxCapacity(request.getMaxCapacity());
        }
        if (request.getPrice() != null) {
            activity.setPrice(request.getPrice());
        }
        if (request.getIsIncluded() != null) {
            activity.setIsIncluded(request.getIsIncluded());
        }
        if (request.getStatus() != null) {
            activity.setStatus(request.getStatus());
        }

        OnboardActivity updated = activityRepository.save(activity);
        log.info("Updated activity ID={}: Title='{}', Location='{}'", updated.getId(), updated.getTitle(), updated.getLocation());
        return OnboardActivityResponse.fromEntity(updated);
    }

    @Transactional
    public OnboardActivityResponse configureTimeAndLocation(Long activityId, ConfigureTimeAndLocationRequest request) {
        OnboardActivity activity = activityRepository.findById(activityId)
                .filter(OnboardActivity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hoạt động với ID: " + activityId));

        LocalDate targetDate = request.getActivityDate() != null ? request.getActivityDate() : activity.getActivityDate();

        validateTimeAndLocation(
                request.getLocation(),
                targetDate,
                request.getStartTime(),
                request.getEndTime(),
                activityId
        );

        activity.setLocation(request.getLocation().trim());
        activity.setActivityDate(targetDate);
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());

        OnboardActivity updated = activityRepository.save(activity);
        log.info("Configured time and location for activity ID={}: Location='{}', Date={}, Start={}, End={}",
                updated.getId(), updated.getLocation(), updated.getActivityDate(), updated.getStartTime(), updated.getEndTime());
        return OnboardActivityResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public OnboardActivityResponse getActivityById(Long id) {
        return activityRepository.findById(id)
                .filter(OnboardActivity::getActive)
                .map(OnboardActivityResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hoạt động với ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<OnboardActivityResponse> getActivitiesByTour(Long tourId) {
        return activityRepository.findByTourIdAndActiveTrueOrderByActivityDateAscStartTimeAsc(tourId)
                .stream()
                .map(OnboardActivityResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OnboardActivityResponse> getActivitiesByTourAndDay(Long tourId, Integer dayNumber) {
        return activityRepository.findByTourIdAndDayNumberAndActiveTrueOrderByStartTimeAsc(tourId, dayNumber)
                .stream()
                .map(OnboardActivityResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<OnboardActivityResponse> getActivitiesPaged(
            Long tourId,
            Integer dayNumber,
            String category,
            String keyword,
            Pageable pageable
    ) {
        return activityRepository.findPagedActivities(tourId, dayNumber, category, keyword, pageable)
                .map(OnboardActivityResponse::fromEntity);
    }

    @Transactional
    public void deleteActivity(Long id) {
        OnboardActivity activity = activityRepository.findById(id)
                .filter(OnboardActivity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hoạt động với ID: " + id));
        activity.setActive(false);
        activity.setStatus(ActivityStatus.CANCELLED);
        activityRepository.save(activity);
        log.info("Soft-deleted activity ID={}", id);
    }

    public void validateTimeAndLocation(
            String location,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            Long excludeId
    ) {
        if (location == null || location.trim().isEmpty()) {
            throw new BusinessRuleException("Địa điểm tổ chức không được để trống");
        }
        if (date == null) {
            throw new BusinessRuleException("Ngày tổ chức không được để trống");
        }
        if (startTime == null || endTime == null) {
            throw new BusinessRuleException("Thời gian bắt đầu và kết thúc không được để trống");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessRuleException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        boolean hasOverlap = activityRepository.existsOverlappingActivity(
                location.trim(),
                date,
                startTime,
                endTime,
                excludeId
        );

        if (hasOverlap) {
            throw new BusinessRuleException(String.format(
                    "Xung đột lịch trình: Địa điểm '%s' vào ngày %s đã có hoạt động khác diễn ra trong khung giờ %s - %s",
                    location.trim(), date, startTime, endTime
            ));
        }
    }
}

package com.cruise.activity.repository;

import com.cruise.activity.entity.OnboardActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OnboardActivityRepository extends JpaRepository<OnboardActivity, Long> {

    List<OnboardActivity> findByTourIdAndActiveTrueOrderByActivityDateAscStartTimeAsc(Long tourId);

    List<OnboardActivity> findByTourIdAndDayNumberAndActiveTrueOrderByStartTimeAsc(Long tourId, Integer dayNumber);

    List<OnboardActivity> findByTourIdAndActivityDateAndActiveTrueOrderByStartTimeAsc(Long tourId, LocalDate activityDate);

    List<OnboardActivity> findByLocationAndActivityDateAndActiveTrue(String location, LocalDate activityDate);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM OnboardActivity a WHERE a.id = :id")
    Optional<OnboardActivity> findByIdForUpdate(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM OnboardActivity a WHERE a.id = :id")
    Optional<OnboardActivity> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT a FROM OnboardActivity a WHERE a.tourId = :tourId AND a.active = true " +
            "AND (:dayNumber IS NULL OR a.dayNumber = :dayNumber) " +
            "AND (:category IS NULL OR LOWER(a.category) = LOWER(:category)) " +
            "AND (:keyword IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR LOWER(a.location) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<OnboardActivity> findPagedActivities(
            @Param("tourId") Long tourId,
            @Param("dayNumber") Integer dayNumber,
            @Param("category") String category,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("SELECT COUNT(a) > 0 FROM OnboardActivity a WHERE a.active = true " +
            "AND a.location = :location " +
            "AND a.activityDate = :activityDate " +
            "AND (:excludeId IS NULL OR a.id <> :excludeId) " +
            "AND (a.startTime < :endTime AND a.endTime > :startTime)")
    boolean existsOverlappingActivity(
            @Param("location") String location,
            @Param("activityDate") LocalDate activityDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") Long excludeId
    );
}

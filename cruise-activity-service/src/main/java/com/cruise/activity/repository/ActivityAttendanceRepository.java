package com.cruise.activity.repository;

import com.cruise.activity.entity.ActivityAttendance;
import com.cruise.activity.enums.ActivityAttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityAttendanceRepository extends JpaRepository<ActivityAttendance, Long> {

    List<ActivityAttendance> findByActivityIdAndActiveTrue(Long activityId);

    Optional<ActivityAttendance> findByActivityIdAndPassengerIdAndActiveTrue(Long activityId, Long passengerId);

    Optional<ActivityAttendance> findByActivityIdAndRfidTagAndActiveTrue(Long activityId, String rfidTag);

    List<ActivityAttendance> findByActivityIdAndCabinNumberAndActiveTrue(Long activityId, String cabinNumber);

    long countByActivityIdAndActiveTrue(Long activityId);

    long countByActivityIdAndStatusAndActiveTrue(Long activityId, ActivityAttendanceStatus status);

    long countByActivityIdAndIsWalkInTrueAndActiveTrue(Long activityId);

    @Query("SELECT a FROM ActivityAttendance a WHERE a.activityId = :activityId AND a.active = true " +
            "AND (:status IS NULL OR a.status = :status) " +
            "AND (:keyword IS NULL OR LOWER(a.passengerName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR LOWER(a.cabinNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR LOWER(a.rfidTag) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ActivityAttendance> searchRoster(
            @Param("activityId") Long activityId,
            @Param("status") ActivityAttendanceStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}

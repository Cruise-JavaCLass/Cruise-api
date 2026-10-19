package com.cruise.activity.repository;

import com.cruise.activity.entity.ActivityRegistration;
import com.cruise.activity.enums.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityRegistrationRepository extends JpaRepository<ActivityRegistration, Long> {

    Optional<ActivityRegistration> findByActivityIdAndPassengerIdAndActiveTrue(Long activityId, Long passengerId);

    Optional<ActivityRegistration> findByActivityIdAndPassengerIdAndStatusInAndActiveTrue(
            Long activityId, Long passengerId, List<RegistrationStatus> statuses);

    List<ActivityRegistration> findByActivityIdAndStatusAndActiveTrue(Long activityId, RegistrationStatus status);

    List<ActivityRegistration> findByActivityIdAndStatusAndActiveTrueOrderByRegisteredAtAscIdAsc(
            Long activityId, RegistrationStatus status);

    List<ActivityRegistration> findByPassengerIdAndActiveTrueOrderByRegisteredAtDesc(Long passengerId);

    long countByActivityIdAndStatusAndActiveTrue(Long activityId, RegistrationStatus status);

    @Query("SELECT r FROM ActivityRegistration r WHERE r.activityId = :activityId AND r.active = true " +
            "AND (:status IS NULL OR r.status = :status) " +
            "AND (:keyword IS NULL OR LOWER(r.passengerName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR LOWER(r.cabinNumber) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ActivityRegistration> findRosterPaged(
            @Param("activityId") Long activityId,
            @Param("keyword") String keyword,
            @Param("status") RegistrationStatus status,
            Pageable pageable
    );
}

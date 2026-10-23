package com.cruise.activity.repository;

import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.enums.ExcursionBookingStatus;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository quản lý dữ liệu hành khách tour bờ (KAN-176, KAN-324, KAN-325, KAN-374)
 */
@Repository
public interface ExcursionPassengerRepository extends JpaRepository<ExcursionPassenger, Long> {

    List<ExcursionPassenger> findByExcursionIdAndActiveTrue(Long excursionId);

    Optional<ExcursionPassenger> findByIdAndExcursionIdAndActiveTrue(Long id, Long excursionId);

    List<ExcursionPassenger> findByExcursionIdAndIdInAndActiveTrue(Long excursionId, List<Long> ids);

    boolean existsByTicketCode(String ticketCode);

    List<ExcursionPassenger> findByExcursionIdAndGroupNameAndActiveTrue(Long excursionId, String groupName);

    long countByExcursionIdAndActiveTrue(Long excursionId);

    long countByExcursionId(Long excursionId);

    long countByExcursionIdAndBookingStatusAndActiveTrue(Long excursionId, ExcursionBookingStatus bookingStatus);

    @Query("SELECT COUNT(ep) FROM ExcursionPassenger ep WHERE ep.excursionId = :excursionId AND ep.checkedInAt IS NOT NULL AND ep.active = true")
    long countCheckedInByExcursionId(@Param("excursionId") Long excursionId);

    @Query("SELECT ep.excursionId, COUNT(ep) FROM ExcursionPassenger ep " +
            "WHERE ep.excursionId IN :excursionIds AND ep.checkedInAt IS NOT NULL AND ep.active = true " +
            "GROUP BY ep.excursionId")
    List<Object[]> countCheckedInGroupedByExcursionId(@Param("excursionIds") java.util.Collection<Long> excursionIds);

    Optional<ExcursionPassenger> findByExcursionIdAndTicketCodeAndActiveTrue(Long excursionId, String ticketCode);

    Optional<ExcursionPassenger> findByExcursionIdAndRfidTagAndActiveTrue(Long excursionId, String rfidTag);

    List<ExcursionPassenger> findByExcursionIdAndBusNumberAndActiveTrue(Long excursionId, String busNumber);

    long countByExcursionIdAndBusNumberAndActiveTrue(Long excursionId, String busNumber);

    List<ExcursionPassenger> findByExcursionIdAndStatusAndActiveTrue(Long excursionId, ExcursionPassengerStatus status);

    List<ExcursionPassenger> findByCabinNumberAndActiveTrue(String cabinNumber);

    List<ExcursionPassenger> findByExcursionIdAndCabinNumberAndActiveTrue(Long excursionId, String cabinNumber);

    List<ExcursionPassenger> findByPassengerNameIgnoreCaseAndActiveTrue(String passengerName);

    List<ExcursionPassenger> findByPassengerIdAndActiveTrue(Long passengerId);

    Optional<ExcursionPassenger> findByExcursionIdAndPassengerIdAndActiveTrue(Long excursionId, Long passengerId);

    List<ExcursionPassenger> findByExcursionIdAndBookingStatusAndActiveTrueOrderByWaitlistPositionAsc(
            Long excursionId, ExcursionBookingStatus bookingStatus);

    @Query("SELECT MAX(ep.waitlistPosition) FROM ExcursionPassenger ep WHERE ep.excursionId = :excursionId AND ep.bookingStatus = 'WAITLISTED' AND ep.active = true")
    Integer findMaxWaitlistPosition(@Param("excursionId") Long excursionId);

    @Query("""
        SELECT ep FROM ExcursionPassenger ep
        WHERE ep.excursionId = :excursionId
          AND (:search IS NULL OR LOWER(ep.passengerName) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(ep.cabinNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(ep.ticketCode) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:bookingStatus IS NULL OR ep.bookingStatus = :bookingStatus)
          AND (:specialAssistance IS NULL
               OR (:specialAssistance = true AND ep.specialRequirements IS NOT NULL AND TRIM(ep.specialRequirements) <> '')
               OR (:specialAssistance = false AND (ep.specialRequirements IS NULL OR TRIM(ep.specialRequirements) = '')))
          AND (:includeCancelled = true OR ep.active = true)
        ORDER BY ep.bookingStatus ASC, ep.waitlistPosition ASC, ep.id ASC
    """)
    Page<ExcursionPassenger> findRosterWithFilters(
            @Param("excursionId") Long excursionId,
            @Param("search") String search,
            @Param("bookingStatus") ExcursionBookingStatus bookingStatus,
            @Param("specialAssistance") Boolean specialAssistance,
            @Param("includeCancelled") Boolean includeCancelled,
            Pageable pageable
    );
}

package com.cruise.activity.repository;

import com.cruise.activity.entity.Passenger;
import com.cruise.activity.enums.CheckInStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, Long> {

    List<Passenger> findByTourIdAndActiveTrue(Long tourId);

    Page<Passenger> findByTourIdAndActiveTrue(Long tourId, Pageable pageable);

    List<Passenger> findByTourIdAndRegistrationStatusAndActiveTrue(
            Long tourId,
            com.cruise.activity.enums.RegistrationStatus registrationStatus
    );

    Page<Passenger> findByTourIdAndRegistrationStatusAndActiveTrue(
            Long tourId,
            com.cruise.activity.enums.RegistrationStatus registrationStatus,
            Pageable pageable
    );

    List<Passenger> findByTourIdAndCheckInStatusAndActiveTrue(Long tourId, CheckInStatus checkInStatus);

    Optional<Passenger> findByRfidTag(String rfidTag);
    List<Passenger> findAllByRfidTagIgnoreCase(String rfidTag);

    Optional<Passenger> findByRfidTagAndActiveTrue(String rfidTag);

    Optional<Passenger> findByQrCodeAndActiveTrue(String qrCode);

    Optional<Passenger> findByPassportNumberAndActiveTrue(String passportNumber);

    Optional<Passenger> findByBookingCodeAndActiveTrue(String bookingCode);

    List<Passenger> findByCabinNumberAndActiveTrue(String cabinNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Passenger p WHERE p.id = :id")
    Optional<Passenger> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT p FROM Passenger p WHERE p.active = true AND (" +
            "LOWER(p.passportNumber) = LOWER(:keyword) OR " +
            "LOWER(p.bookingCode) = LOWER(:keyword) OR " +
            "LOWER(p.qrCode) = LOWER(:keyword) OR " +
            "LOWER(p.rfidTag) = LOWER(:keyword))")
    List<Passenger> findExactIdentifierForEmbarkation(@Param("keyword") String keyword);

    @Query("SELECT p FROM Passenger p WHERE p.active = true AND " +
            "LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'")
    List<Passenger> findByFullNameLikeIgnoreCaseForEmbarkation(@Param("keyword") String keyword);

    @Query("SELECT p FROM Passenger p WHERE p.active = true AND (" +
            "LOWER(p.passportNumber) = LOWER(:keyword) OR " +
            "LOWER(p.bookingCode) = LOWER(:keyword) OR " +
            "LOWER(p.qrCode) = LOWER(:keyword) OR " +
            "LOWER(p.rfidTag) = LOWER(:keyword) OR " +
            "LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Passenger> searchForEmbarkationLookup(@Param("keyword") String keyword);

    @Query("SELECT p FROM Passenger p WHERE p.tourId = :tourId AND p.active = true AND " +
            "(:status IS NULL OR p.registrationStatus = :status) AND " +
            "(:keyword IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.cabinNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.rfidTag) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Passenger> searchPassengersWithStatus(
            @Param("tourId") Long tourId,
            @Param("keyword") String keyword,
            @Param("status") com.cruise.activity.enums.RegistrationStatus status,
            Pageable pageable
    );

    long countByTourIdAndActiveTrue(Long tourId);

    long countByTourIdAndRegistrationStatusAndActiveTrue(
            Long tourId,
            com.cruise.activity.enums.RegistrationStatus registrationStatus
    );

    long countByTourIdAndCheckInStatusAndActiveTrue(Long tourId, CheckInStatus checkInStatus);
}

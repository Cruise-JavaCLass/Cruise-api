package com.cruise.activity.repository;

import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository quản lý dữ liệu hành khách tour bờ (KAN-176, KAN-374)
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
}

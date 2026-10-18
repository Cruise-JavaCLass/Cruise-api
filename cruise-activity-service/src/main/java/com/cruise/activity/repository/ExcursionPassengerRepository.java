package com.cruise.activity.repository;

import com.cruise.activity.entity.ExcursionPassenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository quản lý dữ liệu hành khách tour bờ (KAN-176)
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

    /**
     * Finds active excursion bookings with the given cabin number.
     *
     * @param cabinNumber cabin number to match
     * @return matching active bookings, or an empty list if none match
     */
    List<ExcursionPassenger> findByCabinNumberAndActiveTrue(String cabinNumber);

    /**
     * Finds active excursion bookings by passenger name, ignoring case.
     *
     * @param passengerName passenger name to match
     * @return matching active bookings, or an empty list if none match
     */
    List<ExcursionPassenger> findByPassengerNameIgnoreCaseAndActiveTrue(String passengerName);
}

package com.cruise.activity.dining.repository;

import com.cruise.activity.dining.entity.DiningTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {

    @Query("""
        SELECT t FROM DiningTable t
        WHERE t.restaurant.id = :restaurantId
          AND t.capacity >= :guestCount
          AND NOT EXISTS (
              SELECT r.id FROM DiningReservation r
              WHERE r.diningTable.id = t.id
                AND r.status <> com.cruise.activity.dining.enums.ReservationStatus.CANCELLED
                AND r.reservationTime = :timeSlot
          )
        """)
    List<DiningTable> findAvailableTables(
            @Param("restaurantId") Long restaurantId,
            @Param("timeSlot") LocalDateTime timeSlot,
            @Param("guestCount") Integer guestCount
    );
}
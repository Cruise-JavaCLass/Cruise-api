package com.cruise.activity.dining.repository;

import com.cruise.activity.dining.entity.DiningReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DiningReservationRepository
        extends JpaRepository<DiningReservation, Long> {

    @Query("""
        SELECT r FROM DiningReservation r
        WHERE r.passengerId = :passengerId
          AND r.reservationTime = :timeSlot
          AND r.status <> com.cruise.activity.dining.enums.ReservationStatus.CANCELLED
        """)
    List<DiningReservation> findConflictingReservations(
            @Param("passengerId") Long passengerId,
            @Param("timeSlot") LocalDateTime timeSlot
    );

    List<DiningReservation> findByPassengerId(Long passengerId);
}
package com.cruise.activity.repository;

import com.cruise.activity.entity.ExcursionBookingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExcursionBookingLogRepository extends JpaRepository<ExcursionBookingLog, Long> {

    List<ExcursionBookingLog> findByBookingIdOrderByAuditTimeDesc(Long bookingId);

    List<ExcursionBookingLog> findByExcursionIdOrderByAuditTimeDesc(Long excursionId);

    List<ExcursionBookingLog> findByPassengerIdOrderByAuditTimeDesc(Long passengerId);
}

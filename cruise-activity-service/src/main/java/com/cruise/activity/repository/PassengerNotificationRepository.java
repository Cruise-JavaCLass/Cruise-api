package com.cruise.activity.repository;

import com.cruise.activity.entity.PassengerNotification;
import com.cruise.activity.enums.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PassengerNotificationRepository extends JpaRepository<PassengerNotification, Long> {

    List<PassengerNotification> findByPassengerIdOrderByCreatedAtDesc(Long passengerId);

    List<PassengerNotification> findByPassengerIdAndCategoryOrderByCreatedAtDesc(Long passengerId, NotificationCategory category);

    long countByPassengerIdAndIsReadFalse(Long passengerId);

    long countByPassengerIdAndCategoryAndIsReadFalse(Long passengerId, NotificationCategory category);

    Optional<PassengerNotification> findByIdAndPassengerId(Long id, Long passengerId);

    @Modifying
    @Query("UPDATE PassengerNotification n SET n.isRead = true, n.readAt = :readAt WHERE n.passengerId = :passengerId AND n.isRead = false")
    int markAllAsRead(@Param("passengerId") Long passengerId, @Param("readAt") Instant readAt);
}

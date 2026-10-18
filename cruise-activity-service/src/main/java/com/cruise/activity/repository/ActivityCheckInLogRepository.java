package com.cruise.activity.repository;

import com.cruise.activity.entity.ActivityCheckInLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityCheckInLogRepository extends JpaRepository<ActivityCheckInLog, Long> {

    List<ActivityCheckInLog> findByActivityIdAndActiveTrueOrderByCheckInTimeDesc(Long activityId);

    List<ActivityCheckInLog> findByPassengerIdAndActiveTrueOrderByCheckInTimeDesc(Long passengerId);
}

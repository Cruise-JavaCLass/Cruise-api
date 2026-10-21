package com.cruise.activity.repository;

import com.cruise.activity.entity.EmbarkationCheckInLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmbarkationCheckInLogRepository extends JpaRepository<EmbarkationCheckInLog, Long> {

    List<EmbarkationCheckInLog> findByPassengerId(Long passengerId);

    List<EmbarkationCheckInLog> findByTourId(Long tourId);
}

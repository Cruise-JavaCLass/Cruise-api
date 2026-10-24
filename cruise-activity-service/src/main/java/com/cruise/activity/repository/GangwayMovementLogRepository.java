package com.cruise.activity.repository;

import com.cruise.activity.entity.GangwayMovementLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GangwayMovementLogRepository extends JpaRepository<GangwayMovementLog, Long> {

    List<GangwayMovementLog> findByTourIdAndPortCallIdOrderByScannedAtDesc(Long tourId, Long portCallId);

    @Query("SELECT g FROM GangwayMovementLog g WHERE g.tourId = :tourId AND g.portCallId = :portCallId AND g.passengerId = :passengerId ORDER BY g.scannedAt DESC LIMIT 1")
    Optional<GangwayMovementLog> findLatestMovement(
            @Param("tourId") Long tourId,
            @Param("portCallId") Long portCallId,
            @Param("passengerId") Long passengerId
    );
}

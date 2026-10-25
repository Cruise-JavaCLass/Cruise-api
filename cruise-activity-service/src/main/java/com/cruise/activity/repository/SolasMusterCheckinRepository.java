package com.cruise.activity.repository;

import com.cruise.activity.entity.SolasMusterCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SolasMusterCheckinRepository extends JpaRepository<SolasMusterCheckin, Long> {
    long countByIncidentId(Long incidentId);
    long countByIncidentIdAndMusterStationCode(Long incidentId, String musterStationCode);
    Optional<SolasMusterCheckin> findByIncidentIdAndPassengerId(Long incidentId, Long passengerId);
    List<SolasMusterCheckin> findByIncidentId(Long incidentId);
}

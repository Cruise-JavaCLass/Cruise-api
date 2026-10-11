package com.cruise.itinerary.repository;

import com.cruise.itinerary.entity.CruiseStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CruiseStopRepository extends JpaRepository<CruiseStop, Long> {

    List<CruiseStop> findByTourIdAndActiveTrueOrderByStopOrderAsc(Long tourId);

    List<CruiseStop> findByTourIdOrderByStopOrderAsc(Long tourId);

    boolean existsByTourIdAndStopOrderAndActiveTrue(Long tourId, Integer stopOrder);

    boolean existsByTourIdAndStopOrderAndIdNotAndActiveTrue(Long tourId, Integer stopOrder, Long id);
}

package com.cruise.itinerary.repository;

import com.cruise.itinerary.entity.DailyItinerary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyItineraryRepository extends JpaRepository<DailyItinerary, Long> {

    List<DailyItinerary> findByTourIdAndActiveTrueOrderByDayNumberAsc(Long tourId);

    Optional<DailyItinerary> findByIdAndTourIdAndActiveTrue(Long id, Long tourId);

    boolean existsByTourIdAndDayNumberAndActiveTrue(Long tourId, Integer dayNumber);

    List<DailyItinerary> findByTourIdAndItineraryDateAndActiveTrue(Long tourId, LocalDate itineraryDate);
}

package com.cruise.itinerary.repository;

import com.cruise.itinerary.entity.ItineraryChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItineraryChangeHistoryRepository extends JpaRepository<ItineraryChangeHistory, Long> {

    List<ItineraryChangeHistory> findByTourIdOrderByCreatedAtDesc(Long tourId);

    List<ItineraryChangeHistory> findByDailyItineraryIdOrderByCreatedAtDesc(Long dailyItineraryId);
}

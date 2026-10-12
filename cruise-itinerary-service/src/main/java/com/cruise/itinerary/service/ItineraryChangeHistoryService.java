package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.ItineraryChangeHistoryResponse;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.entity.DailyItinerary;
import com.cruise.itinerary.entity.ItineraryChangeHistory;
import com.cruise.itinerary.enums.ChangeType;
import com.cruise.itinerary.repository.CruiseTourRepository;
import com.cruise.itinerary.repository.ItineraryChangeHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ItineraryChangeHistoryService {

    private final ItineraryChangeHistoryRepository historyRepository;
    private final CruiseTourRepository tourRepository;

    public ItineraryChangeHistoryService(
            ItineraryChangeHistoryRepository historyRepository,
            CruiseTourRepository tourRepository
    ) {
        this.historyRepository = historyRepository;
        this.tourRepository = tourRepository;
    }

    public void recordChange(
            CruiseTour tour,
            DailyItinerary dailyItinerary,
            ChangeType changeType,
            String reason,
            String changedBy,
            String previousState,
            String newState
    ) {
        ItineraryChangeHistory history = ItineraryChangeHistory.builder()
                .tour(tour)
                .dailyItinerary(dailyItinerary)
                .changeType(changeType)
                .reason(reason)
                .changedBy(changedBy != null ? changedBy : "SYSTEM")
                .previousState(previousState)
                .newState(newState)
                .build();
        historyRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<ItineraryChangeHistoryResponse> getHistoriesByTourId(Long tourId) {
        return historyRepository.findByTourIdOrderByCreatedAtDesc(tourId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ItineraryChangeHistoryResponse> getHistoriesByDailyItineraryId(Long dailyItineraryId) {
        return historyRepository.findByDailyItineraryIdOrderByCreatedAtDesc(dailyItineraryId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ItineraryChangeHistoryResponse toResponse(ItineraryChangeHistory history) {
        return ItineraryChangeHistoryResponse.builder()
                .id(history.getId())
                .tourId(history.getTour().getId())
                .dailyItineraryId(history.getDailyItinerary() != null ? history.getDailyItinerary().getId() : null)
                .changeType(history.getChangeType())
                .reason(history.getReason())
                .changedBy(history.getChangedBy())
                .previousState(history.getPreviousState())
                .newState(history.getNewState())
                .createdAt(history.getCreatedAt())
                .build();
    }
}

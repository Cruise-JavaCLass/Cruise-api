package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.CruiseStopRequest;
import com.cruise.itinerary.entity.CruiseStop;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.repository.CruiseStopRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Validator cho các quy tắc xếp lịch điểm dừng và kiểm tra thời gian (KAN-107, KAN-108)
 */
@Component
public class StopValidator {

    private final CruiseStopRepository stopRepository;

    public StopValidator(CruiseStopRepository stopRepository) {
        this.stopRepository = stopRepository;
    }

    public void validateStopSchedule(CruiseTour tour, CruiseStopRequest request, Long currentStopId) {
        LocalDateTime arrival = request.getArrivalTime();
        LocalDateTime departure = request.getDepartureTime();
        LocalDateTime allAboard = request.getAllAboardTime();

        // Rule 1: arrivalTime must be strictly before departureTime
        if (!arrival.isBefore(departure)) {
            throw new IllegalArgumentException("Thời gian cập cảng (arrival) phải trước thời gian rời cảng (departure)");
        }

        // Rule 2: allAboardTime must be between arrivalTime and departureTime if provided
        if (allAboard != null) {
            if (allAboard.isBefore(arrival) || allAboard.isAfter(departure)) {
                throw new IllegalArgumentException("Thời gian lên tàu (all-aboard) phải nằm giữa thời gian cập cảng và rời cảng");
            }
        }

        // Rule 3: Stop dates must be within tour dates
        LocalDateTime tourStart = tour.getStartDate().atStartOfDay();
        LocalDateTime tourEnd = tour.getEndDate().atTime(23, 59, 59);

        if (arrival.isBefore(tourStart)) {
            throw new IllegalArgumentException("Thời gian cập cảng không thể diễn ra trước ngày bắt đầu tour (" + tour.getStartDate() + ")");
        }

        if (departure.isAfter(tourEnd)) {
            throw new IllegalArgumentException("Thời gian rời cảng không thể vượt quá ngày kết thúc tour (" + tour.getEndDate() + ")");
        }

        // Rule 4: Sequential validation against existing stops
        List<CruiseStop> existingStops = stopRepository.findByTourIdAndActiveTrueOrderByStopOrderAsc(tour.getId());

        for (CruiseStop other : existingStops) {
            if (currentStopId != null && other.getId().equals(currentStopId)) {
                continue;
            }

            // Stop earlier in order must have departure before this stop's arrival
            if (other.getStopOrder() < request.getStopOrder()) {
                if (other.getDepartureTime().isAfter(arrival)) {
                    throw new IllegalArgumentException("Điểm dừng thứ tự " + request.getStopOrder() +
                            " không thể cập cảng trước khi điểm dừng thứ tự " + other.getStopOrder() + " rời cảng");
                }
            }

            // Stop later in order must have arrival after this stop's departure
            if (other.getStopOrder() > request.getStopOrder()) {
                if (departure.isAfter(other.getArrivalTime())) {
                    throw new IllegalArgumentException("Điểm dừng thứ tự " + request.getStopOrder() +
                            " không thể rời cảng sau khi điểm dừng thứ tự " + other.getStopOrder() + " đã cập cảng");
                }
            }
        }
    }
}

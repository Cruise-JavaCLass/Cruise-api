package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.DailyItineraryRequest;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.entity.DailyItinerary;
import com.cruise.itinerary.repository.DailyItineraryRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Validator kiểm tra các quy tắc xếp lịch trình hàng ngày (Daily Itinerary Scheduling Rules - KAN-116)
 */
@Component
public class DailyItineraryValidator {

    private final DailyItineraryRepository dailyItineraryRepository;

    public DailyItineraryValidator(DailyItineraryRepository dailyItineraryRepository) {
        this.dailyItineraryRepository = dailyItineraryRepository;
    }

    public void validateDailyItinerary(CruiseTour tour, DailyItineraryRequest request, Long currentItineraryId) {
        Integer dayNumber = request.getDayNumber();
        LocalDate itineraryDate = request.getItineraryDate();
        LocalTime arrivalTime = request.getArrivalTime();
        LocalTime departureTime = request.getDepartureTime();
        LocalTime allAboardTime = request.getAllAboardTime();
        Boolean isSeaDay = Boolean.TRUE.equals(request.getIsSeaDay());

        // Quy tắc 1: Thứ tự ngày hợp lệ và nằm trong phạm vi tour
        if (dayNumber == null || dayNumber < 1) {
            throw new IllegalArgumentException("Số thứ tự ngày phải lớn hơn hoặc bằng 1");
        }
        if (tour.getDurationDays() != null && dayNumber > tour.getDurationDays()) {
            throw new IllegalArgumentException("Số thứ tự ngày không được vượt quá tổng thời lượng tour (" + tour.getDurationDays() + " ngày)");
        }

        // Quy tắc 2: Ngày lịch trình phải nằm trong khoảng thời gian tour
        if (tour.getStartDate() != null && itineraryDate.isBefore(tour.getStartDate())) {
            throw new IllegalArgumentException("Ngày lịch trình không được trước ngày bắt đầu tour (" + tour.getStartDate() + ")");
        }
        if (tour.getEndDate() != null && itineraryDate.isAfter(tour.getEndDate())) {
            throw new IllegalArgumentException("Ngày lịch trình không được sau ngày kết thúc tour (" + tour.getEndDate() + ")");
        }

        // Quy tắc 3: Ngày lịch trình phải khớp với số thứ tự ngày tính từ ngày bắt đầu
        if (tour.getStartDate() != null) {
            LocalDate expectedDate = tour.getStartDate().plusDays(dayNumber - 1L);
            if (!itineraryDate.isEqual(expectedDate)) {
                throw new IllegalArgumentException("Ngày lịch trình (" + itineraryDate + ") phải tương ứng với ngày thứ " + dayNumber + " của tour (" + expectedDate + ")");
            }
        }

        // Quy tắc 4: Giờ đến, rời cảng và all-aboard trong ngày cập cảng
        if (!isSeaDay && arrivalTime != null && departureTime != null) {
            if (!arrivalTime.isBefore(departureTime)) {
                throw new IllegalArgumentException("Thời gian cập cảng (arrival) phải trước thời gian rời cảng (departure)");
            }
            if (allAboardTime != null) {
                if (allAboardTime.isBefore(arrivalTime) || allAboardTime.isAfter(departureTime)) {
                    throw new IllegalArgumentException("Thời gian lên tàu (all-aboard) phải nằm giữa thời gian cập cảng và rời cảng");
                }
            }
        }

        // Quy tắc 5: Ngày trên biển (Sea Day) không được gắn cảng ghé
        if (isSeaDay && (request.getPortId() != null || (request.getPortName() != null && !request.getPortName().isBlank()))) {
            throw new IllegalArgumentException("Ngày lênh đênh trên biển (Sea Day) không thể chỉ định cảng dừng");
        }

        // Quy tắc 6: Tính tuần tự và không xung đột với các ngày khác trong tour
        List<DailyItinerary> otherItineraries = dailyItineraryRepository.findByTourIdAndActiveTrueOrderByDayNumberAsc(tour.getId());
        for (DailyItinerary other : otherItineraries) {
            if (currentItineraryId != null && other.getId().equals(currentItineraryId)) {
                continue;
            }

            if (other.getDayNumber().equals(dayNumber)) {
                throw new IllegalArgumentException("Lịch trình cho ngày thứ " + dayNumber + " đã tồn tại trong tour");
            }

            if (other.getItineraryDate().isEqual(itineraryDate)) {
                throw new IllegalArgumentException("Ngày lịch trình (" + itineraryDate + ") đã được sử dụng trong tour");
            }
        }
    }
}

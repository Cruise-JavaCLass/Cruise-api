package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.DailyItineraryRequest;
import com.cruise.itinerary.dto.DailyItineraryResponse;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.entity.DailyItinerary;
import com.cruise.itinerary.entity.Port;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.repository.CruiseTourRepository;
import com.cruise.itinerary.repository.DailyItineraryRepository;
import com.cruise.itinerary.repository.PortRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DailyItineraryService {

    private final DailyItineraryRepository dailyItineraryRepository;
    private final CruiseTourRepository tourRepository;
    private final PortRepository portRepository;

    public DailyItineraryService(
            DailyItineraryRepository dailyItineraryRepository,
            CruiseTourRepository tourRepository,
            PortRepository portRepository
    ) {
        this.dailyItineraryRepository = dailyItineraryRepository;
        this.tourRepository = tourRepository;
        this.portRepository = portRepository;
    }

    public DailyItineraryResponse createDailyItinerary(Long tourId, DailyItineraryRequest request) {
        CruiseTour tour = findTourById(tourId);

        if (dailyItineraryRepository.existsByTourIdAndDayNumberAndActiveTrue(tourId, request.getDayNumber())) {
            throw new DuplicateResourceException("Lịch trình cho ngày thứ " + request.getDayNumber() + " đã tồn tại trong tour");
        }

        validateItineraryDates(tour, request);

        Port port = null;
        String portName = request.getPortName();
        if (request.getPortId() != null) {
            port = portRepository.findById(request.getPortId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cảng với ID " + request.getPortId() + " không tồn tại"));
            if (portName == null || portName.isBlank()) {
                portName = port.getName();
            }
        }

        DailyItinerary itinerary = DailyItinerary.builder()
                .tour(tour)
                .dayNumber(request.getDayNumber())
                .itineraryDate(request.getItineraryDate())
                .title(request.getTitle())
                .description(request.getDescription())
                .port(port)
                .portName(portName)
                .arrivalTime(request.getArrivalTime())
                .departureTime(request.getDepartureTime())
                .allAboardTime(request.getAllAboardTime())
                .isSeaDay(Boolean.TRUE.equals(request.getIsSeaDay()))
                .weatherForecast(request.getWeatherForecast())
                .active(true)
                .build();

        return toResponse(dailyItineraryRepository.save(itinerary));
    }

    public DailyItineraryResponse updateDailyItinerary(Long tourId, Long itineraryId, DailyItineraryRequest request) {
        CruiseTour tour = findTourById(tourId);
        DailyItinerary itinerary = findItineraryByIdAndTourId(itineraryId, tourId);

        if (!itinerary.getDayNumber().equals(request.getDayNumber())
                && dailyItineraryRepository.existsByTourIdAndDayNumberAndActiveTrue(tourId, request.getDayNumber())) {
            throw new DuplicateResourceException("Lịch trình cho ngày thứ " + request.getDayNumber() + " đã tồn tại trong tour");
        }

        validateItineraryDates(tour, request);

        Port port = null;
        String portName = request.getPortName();
        if (request.getPortId() != null) {
            port = portRepository.findById(request.getPortId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cảng với ID " + request.getPortId() + " không tồn tại"));
            if (portName == null || portName.isBlank()) {
                portName = port.getName();
            }
        }

        itinerary.setDayNumber(request.getDayNumber());
        itinerary.setItineraryDate(request.getItineraryDate());
        itinerary.setTitle(request.getTitle());
        itinerary.setDescription(request.getDescription());
        itinerary.setPort(port);
        itinerary.setPortName(portName);
        itinerary.setArrivalTime(request.getArrivalTime());
        itinerary.setDepartureTime(request.getDepartureTime());
        itinerary.setAllAboardTime(request.getAllAboardTime());
        itinerary.setIsSeaDay(Boolean.TRUE.equals(request.getIsSeaDay()));
        itinerary.setWeatherForecast(request.getWeatherForecast());

        return toResponse(dailyItineraryRepository.save(itinerary));
    }

    @Transactional(readOnly = true)
    public List<DailyItineraryResponse> getDailyItinerariesByTourId(Long tourId) {
        findTourById(tourId);
        return dailyItineraryRepository.findByTourIdAndActiveTrueOrderByDayNumberAsc(tourId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DailyItineraryResponse getDailyItineraryById(Long tourId, Long itineraryId) {
        return toResponse(findItineraryByIdAndTourId(itineraryId, tourId));
    }

    public void deleteDailyItinerary(Long tourId, Long itineraryId) {
        DailyItinerary itinerary = findItineraryByIdAndTourId(itineraryId, tourId);
        itinerary.setActive(false);
        dailyItineraryRepository.save(itinerary);
    }

    private CruiseTour findTourById(Long tourId) {
        return tourRepository.findById(tourId)
                .orElseThrow(() -> new ResourceNotFoundException("Tour du thuyền với ID " + tourId + " không tồn tại"));
    }

    private DailyItinerary findItineraryByIdAndTourId(Long itineraryId, Long tourId) {
        return dailyItineraryRepository.findByIdAndTourIdAndActiveTrue(itineraryId, tourId)
                .orElseThrow(() -> new ResourceNotFoundException("Lịch trình ngày với ID " + itineraryId + " không tồn tại trong tour"));
    }

    private void validateItineraryDates(CruiseTour tour, DailyItineraryRequest request) {
        if (request.getDayNumber() < 1) {
            throw new IllegalArgumentException("Số thứ tự ngày phải lớn hơn hoặc bằng 1");
        }
        if (tour.getDurationDays() != null && request.getDayNumber() > tour.getDurationDays()) {
            throw new IllegalArgumentException("Số thứ tự ngày không được vượt quá tổng thời lượng tour (" + tour.getDurationDays() + " ngày)");
        }
        if (tour.getStartDate() != null && request.getItineraryDate().isBefore(tour.getStartDate())) {
            throw new IllegalArgumentException("Ngày lịch trình không được trước ngày bắt đầu tour (" + tour.getStartDate() + ")");
        }
        if (tour.getEndDate() != null && request.getItineraryDate().isAfter(tour.getEndDate())) {
            throw new IllegalArgumentException("Ngày lịch trình không được sau ngày kết thúc tour (" + tour.getEndDate() + ")");
        }
    }

    private DailyItineraryResponse toResponse(DailyItinerary itinerary) {
        return DailyItineraryResponse.builder()
                .id(itinerary.getId())
                .tourId(itinerary.getTour().getId())
                .dayNumber(itinerary.getDayNumber())
                .itineraryDate(itinerary.getItineraryDate())
                .title(itinerary.getTitle())
                .description(itinerary.getDescription())
                .portId(itinerary.getPort() != null ? itinerary.getPort().getId() : null)
                .portName(itinerary.getPortName())
                .arrivalTime(itinerary.getArrivalTime())
                .departureTime(itinerary.getDepartureTime())
                .allAboardTime(itinerary.getAllAboardTime())
                .isSeaDay(itinerary.getIsSeaDay())
                .weatherForecast(itinerary.getWeatherForecast())
                .active(itinerary.getActive())
                .createdAt(itinerary.getCreatedAt())
                .updatedAt(itinerary.getUpdatedAt())
                .build();
    }
}

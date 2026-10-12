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
        checkDayNumberUniqueness(tourId, request.getDayNumber(), null);
        validateItineraryDates(tour, request);

        Port port = resolvePort(request.getPortId());
        String portName = resolvePortName(request.getPortName(), port);

        DailyItinerary itinerary = DailyItinerary.builder().tour(tour).build();
        applyRequestToEntity(itinerary, request, port, portName);
        itinerary.setActive(true);

        return toResponse(dailyItineraryRepository.save(itinerary));
    }

    public DailyItineraryResponse updateDailyItinerary(Long tourId, Long itineraryId, DailyItineraryRequest request) {
        CruiseTour tour = findTourById(tourId);
        DailyItinerary itinerary = findItineraryByIdAndTourId(itineraryId, tourId);

        checkDayNumberUniqueness(tourId, request.getDayNumber(), itinerary.getDayNumber());
        validateItineraryDates(tour, request);

        Port port = resolvePort(request.getPortId());
        String portName = resolvePortName(request.getPortName(), port);

        applyRequestToEntity(itinerary, request, port, portName);
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

    private void checkDayNumberUniqueness(Long tourId, Integer newDayNumber, Integer currentDayNumber) {
        if (!newDayNumber.equals(currentDayNumber)
                && dailyItineraryRepository.existsByTourIdAndDayNumberAndActiveTrue(tourId, newDayNumber)) {
            throw new DuplicateResourceException("Lịch trình cho ngày thứ " + newDayNumber + " đã tồn tại trong tour");
        }
    }

    private Port resolvePort(Long portId) {
        if (portId == null) {
            return null;
        }
        return portRepository.findById(portId)
                .orElseThrow(() -> new ResourceNotFoundException("Cảng với ID " + portId + " không tồn tại"));
    }

    private String resolvePortName(String requestedPortName, Port port) {
        if (requestedPortName != null && !requestedPortName.isBlank()) {
            return requestedPortName;
        }
        return port != null ? port.getName() : null;
    }

    private void applyRequestToEntity(DailyItinerary entity, DailyItineraryRequest req, Port port, String portName) {
        entity.setDayNumber(req.getDayNumber());
        entity.setItineraryDate(req.getItineraryDate());
        entity.setTitle(req.getTitle());
        entity.setDescription(req.getDescription());
        entity.setPort(port);
        entity.setPortName(portName);
        entity.setArrivalTime(req.getArrivalTime());
        entity.setDepartureTime(req.getDepartureTime());
        entity.setAllAboardTime(req.getAllAboardTime());
        entity.setIsSeaDay(Boolean.TRUE.equals(req.getIsSeaDay()));
        entity.setWeatherForecast(req.getWeatherForecast());
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

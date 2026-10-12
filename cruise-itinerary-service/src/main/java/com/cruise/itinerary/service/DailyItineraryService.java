package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.DailyItineraryRequest;
import com.cruise.itinerary.dto.DailyItineraryResponse;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.entity.DailyItinerary;
import com.cruise.itinerary.entity.Port;
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
    private final DailyItineraryValidator dailyItineraryValidator;
    private final ItineraryChangeHistoryService historyService;

    public DailyItineraryService(
            DailyItineraryRepository dailyItineraryRepository,
            CruiseTourRepository tourRepository,
            PortRepository portRepository,
            DailyItineraryValidator dailyItineraryValidator,
            ItineraryChangeHistoryService historyService
    ) {
        this.dailyItineraryRepository = dailyItineraryRepository;
        this.tourRepository = tourRepository;
        this.portRepository = portRepository;
        this.dailyItineraryValidator = dailyItineraryValidator;
        this.historyService = historyService;
    }

    public DailyItineraryResponse createDailyItinerary(Long tourId, DailyItineraryRequest request) {
        CruiseTour tour = findTourById(tourId);
        dailyItineraryValidator.validateDailyItinerary(tour, request, null);

        Port port = resolvePort(request.getPortId());
        String portName = resolvePortName(request.getPortName(), port);

        DailyItinerary itinerary = DailyItinerary.builder().tour(tour).build();
        applyRequestToEntity(itinerary, request, port, portName);
        itinerary.setActive(true);

        DailyItinerary saved = dailyItineraryRepository.save(itinerary);
        historyService.recordChange(tour, saved, com.cruise.itinerary.enums.ChangeType.CREATE,
                "Khởi tạo lịch trình ngày " + saved.getDayNumber(), null, null, saved.getTitle());

        return toResponse(saved);
    }

    public DailyItineraryResponse updateDailyItinerary(Long tourId, Long itineraryId, DailyItineraryRequest request) {
        CruiseTour tour = findTourById(tourId);
        DailyItinerary itinerary = findItineraryByIdAndTourId(itineraryId, tourId);
        String previousState = "Day " + itinerary.getDayNumber() + ": " + itinerary.getTitle();

        dailyItineraryValidator.validateDailyItinerary(tour, request, itineraryId);

        Port port = resolvePort(request.getPortId());
        String portName = resolvePortName(request.getPortName(), port);

        applyRequestToEntity(itinerary, request, port, portName);
        DailyItinerary updated = dailyItineraryRepository.save(itinerary);
        String newState = "Day " + updated.getDayNumber() + ": " + updated.getTitle();

        historyService.recordChange(tour, updated, com.cruise.itinerary.enums.ChangeType.UPDATE,
                "Cập nhật thông tin lịch trình", null, previousState, newState);

        return toResponse(updated);
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

    public List<DailyItineraryResponse> reorderDailyItineraries(Long tourId, com.cruise.itinerary.dto.ReorderItineraryRequest request) {
        CruiseTour tour = findTourById(tourId);
        List<DailyItinerary> currentItineraries = dailyItineraryRepository.findByTourIdAndActiveTrueOrderByDayNumberAsc(tourId);

        java.util.Map<Long, Integer> newOrderMap = new java.util.HashMap<>();
        java.util.Set<Integer> uniqueOrders = new java.util.HashSet<>();

        for (com.cruise.itinerary.dto.ReorderItemDto item : request.getItems()) {
            if (!uniqueOrders.add(item.getNewOrder())) {
                throw new IllegalArgumentException("Thứ tự ngày không được trùng lặp: " + item.getNewOrder());
            }
            if (tour.getDurationDays() != null && item.getNewOrder() > tour.getDurationDays()) {
                throw new IllegalArgumentException("Thứ tự ngày không được vượt quá thời lượng tour: " + item.getNewOrder());
            }
            newOrderMap.put(item.getId(), item.getNewOrder());
        }

        for (DailyItinerary itinerary : currentItineraries) {
            Integer newDayNumber = newOrderMap.get(itinerary.getId());
            if (newDayNumber != null) {
                itinerary.setDayNumber(newDayNumber);
                if (tour.getStartDate() != null) {
                    itinerary.setItineraryDate(tour.getStartDate().plusDays(newDayNumber - 1L));
                }
            }
        }

        dailyItineraryRepository.saveAll(currentItineraries);
        historyService.recordChange(tour, null, com.cruise.itinerary.enums.ChangeType.REORDER,
                "Sắp xếp lại thứ tự các ngày trong tour", null, null, null);

        return getDailyItinerariesByTourId(tourId);
    }

    public void deleteDailyItinerary(Long tourId, Long itineraryId) {
        DailyItinerary itinerary = findItineraryByIdAndTourId(itineraryId, tourId);
        itinerary.setActive(false);
        dailyItineraryRepository.save(itinerary);

        historyService.recordChange(itinerary.getTour(), itinerary, com.cruise.itinerary.enums.ChangeType.DELETE,
                "Xóa lịch trình ngày " + itinerary.getDayNumber(), null, itinerary.getTitle(), null);
    }

    private CruiseTour findTourById(Long tourId) {
        return tourRepository.findById(tourId)
                .orElseThrow(() -> new ResourceNotFoundException("Tour du thuyền với ID " + tourId + " không tồn tại"));
    }

    private DailyItinerary findItineraryByIdAndTourId(Long itineraryId, Long tourId) {
        return dailyItineraryRepository.findByIdAndTourIdAndActiveTrue(itineraryId, tourId)
                .orElseThrow(() -> new ResourceNotFoundException("Lịch trình ngày với ID " + itineraryId + " không tồn tại trong tour"));
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

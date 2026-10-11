package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.CruiseStopRequest;
import com.cruise.itinerary.dto.CruiseStopResponse;
import com.cruise.itinerary.entity.CruiseStop;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.entity.Port;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.repository.CruiseStopRepository;
import com.cruise.itinerary.repository.CruiseTourRepository;
import com.cruise.itinerary.repository.PortRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CruiseStopService {

    private final CruiseStopRepository stopRepository;
    private final CruiseTourRepository tourRepository;
    private final PortRepository portRepository;
    private final StopValidator stopValidator;

    public CruiseStopService(CruiseStopRepository stopRepository,
                             CruiseTourRepository tourRepository,
                             PortRepository portRepository,
                             StopValidator stopValidator) {
        this.stopRepository = stopRepository;
        this.tourRepository = tourRepository;
        this.portRepository = portRepository;
        this.stopValidator = stopValidator;
    }

    public CruiseStopResponse createStop(Long tourId, CruiseStopRequest request) {
        CruiseTour tour = tourRepository.findById(tourId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tour với ID: " + tourId));

        Port port = portRepository.findById(request.getPortId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cảng với ID: " + request.getPortId()));

        if (stopRepository.existsByTourIdAndStopOrderAndActiveTrue(tourId, request.getStopOrder())) {
            throw new DuplicateResourceException("Thứ tự điểm dừng '" + request.getStopOrder() + "' đã tồn tại trong tour này");
        }

        stopValidator.validateStopSchedule(tour, request, null);

        CruiseStop stop = CruiseStop.builder()
                .tour(tour)
                .port(port)
                .stopOrder(request.getStopOrder())
                .arrivalTime(request.getArrivalTime())
                .departureTime(request.getDepartureTime())
                .allAboardTime(request.getAllAboardTime())
                .description(request.getDescription())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        return CruiseStopResponse.fromEntity(stopRepository.save(stop));
    }

    public CruiseStopResponse updateStop(Long tourId, Long stopId, CruiseStopRequest request) {
        CruiseTour tour = tourRepository.findById(tourId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tour với ID: " + tourId));

        CruiseStop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm dừng với ID: " + stopId));

        if (!stop.getTour().getId().equals(tourId)) {
            throw new IllegalArgumentException("Điểm dừng ID: " + stopId + " không thuộc về tour ID: " + tourId);
        }

        Port port = portRepository.findById(request.getPortId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cảng với ID: " + request.getPortId()));

        if (stopRepository.existsByTourIdAndStopOrderAndIdNotAndActiveTrue(tourId, request.getStopOrder(), stopId)) {
            throw new DuplicateResourceException("Thứ tự điểm dừng '" + request.getStopOrder() + "' đã tồn tại trong tour này");
        }

        stopValidator.validateStopSchedule(tour, request, stopId);

        stop.setPort(port);
        stop.setStopOrder(request.getStopOrder());
        stop.setArrivalTime(request.getArrivalTime());
        stop.setDepartureTime(request.getDepartureTime());
        stop.setAllAboardTime(request.getAllAboardTime());
        stop.setDescription(request.getDescription());
        if (request.getActive() != null) stop.setActive(request.getActive());

        return CruiseStopResponse.fromEntity(stopRepository.save(stop));
    }

    @Transactional(readOnly = true)
    public List<CruiseStopResponse> getStopsByTourId(Long tourId) {
        if (!tourRepository.existsById(tourId)) {
            throw new ResourceNotFoundException("Không tìm thấy tour với ID: " + tourId);
        }
        return stopRepository.findByTourIdAndActiveTrueOrderByStopOrderAsc(tourId).stream()
                .map(CruiseStopResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CruiseStopResponse getStopById(Long tourId, Long stopId) {
        CruiseStop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm dừng với ID: " + stopId));

        if (!stop.getTour().getId().equals(tourId)) {
            throw new IllegalArgumentException("Điểm dừng ID: " + stopId + " không thuộc về tour ID: " + tourId);
        }
        return CruiseStopResponse.fromEntity(stop);
    }

    public void deleteStop(Long tourId, Long stopId) {
        CruiseStop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm dừng với ID: " + stopId));

        if (!stop.getTour().getId().equals(tourId)) {
            throw new IllegalArgumentException("Điểm dừng ID: " + stopId + " không thuộc về tour ID: " + tourId);
        }
        stop.setActive(false);
        stopRepository.save(stop);
    }
}

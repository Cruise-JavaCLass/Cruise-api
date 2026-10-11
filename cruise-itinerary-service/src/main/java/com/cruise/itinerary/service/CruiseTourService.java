package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.*;
import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.enums.TourStatus;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.repository.CruiseShipRepository;
import com.cruise.itinerary.repository.CruiseTourRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service xử lý logic nghiệp vụ quản lý Tour du thuyền (Cruise Tour Management)
 */
@Service
@Transactional
public class CruiseTourService {

    private final CruiseTourRepository tourRepository;
    private final CruiseShipRepository shipRepository;
    private final TourValidator tourValidator;

    public CruiseTourService(
            CruiseTourRepository tourRepository,
            CruiseShipRepository shipRepository,
            TourValidator tourValidator
    ) {
        this.tourRepository = tourRepository;
        this.shipRepository = shipRepository;
        this.tourValidator = tourValidator;
    }

    /**
     * Tạo mới tour du thuyền trong hệ thống
     */
    public CruiseTourResponse createTour(CreateCruiseTourRequest request) {
        String trimmedCode = request.getCode().trim().toUpperCase();
        String trimmedName = request.getName().trim();

        if (tourRepository.existsByCode(trimmedCode)) {
            throw new DuplicateResourceException("Mã tour '" + request.getCode() + "' đã tồn tại trong hệ thống");
        }
        if (tourRepository.existsByName(trimmedName)) {
            throw new DuplicateResourceException("Tên tour '" + request.getName() + "' đã tồn tại trong hệ thống");
        }

        CruiseShip ship = null;
        if (request.getShipId() != null) {
            ship = shipRepository.findById(request.getShipId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tàu du lịch với ID: " + request.getShipId()));
        }

        tourValidator.validate(request, ship);

        CruiseTour tour = CruiseTour.builder()
                .name(trimmedName)
                .code(trimmedCode)
                .description(request.getDescription())
                .ship(ship)
                .durationDays(request.getDurationDays())
                .durationNights(request.getDurationNights())
                .departurePort(request.getDeparturePort().trim())
                .destinationPort(request.getDestinationPort().trim())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .maxCapacity(request.getMaxCapacity())
                .basePrice(request.getBasePrice())
                .currency(request.getCurrency() != null ? request.getCurrency().trim().toUpperCase() : "VND")
                .itineraryOverview(request.getItineraryOverview())
                .imageUrl(request.getImageUrl())
                .status(request.getStatus() != null ? request.getStatus() : TourStatus.DRAFT)
                .active(true)
                .build();

        CruiseTour savedTour = tourRepository.save(tour);
        return CruiseTourResponse.fromEntity(savedTour);
    }

    /**
     * Cập nhật thông tin chi tiết tour du thuyền
     */
    public CruiseTourResponse updateTour(Long id, UpdateCruiseTourRequest request) {
        CruiseTour tour = tourRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tour du lịch với ID: " + id));

        String newCode = request.getCode().trim().toUpperCase();
        String newName = request.getName().trim();

        if (tourRepository.existsByCodeAndIdNot(newCode, id)) {
            throw new DuplicateResourceException("Mã tour '" + request.getCode() + "' đã được sử dụng bởi tour khác");
        }
        if (tourRepository.existsByNameAndIdNot(newName, id)) {
            throw new DuplicateResourceException("Tên tour '" + request.getName() + "' đã được sử dụng bởi tour khác");
        }

        CruiseShip ship = null;
        if (request.getShipId() != null) {
            ship = shipRepository.findById(request.getShipId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tàu du lịch với ID: " + request.getShipId()));
        }

        tourValidator.validate(request, ship);

        tour.setName(newName);
        tour.setCode(newCode);
        tour.setDescription(request.getDescription());
        tour.setShip(ship);
        tour.setDurationDays(request.getDurationDays());
        tour.setDurationNights(request.getDurationNights());
        tour.setDeparturePort(request.getDeparturePort().trim());
        tour.setDestinationPort(request.getDestinationPort().trim());
        tour.setStartDate(request.getStartDate());
        tour.setEndDate(request.getEndDate());
        tour.setMaxCapacity(request.getMaxCapacity());
        tour.setBasePrice(request.getBasePrice());

        if (request.getCurrency() != null) {
            tour.setCurrency(request.getCurrency().trim().toUpperCase());
        }
        tour.setItineraryOverview(request.getItineraryOverview());
        tour.setImageUrl(request.getImageUrl());

        if (request.getStatus() != null) {
            tour.setStatus(request.getStatus());
        }
        if (request.getActive() != null) {
            tour.setActive(request.getActive());
        }

        CruiseTour updatedTour = tourRepository.save(tour);
        return CruiseTourResponse.fromEntity(updatedTour);
    }

    /**
     * Cập nhật trạng thái hoạt động của tour
     */
    public CruiseTourResponse updateTourStatus(Long id, UpdateTourStatusRequest request) {
        CruiseTour tour = tourRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tour du lịch với ID: " + id));

        tour.setStatus(request.getStatus());
        CruiseTour updatedTour = tourRepository.save(tour);
        return CruiseTourResponse.fromEntity(updatedTour);
    }

    /**
     * Lấy thông tin chi tiết tour theo ID
     */
    @Transactional(readOnly = true)
    public CruiseTourResponse getTourById(Long id) {
        CruiseTour tour = tourRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tour du lịch với ID: " + id));
        return CruiseTourResponse.fromEntity(tour);
    }

    /**
     * Lấy thông tin chi tiết tour theo mã định danh (Code)
     */
    @Transactional(readOnly = true)
    public CruiseTourResponse getTourByCode(String code) {
        CruiseTour tour = tourRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tour du lịch với mã: " + code));
        return CruiseTourResponse.fromEntity(tour);
    }

    /**
     * Lấy danh sách tour phân trang, hỗ trợ tìm kiếm và lọc
     */
    @Transactional(readOnly = true)
    public Page<CruiseTourResponse> getAllTours(
            String keyword,
            TourStatus status,
            Long shipId,
            Boolean activeOnly,
            Pageable pageable
    ) {
        String trimmedKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        Page<CruiseTour> page = tourRepository.searchTours(trimmedKeyword, status, shipId, activeOnly, pageable);
        return page.map(CruiseTourResponse::fromEntity);
    }

    /**
     * Lấy danh sách toàn bộ các tour đang kích hoạt
     */
    @Transactional(readOnly = true)
    public List<CruiseTourResponse> getAllActiveTours() {
        return tourRepository.findByActiveTrue().stream()
                .map(CruiseTourResponse::fromEntity)
                .toList();
    }

    /**
     * Xóa tour du thuyền (hỗ trợ xóa mềm hoặc xóa cứng)
     */
    public void deleteTour(Long id, boolean hardDelete) {
        CruiseTour tour = tourRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tour du lịch với ID: " + id));

        if (hardDelete) {
            tourRepository.delete(tour);
        } else {
            tour.setActive(false);
            tour.setStatus(TourStatus.CANCELLED);
            tourRepository.save(tour);
        }
    }

    /**
     * Thống kê số lượng tour đang kích hoạt
     */
    @Transactional(readOnly = true)
    public long countActiveTours() {
        return tourRepository.countByActiveTrue();
    }
}

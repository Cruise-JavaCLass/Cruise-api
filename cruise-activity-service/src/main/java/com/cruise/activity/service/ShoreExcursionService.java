package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.ExcursionGuide;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ExcursionVehicle;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.*;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ExcursionGuideRepository;
import com.cruise.activity.repository.ExcursionPassengerRepository;
import com.cruise.activity.repository.ExcursionVehicleRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service quản lý Tour tham quan trên bờ (Shore Excursion)
 * Thực thi các quy tắc nghiệp vụ cấu hình Giá, Sức chứa, Lịch trình, Gán hành khách, Phương tiện và Hướng dẫn viên (KAN-168, KAN-176, KAN-177)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShoreExcursionService {

    private final ShoreExcursionRepository excursionRepository;
    private final ExcursionPassengerRepository passengerRepository;
    private final ExcursionVehicleRepository vehicleRepository;
    private final ExcursionGuideRepository guideRepository;

    @Transactional
    public ShoreExcursionResponse createExcursion(CreateShoreExcursionRequest request) {
        validateSchedule(
                request.getExcursionDate(),
                request.getGatheringTime(),
                request.getDepartureTime(),
                request.getReturnToShipTime()
        );
        validatePriceAndCapacity(
                request.getPrice(),
                request.getChildPrice(),
                request.getMaxCapacity()
        );
        checkDuplicate(
                request.getTourId(),
                request.getPortId(),
                request.getExcursionDate(),
                request.getTitle(),
                null
        );

        ShoreExcursion excursion = ShoreExcursion.builder()
                .tourId(request.getTourId())
                .portId(request.getPortId())
                .dayNumber(request.getDayNumber())
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .portName(request.getPortName().trim())
                .meetingPoint(request.getMeetingPoint().trim())
                .localProvider(request.getLocalProvider())
                .providerContact(request.getProviderContact())
                .excursionDate(request.getExcursionDate())
                .gatheringTime(request.getGatheringTime())
                .departureTime(request.getDepartureTime())
                .returnToShipTime(request.getReturnToShipTime())
                .maxCapacity(request.getMaxCapacity())
                .currentParticipants(0)
                .price(request.getPrice())
                .childPrice(request.getChildPrice() != null ? request.getChildPrice() : BigDecimal.ZERO)
                .currency(request.getCurrency() != null && !request.getCurrency().isBlank() ? request.getCurrency().trim().toUpperCase() : "USD")
                .isIncluded(request.getIsIncluded() != null ? request.getIsIncluded() : false)
                .status(request.getStatus() != null ? request.getStatus() : ExcursionStatus.SCHEDULED)
                .active(true)
                .build();

        ShoreExcursion saved = excursionRepository.save(excursion);
        log.info("Created shore excursion ID={}, Title='{}', Port='{}'", saved.getId(), saved.getTitle(), saved.getPortName());
        return ShoreExcursionResponse.fromEntity(saved);
    }

    @Transactional
    public ShoreExcursionResponse updateExcursion(Long id, UpdateShoreExcursionRequest request) {
        ShoreExcursion excursion = findActiveExcursion(id);

        validateSchedule(
                request.getExcursionDate(),
                request.getGatheringTime(),
                request.getDepartureTime(),
                request.getReturnToShipTime()
        );
        validatePriceAndCapacity(
                request.getPrice(),
                request.getChildPrice(),
                request.getMaxCapacity()
        );
        checkDuplicate(
                excursion.getTourId(),
                request.getPortId(),
                request.getExcursionDate(),
                request.getTitle(),
                id
        );

        if (request.getMaxCapacity() < excursion.getCurrentParticipants()) {
            throw new BusinessRuleException(String.format(
                    "Sức chứa tối đa (%d) không được nhỏ hơn số người đã đăng ký hiện tại (%d)",
                    request.getMaxCapacity(), excursion.getCurrentParticipants()
            ));
        }

        excursion.setPortId(request.getPortId());
        excursion.setDayNumber(request.getDayNumber());
        excursion.setTitle(request.getTitle().trim());
        excursion.setDescription(request.getDescription());
        excursion.setPortName(request.getPortName().trim());
        excursion.setMeetingPoint(request.getMeetingPoint().trim());
        excursion.setLocalProvider(request.getLocalProvider());
        excursion.setProviderContact(request.getProviderContact());
        excursion.setExcursionDate(request.getExcursionDate());
        excursion.setGatheringTime(request.getGatheringTime());
        excursion.setDepartureTime(request.getDepartureTime());
        excursion.setReturnToShipTime(request.getReturnToShipTime());
        excursion.setMaxCapacity(request.getMaxCapacity());
        excursion.setPrice(request.getPrice());
        if (request.getChildPrice() != null) {
            excursion.setChildPrice(request.getChildPrice());
        }
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            excursion.setCurrency(request.getCurrency().trim().toUpperCase());
        }
        if (request.getIsIncluded() != null) {
            excursion.setIsIncluded(request.getIsIncluded());
        }
        if (request.getStatus() != null) {
            excursion.setStatus(request.getStatus());
        }

        ShoreExcursion updated = excursionRepository.save(excursion);
        log.info("Updated shore excursion ID={}, Title='{}'", updated.getId(), updated.getTitle());
        return ShoreExcursionResponse.fromEntity(updated);
    }

    /**
     * Cấu hình chuyên biệt: Giá vé, Sức chứa và Lịch trình cho tour bờ (KAN-168)
     */
    @Transactional
    public ShoreExcursionResponse configurePriceCapacityAndSchedule(
            Long id,
            ConfigureExcursionPriceCapacityScheduleRequest request
    ) {
        ShoreExcursion excursion = findActiveExcursion(id);

        LocalDate targetDate = request.getExcursionDate() != null
                ? request.getExcursionDate()
                : excursion.getExcursionDate();

        validateSchedule(
                targetDate,
                request.getGatheringTime(),
                request.getDepartureTime(),
                request.getReturnToShipTime()
        );
        validatePriceAndCapacity(
                request.getPrice(),
                request.getChildPrice(),
                request.getMaxCapacity()
        );

        if (request.getMaxCapacity() < excursion.getCurrentParticipants()) {
            throw new BusinessRuleException(String.format(
                    "Sức chứa tối đa (%d) không được nhỏ hơn số người đã đăng ký hiện tại (%d)",
                    request.getMaxCapacity(), excursion.getCurrentParticipants()
            ));
        }

        excursion.setExcursionDate(targetDate);
        excursion.setGatheringTime(request.getGatheringTime());
        excursion.setDepartureTime(request.getDepartureTime());
        excursion.setReturnToShipTime(request.getReturnToShipTime());
        excursion.setMaxCapacity(request.getMaxCapacity());
        excursion.setPrice(request.getPrice());
        if (request.getChildPrice() != null) {
            excursion.setChildPrice(request.getChildPrice());
        }
        if (request.getIsIncluded() != null) {
            excursion.setIsIncluded(request.getIsIncluded());
        }

        ShoreExcursion updated = excursionRepository.save(excursion);
        log.info("Configured price, capacity and schedule for excursion ID={}: Price={}, Capacity={}, Return={}",
                updated.getId(), updated.getPrice(), updated.getMaxCapacity(), updated.getReturnToShipTime());
        return ShoreExcursionResponse.fromEntity(updated);
    }

    @Transactional
    public ShoreExcursionResponse updateStatus(Long id, UpdateExcursionStatusRequest request) {
        ShoreExcursion excursion = findActiveExcursion(id);

        ExcursionStatus current = excursion.getStatus();
        ExcursionStatus target = request.getStatus();

        if (!current.canTransitionTo(target)) {
            throw new BusinessRuleException(String.format(
                    "Không thể chuyển trạng thái tour bờ từ '%s' sang '%s'. Các trạng thái hợp lệ: %s",
                    current, target, current.getAllowedTransitions()
            ));
        }

        if (target == ExcursionStatus.CANCELLED) {
            excursion.setCancellationReason(request.getCancellationReason());
        }

        excursion.setStatus(target);
        ShoreExcursion updated = excursionRepository.save(excursion);
        log.info("Transitioned excursion ID={} status from {} to {}", id, current, target);
        return ShoreExcursionResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public Set<ExcursionStatus> getAllowedStatusTransitions(Long id) {
        ShoreExcursion excursion = findActiveExcursion(id);
        return excursion.getStatus().getAllowedTransitions();
    }

    @Transactional(readOnly = true)
    public ShoreExcursionResponse getExcursionById(Long id) {
        ShoreExcursion excursion = findActiveExcursion(id);
        return buildFullExcursionResponse(excursion);
    }

    @Transactional(readOnly = true)
    public List<ShoreExcursionResponse> getExcursionsByTour(Long tourId) {
        return toResponseList(excursionRepository.findByTourIdAndActiveTrueOrderByExcursionDateAscGatheringTimeAsc(tourId));
    }

    @Transactional(readOnly = true)
    public List<ShoreExcursionResponse> getExcursionsByTourAndPort(Long tourId, Long portId) {
        return toResponseList(excursionRepository.findByTourIdAndPortIdAndActiveTrueOrderByGatheringTimeAsc(tourId, portId));
    }

    @Transactional(readOnly = true)
    public List<ShoreExcursionResponse> getExcursionsByTourAndDay(Long tourId, Integer dayNumber) {
        return toResponseList(excursionRepository.findByTourIdAndDayNumberAndActiveTrueOrderByGatheringTimeAsc(tourId, dayNumber));
    }

    @Transactional(readOnly = true)
    public List<ShoreExcursionResponse> getExcursionsByTourAndStatus(Long tourId, ExcursionStatus status) {
        return toResponseList(excursionRepository.findByTourIdAndStatusAndActiveTrueOrderByGatheringTimeAsc(tourId, status));
    }

    @Transactional(readOnly = true)
    public Page<ShoreExcursionResponse> getExcursionsPaged(
            Long tourId,
            Long portId,
            Integer dayNumber,
            String keyword,
            Pageable pageable
    ) {
        return excursionRepository.findPagedExcursions(tourId, portId, dayNumber, keyword, pageable)
                .map(ShoreExcursionResponse::fromEntity);
    }

    private List<ShoreExcursionResponse> toResponseList(List<ShoreExcursion> excursions) {
        return excursions.stream().map(ShoreExcursionResponse::fromEntity).toList();
    }

    @Transactional
    public void deleteExcursion(Long id) {
        ShoreExcursion excursion = findActiveExcursion(id);
        excursion.setActive(false);
        excursion.setStatus(ExcursionStatus.CANCELLED);
        excursionRepository.save(excursion);
        log.info("Soft-deleted shore excursion ID={}", id);
    }

    // ==========================================
    // PASSENGER ASSIGNMENT TO EXCURSION GROUPS (KAN-176)
    // ==========================================

    @Transactional(readOnly = true)
    public List<ExcursionPassengerResponse> getPassengersByExcursion(Long excursionId) {
        findActiveExcursion(excursionId);
        return passengerRepository.findByExcursionIdAndActiveTrue(excursionId)
                .stream()
                .map(ExcursionPassengerResponse::fromEntity)
                .toList();
    }

    @Transactional
    public ShoreExcursionResponse addPassengerToExcursion(Long excursionId, AddExcursionPassengerRequest request) {
        ShoreExcursion excursion = findActiveExcursion(excursionId);

        if (excursion.getStatus() == ExcursionStatus.CANCELLED || excursion.getStatus() == ExcursionStatus.COMPLETED) {
            throw new BusinessRuleException("Không thể thêm hành khách vào tour bờ đã hủy hoặc đã hoàn thành");
        }

        if (excursion.getCurrentParticipants() >= excursion.getMaxCapacity()) {
            throw new BusinessRuleException(String.format("Tour đã đạt sức chứa tối đa (%d khách)", excursion.getMaxCapacity()));
        }

        String ticketCode = request.getTicketCode();
        if (ticketCode == null || ticketCode.isBlank()) {
            long count = passengerRepository.countByExcursionIdAndActiveTrue(excursionId) + 1;
            ticketCode = String.format("EXC-TKT-%d-%03d", excursionId, count);
        } else if (passengerRepository.existsByTicketCode(ticketCode.trim())) {
            throw new BusinessRuleException("Mã vé tour bờ '" + ticketCode.trim() + "' đã tồn tại trên hệ thống");
        }

        ExcursionPassenger passenger = ExcursionPassenger.builder()
                .excursionId(excursionId)
                .passengerName(request.getPassengerName().trim())
                .cabinNumber(request.getCabinNumber().trim())
                .ticketCode(ticketCode.trim())
                .busNumber(request.getBusNumber() != null ? request.getBusNumber().trim() : null)
                .groupName(request.getGroupName() != null ? request.getGroupName().trim() : null)
                .emergencyPhone(request.getEmergencyPhone() != null ? request.getEmergencyPhone().trim() : null)
                .status(request.getStatus() != null ? request.getStatus() : ExcursionPassengerStatus.REGISTERED)
                .specialRequirements(request.getSpecialRequirements())
                .active(true)
                .build();

        passengerRepository.save(passenger);

        excursion.setCurrentParticipants(excursion.getCurrentParticipants() + 1);
        ShoreExcursion updatedExcursion = excursionRepository.save(excursion);

        List<ExcursionPassenger> passengers = passengerRepository.findByExcursionIdAndActiveTrue(excursionId);
        log.info("Added passenger '{}' to excursion ID={}, CurrentParticipants={}/{}",
                passenger.getPassengerName(), excursionId, updatedExcursion.getCurrentParticipants(), updatedExcursion.getMaxCapacity());
        return ShoreExcursionResponse.fromEntity(updatedExcursion, passengers);
    }

    @Transactional
    public ShoreExcursionResponse assignPassengerToGroup(Long excursionId, Long passengerId, AssignPassengerGroupRequest request) {
        ShoreExcursion excursion = findActiveExcursion(excursionId);

        ExcursionPassenger passenger = passengerRepository.findByIdAndExcursionIdAndActiveTrue(passengerId, excursionId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Không tìm thấy hành khách với ID: %d trong tour bờ ID: %d", passengerId, excursionId)));

        passenger.setGroupName(request.getGroupName().trim());
        if (request.getBusNumber() != null) {
            passenger.setBusNumber(request.getBusNumber().trim());
        }
        passengerRepository.save(passenger);

        List<ExcursionPassenger> passengers = passengerRepository.findByExcursionIdAndActiveTrue(excursionId);
        log.info("Assigned passenger ID={} in excursion ID={} to group='{}', bus='{}'",
                passengerId, excursionId, request.getGroupName(), request.getBusNumber());
        return ShoreExcursionResponse.fromEntity(excursion, passengers);
    }

    @Transactional
    public ShoreExcursionResponse bulkAssignPassengersToGroup(Long excursionId, BulkAssignPassengerGroupRequest request) {
        ShoreExcursion excursion = findActiveExcursion(excursionId);

        List<ExcursionPassenger> targetPassengers = passengerRepository.findByExcursionIdAndIdInAndActiveTrue(excursionId, request.getPassengerIds());
        if (targetPassengers.isEmpty()) {
            throw new BusinessRuleException("Không tìm thấy hành khách hợp lệ nào trong tour bờ để gán nhóm");
        }

        for (ExcursionPassenger p : targetPassengers) {
            p.setGroupName(request.getGroupName().trim());
            if (request.getBusNumber() != null) {
                p.setBusNumber(request.getBusNumber().trim());
            }
        }
        passengerRepository.saveAll(targetPassengers);

        List<ExcursionPassenger> allPassengers = passengerRepository.findByExcursionIdAndActiveTrue(excursionId);
        log.info("Bulk-assigned {} passengers in excursion ID={} to group='{}', bus='{}'",
                targetPassengers.size(), excursionId, request.getGroupName(), request.getBusNumber());
        return ShoreExcursionResponse.fromEntity(excursion, allPassengers);
    }

    @Transactional
    public ShoreExcursionResponse removePassengerFromExcursion(Long excursionId, Long passengerId) {
        ShoreExcursion excursion = findActiveExcursion(excursionId);

        ExcursionPassenger passenger = passengerRepository.findByIdAndExcursionIdAndActiveTrue(passengerId, excursionId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Không tìm thấy hành khách với ID: %d trong tour bờ ID: %d", passengerId, excursionId)));

        passenger.setActive(false);
        passengerRepository.save(passenger);

        if (excursion.getCurrentParticipants() > 0) {
            excursion.setCurrentParticipants(excursion.getCurrentParticipants() - 1);
        }
        ShoreExcursion updatedExcursion = excursionRepository.save(excursion);

        List<ExcursionPassenger> remainingPassengers = passengerRepository.findByExcursionIdAndActiveTrue(excursionId);
        log.info("Removed passenger ID={} from excursion ID={}, RemainingParticipants={}",
                passengerId, excursionId, updatedExcursion.getCurrentParticipants());
        return buildFullExcursionResponse(updatedExcursion);
    }

    // ==========================================
    // VEHICLE & GUIDE MANAGEMENT (KAN-177)
    // ==========================================

    @Transactional(readOnly = true)
    public List<ExcursionVehicleResponse> getVehiclesByExcursion(Long excursionId) {
        findActiveExcursion(excursionId);
        return vehicleRepository.findByExcursionIdAndActiveTrue(excursionId)
                .stream()
                .map(ExcursionVehicleResponse::fromEntity)
                .toList();
    }

    @Transactional
    public ShoreExcursionResponse addVehicleToExcursion(Long excursionId, AddExcursionVehicleRequest request) {
        ShoreExcursion excursion = findActiveExcursion(excursionId);

        int capacity = request.getCapacity();
        int assigned = request.getAssignedPassengers() != null ? request.getAssignedPassengers() : 0;
        if (assigned < 0) {
            throw new BusinessRuleException("Số lượng hành khách đã gán không được nhỏ hơn 0");
        }
        if (assigned > capacity) {
            throw new BusinessRuleException(String.format("Số lượng hành khách đã gán (%d) không được vượt quá sức chứa phương tiện (%d)", assigned, capacity));
        }

        long count = vehicleRepository.countByExcursionId(excursionId) + 1;
        String vehicleCode = String.format("VEH-%02d", count);

        ExcursionVehicle vehicle = ExcursionVehicle.builder()
                .excursionId(excursionId)
                .vehicleCode(vehicleCode)
                .licensePlate(request.getLicensePlate().trim())
                .vehicleType(request.getVehicleType().trim())
                .driverName(request.getDriverName().trim())
                .driverPhone(request.getDriverPhone() != null ? request.getDriverPhone().trim() : null)
                .capacity(capacity)
                .assignedPassengers(assigned)
                .status(request.getStatus() != null ? request.getStatus() : VehicleStatus.READY)
                .operatorName(request.getOperatorName() != null ? request.getOperatorName().trim() : excursion.getLocalProvider())
                .notes(request.getNotes())
                .active(true)
                .build();

        vehicleRepository.save(vehicle);
        log.info("Added vehicle '{}' ({}) to excursion ID={}", vehicle.getVehicleCode(), vehicle.getLicensePlate(), excursionId);
        return buildFullExcursionResponse(excursion);
    }

    @Transactional
    public ShoreExcursionResponse updateVehicleInExcursion(Long excursionId, String vehicleIdOrCode, UpdateExcursionVehicleRequest request) {
        ShoreExcursion excursion = findActiveExcursion(excursionId);
        ExcursionVehicle vehicle = findVehicleInExcursion(excursionId, vehicleIdOrCode);

        if (request.getCapacity() != null && request.getCapacity() <= 0) {
            throw new BusinessRuleException("Sức chứa phương tiện phải lớn hơn 0");
        }
        int effectiveCapacity = request.getCapacity() != null ? request.getCapacity() : vehicle.getCapacity();
        int effectiveAssigned = request.getAssignedPassengers() != null ? request.getAssignedPassengers() : (vehicle.getAssignedPassengers() != null ? vehicle.getAssignedPassengers() : 0);

        if (effectiveAssigned < 0) {
            throw new BusinessRuleException("Số lượng hành khách đã gán không được nhỏ hơn 0");
        }
        if (effectiveAssigned > effectiveCapacity) {
            throw new BusinessRuleException(String.format("Số lượng hành khách đã gán (%d) không được vượt quá sức chứa phương tiện (%d)", effectiveAssigned, effectiveCapacity));
        }

        if (request.getLicensePlate() != null && !request.getLicensePlate().isBlank()) {
            vehicle.setLicensePlate(request.getLicensePlate().trim());
        }
        if (request.getVehicleType() != null && !request.getVehicleType().isBlank()) {
            vehicle.setVehicleType(request.getVehicleType().trim());
        }
        if (request.getDriverName() != null && !request.getDriverName().isBlank()) {
            vehicle.setDriverName(request.getDriverName().trim());
        }
        if (request.getDriverPhone() != null) {
            vehicle.setDriverPhone(request.getDriverPhone().trim());
        }
        if (request.getCapacity() != null) {
            vehicle.setCapacity(request.getCapacity());
        }
        if (request.getAssignedPassengers() != null) {
            vehicle.setAssignedPassengers(request.getAssignedPassengers());
        }
        if (request.getStatus() != null) {
            vehicle.setStatus(request.getStatus());
        }
        if (request.getOperatorName() != null) {
            vehicle.setOperatorName(request.getOperatorName().trim());
        }
        if (request.getNotes() != null) {
            vehicle.setNotes(request.getNotes());
        }

        vehicleRepository.save(vehicle);
        log.info("Updated vehicle '{}' in excursion ID={}", vehicle.getVehicleCode(), excursionId);
        return buildFullExcursionResponse(excursion);
    }

    @Transactional
    public ShoreExcursionResponse removeVehicleFromExcursion(Long excursionId, String vehicleIdOrCode) {
        ShoreExcursion excursion = findActiveExcursion(excursionId);
        ExcursionVehicle vehicle = findVehicleInExcursion(excursionId, vehicleIdOrCode);

        vehicle.setActive(false);
        vehicleRepository.save(vehicle);
        log.info("Removed vehicle '{}' from excursion ID={}", vehicle.getVehicleCode(), excursionId);
        return buildFullExcursionResponse(excursion);
    }

    @Transactional(readOnly = true)
    public List<ExcursionGuideResponse> getGuidesByExcursion(Long excursionId) {
        findActiveExcursion(excursionId);
        return guideRepository.findByExcursionIdAndActiveTrue(excursionId)
                .stream()
                .map(ExcursionGuideResponse::fromEntity)
                .toList();
    }

    @Transactional
    public ShoreExcursionResponse addGuideToExcursion(Long excursionId, AddExcursionGuideRequest request) {
        ShoreExcursion excursion = findActiveExcursion(excursionId);

        long count = guideRepository.countByExcursionId(excursionId) + 1;
        String guideCode = String.format("GUD-%02d", count);

        String languagesStr = request.getLanguages() != null ? String.join(", ", request.getLanguages()) : null;

        ExcursionGuide guide = ExcursionGuide.builder()
                .excursionId(excursionId)
                .guideCode(guideCode)
                .fullName(request.getFullName().trim())
                .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
                .role(request.getRole() != null ? request.getRole() : GuideRole.LEAD)
                .languages(languagesStr)
                .licenseNumber(request.getLicenseNumber() != null ? request.getLicenseNumber().trim() : null)
                .status(request.getStatus() != null ? request.getStatus() : GuideStatus.ACTIVE)
                .rating(request.getRating() != null ? request.getRating() : 5.0)
                .notes(request.getNotes())
                .active(true)
                .build();

        guideRepository.save(guide);
        log.info("Added guide '{}' ({}) to excursion ID={}", guide.getGuideCode(), guide.getFullName(), excursionId);
        return buildFullExcursionResponse(excursion);
    }

    @Transactional
    public ShoreExcursionResponse updateGuideInExcursion(Long excursionId, String guideIdOrCode, UpdateExcursionGuideRequest request) {
        ShoreExcursion excursion = findActiveExcursion(excursionId);
        ExcursionGuide guide = findGuideInExcursion(excursionId, guideIdOrCode);

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            guide.setFullName(request.getFullName().trim());
        }
        if (request.getPhoneNumber() != null) {
            guide.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getRole() != null) {
            guide.setRole(request.getRole());
        }
        if (request.getLanguages() != null) {
            guide.setLanguages(String.join(", ", request.getLanguages()));
        }
        if (request.getLicenseNumber() != null) {
            guide.setLicenseNumber(request.getLicenseNumber().trim());
        }
        if (request.getStatus() != null) {
            guide.setStatus(request.getStatus());
        }
        if (request.getRating() != null) {
            guide.setRating(request.getRating());
        }
        if (request.getNotes() != null) {
            guide.setNotes(request.getNotes());
        }

        guideRepository.save(guide);
        log.info("Updated guide '{}' in excursion ID={}", guide.getGuideCode(), excursionId);
        return buildFullExcursionResponse(excursion);
    }

    @Transactional
    public ShoreExcursionResponse removeGuideFromExcursion(Long excursionId, String guideIdOrCode) {
        ShoreExcursion excursion = findActiveExcursion(excursionId);
        ExcursionGuide guide = findGuideInExcursion(excursionId, guideIdOrCode);

        guide.setActive(false);
        guideRepository.save(guide);
        log.info("Removed guide '{}' from excursion ID={}", guide.getGuideCode(), excursionId);
        return buildFullExcursionResponse(excursion);
    }

    /**
     * Tra cứu danh sách hành khách gom nhóm theo từng đoàn/xe cho tour bờ (KAN-178)
     */
    @Transactional(readOnly = true)
    public ExcursionManifestSummaryResponse getPassengerListsByGroup(Long excursionId) {
        ShoreExcursion excursion = findActiveExcursion(excursionId);
        List<ExcursionPassenger> passengers = passengerRepository.findByExcursionIdAndActiveTrue(excursionId);
        List<ExcursionVehicle> vehicles = vehicleRepository.findByExcursionIdAndActiveTrue(excursionId);
        List<ExcursionGuide> guides = guideRepository.findByExcursionIdAndActiveTrue(excursionId);

        Map<String, List<ExcursionPassenger>> grouped = passengers.stream()
                .collect(Collectors.groupingBy(
                        p -> (p.getGroupName() != null && !p.getGroupName().isBlank()) ? p.getGroupName().trim() : "Chưa phân nhóm",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ExcursionGroupManifestResponse> groupResponses = new ArrayList<>();
        for (Map.Entry<String, List<ExcursionPassenger>> entry : grouped.entrySet()) {
            String groupName = entry.getKey();
            List<ExcursionPassenger> groupList = entry.getValue();

            String busNumber = groupList.stream()
                    .map(ExcursionPassenger::getBusNumber)
                    .filter(b -> b != null && !b.isBlank())
                    .findFirst()
                    .orElse(null);

            ExcursionVehicleResponse vehicleInfo = null;
            if (busNumber != null) {
                vehicleInfo = vehicles.stream()
                        .filter(v -> busNumber.equalsIgnoreCase(v.getVehicleCode()) || busNumber.equalsIgnoreCase(v.getLicensePlate()))
                        .findFirst()
                        .map(ExcursionVehicleResponse::fromEntity)
                        .orElse(null);
            }

            ExcursionGuideResponse guideInfo = guides.isEmpty() ? null : ExcursionGuideResponse.fromEntity(guides.get(0));

            int checkedIn = (int) groupList.stream().filter(p -> p.getStatus() == ExcursionPassengerStatus.CHECKED_IN).count();
            int onTour = (int) groupList.stream().filter(p -> p.getStatus() == ExcursionPassengerStatus.ON_TOUR).count();
            int returnedSafe = (int) groupList.stream().filter(p -> p.getStatus() == ExcursionPassengerStatus.RETURNED_SAFE).count();
            int pending = (int) groupList.stream().filter(p -> p.getStatus() == ExcursionPassengerStatus.REGISTERED).count();

            List<ExcursionPassengerResponse> passengerResponses = groupList.stream()
                    .map(ExcursionPassengerResponse::fromEntity)
                    .toList();

            groupResponses.add(ExcursionGroupManifestResponse.builder()
                    .groupName(groupName)
                    .busNumber(busNumber)
                    .vehicleInfo(vehicleInfo)
                    .guideInfo(guideInfo)
                    .totalPassengers(groupList.size())
                    .checkedInCount(checkedIn)
                    .onTourCount(onTour)
                    .returnedSafeCount(returnedSafe)
                    .pendingCount(pending)
                    .passengers(passengerResponses)
                    .build());
        }

        int groupCount = (int) groupResponses.stream()
                .filter(g -> !"Chưa phân nhóm".equals(g.getGroupName()))
                .count();

        return ExcursionManifestSummaryResponse.builder()
                .excursionId(excursion.getId())
                .excursionTitle(excursion.getTitle())
                .excursionDate(excursion.getExcursionDate())
                .gatheringTime(excursion.getGatheringTime())
                .departureTime(excursion.getDepartureTime())
                .returnToShipTime(excursion.getReturnToShipTime())
                .totalPassengers(passengers.size())
                .totalGroups(groupCount)
                .groups(groupResponses)
                .build();
    }

    /**
     * Tự động sinh và phân bổ nhóm / xe cho các hành khách chưa có nhóm (KAN-178)
     */
    @Transactional
    public ExcursionManifestSummaryResponse autoGeneratePassengerGroups(Long excursionId, AutoGenerateGroupsRequest request) {
        findActiveExcursion(excursionId);
        List<ExcursionPassenger> passengers = passengerRepository.findByExcursionIdAndActiveTrue(excursionId);
        List<ExcursionVehicle> vehicles = vehicleRepository.findByExcursionIdAndActiveTrue(excursionId);

        List<ExcursionPassenger> eligible = passengers.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .toList();

        if (eligible.isEmpty()) {
            return getPassengerListsByGroup(excursionId);
        }

        int targetGroups = 1;
        if (request != null && request.getTargetGroupCount() != null && request.getTargetGroupCount() > 0) {
            targetGroups = request.getTargetGroupCount();
        } else if (!vehicles.isEmpty()) {
            targetGroups = vehicles.size();
        } else if (request != null && request.getMaxPassengersPerGroup() != null && request.getMaxPassengersPerGroup() > 0) {
            targetGroups = (int) Math.ceil((double) eligible.size() / request.getMaxPassengersPerGroup());
        } else {
            targetGroups = Math.max(1, (int) Math.ceil((double) eligible.size() / 25.0));
        }

        if (request != null && request.getMaxPassengersPerGroup() != null && request.getMaxPassengersPerGroup() > 0) {
            int minimumGroups = (int) Math.ceil((double) eligible.size() / request.getMaxPassengersPerGroup());
            targetGroups = Math.max(targetGroups, minimumGroups);
        }
        targetGroups = Math.min(targetGroups, eligible.size());
        String rawPrefix = (request != null && request.getGroupPrefix() != null && !request.getGroupPrefix().isBlank())
                ? request.getGroupPrefix().trim()
                : "Đoàn";
        String prefix = rawPrefix + " ";
        boolean assignVehicles = request == null || Boolean.TRUE.equals(request.getAutoAssignVehicles());

        for (int i = 0; i < eligible.size(); i++) {
            ExcursionPassenger p = eligible.get(i);
            int groupIndex = (i % targetGroups) + 1;
            p.setGroupName(prefix + groupIndex);
            if (assignVehicles && !vehicles.isEmpty()) {
                p.setBusNumber(vehicles.get(i % vehicles.size()).getVehicleCode());
            }
        }

        passengerRepository.saveAll(eligible);
        log.info("Auto-generated {} groups for {} passengers in excursion ID={}", targetGroups, eligible.size(), excursionId);
        return getPassengerListsByGroup(excursionId);
    }

    /**
     * Xuất danh sách hành khách theo nhóm dạng CSV (KAN-178)
     */
    @Transactional(readOnly = true)
    public byte[] exportPassengerGroupsCsv(Long excursionId, String groupFilter) {
        ExcursionManifestSummaryResponse manifest = getPassengerListsByGroup(excursionId);

        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff');
        sb.append("STT,Đoàn / Nhóm,Xe vận chuyển,Mã vé,Họ và tên,Số cabin,Trạng thái,Số điện thoại khẩn cấp,Yêu cầu đặc biệt\n");

        int index = 1;
        for (ExcursionGroupManifestResponse group : manifest.getGroups()) {
            if (groupFilter != null && !groupFilter.isBlank() && !group.getGroupName().equalsIgnoreCase(groupFilter.trim())) {
                continue;
            }
            for (ExcursionPassengerResponse p : group.getPassengers()) {
                sb.append(index++).append(",");
                sb.append(formatCsvValue(group.getGroupName())).append(",");
                sb.append(formatCsvValue(p.getBusNumber())).append(",");
                sb.append(formatCsvValue(p.getTicketCode())).append(",");
                sb.append(formatCsvValue(p.getPassengerName())).append(",");
                sb.append(formatCsvValue(p.getCabinNumber())).append(",");
                sb.append(formatCsvValue(p.getStatus() != null ? p.getStatus().name() : "")).append(",");
                sb.append(formatCsvValue(p.getEmergencyPhone())).append(",");
                sb.append(formatCsvValue(p.getSpecialRequirements())).append("\n");
            }
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String formatCsvValue(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private ExcursionVehicle findVehicleInExcursion(Long excursionId, String vehicleIdOrCode) {
        if (vehicleIdOrCode == null || vehicleIdOrCode.isBlank()) {
            throw new ResourceNotFoundException("ID hoặc mã phương tiện không được để trống");
        }
        var byCode = vehicleRepository.findByVehicleCodeAndExcursionIdAndActiveTrue(vehicleIdOrCode.trim(), excursionId);
        if (byCode.isPresent()) {
            return byCode.get();
        }
        try {
            String trimmed = vehicleIdOrCode.trim();
            if (trimmed.matches("\\d+")) {
                long numId = Long.parseLong(trimmed);
                var byId = vehicleRepository.findByIdAndExcursionIdAndActiveTrue(numId, excursionId);
                if (byId.isPresent()) {
                    return byId.get();
                }
            }
        } catch (NumberFormatException ignored) {
            // ignore
        }
        throw new ResourceNotFoundException(String.format("Không tìm thấy phương tiện với ID/Mã '%s' trong tour bờ ID: %d", vehicleIdOrCode, excursionId));
    }

    private ExcursionGuide findGuideInExcursion(Long excursionId, String guideIdOrCode) {
        if (guideIdOrCode == null || guideIdOrCode.isBlank()) {
            throw new ResourceNotFoundException("ID hoặc mã hướng dẫn viên không được để trống");
        }
        var byCode = guideRepository.findByGuideCodeAndExcursionIdAndActiveTrue(guideIdOrCode.trim(), excursionId);
        if (byCode.isPresent()) {
            return byCode.get();
        }
        try {
            String trimmed = guideIdOrCode.trim();
            if (trimmed.matches("\\d+")) {
                long numId = Long.parseLong(trimmed);
                var byId = guideRepository.findByIdAndExcursionIdAndActiveTrue(numId, excursionId);
                if (byId.isPresent()) {
                    return byId.get();
                }
            }
        } catch (NumberFormatException ignored) {
            // ignore
        }
        throw new ResourceNotFoundException(String.format("Không tìm thấy hướng dẫn viên với ID/Mã '%s' trong tour bờ ID: %d", guideIdOrCode, excursionId));
    }

    private ShoreExcursionResponse buildFullExcursionResponse(ShoreExcursion excursion) {
        Long id = excursion.getId();
        List<ExcursionPassenger> passengers = passengerRepository != null ? passengerRepository.findByExcursionIdAndActiveTrue(id) : null;
        List<ExcursionVehicle> vehicles = vehicleRepository != null ? vehicleRepository.findByExcursionIdAndActiveTrue(id) : null;
        List<ExcursionGuide> guides = guideRepository != null ? guideRepository.findByExcursionIdAndActiveTrue(id) : null;
        return ShoreExcursionResponse.fromEntity(excursion, passengers, vehicles, guides);
    }

    public void validateSchedule(
            LocalDate date,
            LocalTime gatheringTime,
            LocalTime departureTime,
            LocalTime returnToShipTime
    ) {
        if (date == null) {
            throw new BusinessRuleException("Ngày tổ chức tour bờ không được để trống");
        }
        if (gatheringTime == null || departureTime == null || returnToShipTime == null) {
            throw new BusinessRuleException("Giờ tập trung, xuất phát và quay lại tàu không được để trống");
        }
        if (!departureTime.isAfter(gatheringTime)) {
            throw new BusinessRuleException("Giờ khởi hành phải sau giờ tập trung của hành khách");
        }
        if (!returnToShipTime.isAfter(departureTime)) {
            throw new BusinessRuleException("Giờ quay lại tàu phải sau giờ khởi hành tour bờ");
        }
    }

    public void validatePriceAndCapacity(
            BigDecimal price,
            BigDecimal childPrice,
            Integer maxCapacity
    ) {
        if (maxCapacity == null || maxCapacity <= 0) {
            throw new BusinessRuleException("Sức chứa tối đa của tour bờ phải lớn hơn 0");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Giá vé người lớn không được để trống hoặc âm");
        }
        if (childPrice != null && childPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Giá vé trẻ em không được âm");
        }
        if (childPrice != null && childPrice.compareTo(price) > 0) {
            throw new BusinessRuleException("Giá vé trẻ em không được cao hơn giá vé người lớn");
        }
    }

    private void checkDuplicate(
            Long tourId,
            Long portId,
            LocalDate date,
            String title,
            Long excludeId
    ) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessRuleException("Tên tour bờ không được để trống");
        }
        boolean exists = excursionRepository.existsDuplicateExcursion(
                tourId,
                portId,
                date,
                title.trim(),
                excludeId
        );
        if (exists) {
            throw new BusinessRuleException(String.format(
                    "Tour bờ '%s' đã tồn tại tại cảng này vào ngày %s",
                    title.trim(), date
            ));
        }
    }

    private ShoreExcursion findActiveExcursion(Long id) {
        return excursionRepository.findById(id)
                .filter(ShoreExcursion::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tour bờ với ID: " + id));
    }
}

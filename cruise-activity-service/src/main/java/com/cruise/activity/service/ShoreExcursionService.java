package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ExcursionPassengerRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Service quản lý Tour tham quan trên bờ (Shore Excursion)
 * Thực thi các quy tắc nghiệp vụ cấu hình Giá, Sức chứa, Lịch trình và Gán hành khách (KAN-168, KAN-176)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShoreExcursionService {

    private final ShoreExcursionRepository excursionRepository;
    private final ExcursionPassengerRepository passengerRepository;

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
        List<ExcursionPassenger> passengers = passengerRepository != null ? passengerRepository.findByExcursionIdAndActiveTrue(id) : null;
        return ShoreExcursionResponse.fromEntity(excursion, passengers);
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
        return ShoreExcursionResponse.fromEntity(updatedExcursion, remainingPassengers);
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

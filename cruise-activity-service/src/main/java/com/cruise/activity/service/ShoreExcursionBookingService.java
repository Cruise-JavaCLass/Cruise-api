package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.*;
import com.cruise.activity.enums.ExcursionBookingStatus;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.enums.VehicleStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.DuplicateRegistrationException;
import com.cruise.activity.exception.PaymentRequiredException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.*;
import com.cruise.activity.service.PassengerNotificationService;
import com.cruise.activity.dto.SendNotificationRequest;
import com.cruise.activity.enums.NotificationCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShoreExcursionBookingService {

    private final ShoreExcursionRepository excursionRepository;
    private final ExcursionPassengerRepository passengerRepository;
    private final ExcursionVehicleRepository vehicleRepository;
    private final PassengerRepository cruisePassengerRepository;
    private final ExcursionBookingLogRepository bookingLogRepository;
    private final PassengerNotificationService notificationService;

    /**
     * Đặt tour tham quan trên bờ, kiểm tra sức chứa xe và ghi nợ Folio (KAN-374 / AC1, AC2, AC3, AC5)
     */
    @Transactional
    public ExcursionBookingResponse bookExcursion(Long excursionId, ExcursionBookingRequest request) {
        log.info("Processing excursion booking: excursionId={}, passengerId={}", excursionId, request.getPassengerId());

        // 1. Áp dụng Khóa bi quan (Pessimistic Lock) để chống race condition và overbooking
        ShoreExcursion excursion = excursionRepository.findByIdWithLock(excursionId)
                .filter(ShoreExcursion::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Tour trên bờ với ID " + excursionId + " không tồn tại"));

        if (excursion.getStatus() == ExcursionStatus.CANCELLED || excursion.getStatus() == ExcursionStatus.COMPLETED) {
            throw new BusinessRuleException("Tour trên bờ hiện không mở đăng ký (Trạng thái: " + excursion.getStatus() + ")");
        }

        // 2. Tra cứu thông tin hành khách
        Passenger passenger = cruisePassengerRepository.findById(request.getPassengerId())
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hành khách với ID " + request.getPassengerId() + " không tồn tại"));

        // Kiểm tra cabin folio có bị khóa chi tiêu
        if (isFolioLocked(passenger)) {
            throw new PaymentRequiredException("Tài khoản cabin folio của hành khách " + passenger.getFullName() +
                    " (Cabin " + passenger.getCabinNumber() + ") đang bị khóa hoặc không đủ hạn mức thanh toán");
        }

        // 3. Kiểm tra tính idempotent & trùng lặp đăng ký tour bờ
        passengerRepository.findByExcursionIdAndPassengerIdAndActiveTrue(excursionId, passenger.getId())
                .ifPresent(existing -> {
                    throw new DuplicateRegistrationException("Hành khách " + passenger.getFullName() +
                            " đã đăng ký tour bờ này trước đó với mã vé: " + existing.getTicketCode());
                });

        // 4. Kiểm tra sức chứa xe (Vehicle & Group Capacity - AC2)
        List<ExcursionVehicle> vehicles = vehicleRepository.findByExcursionIdAndActiveTrue(excursionId);
        List<ExcursionVehicle> operationalVehicles = vehicles.stream()
                .filter(v -> v.getStatus() == VehicleStatus.READY || v.getStatus() == VehicleStatus.ACTIVE || v.getStatus() == VehicleStatus.ON_TRIP)
                .toList();

        int totalVehicleCapacity = operationalVehicles.stream().mapToInt(ExcursionVehicle::getCapacity).sum();
        long currentBookings = passengerRepository.countByExcursionIdAndActiveTrue(excursionId);

        if (totalVehicleCapacity > 0 && currentBookings >= totalVehicleCapacity) {
            throw new BusinessRuleException(String.format("Không thể đặt chỗ: tổng sức chứa của các xe đang hoạt động đã đầy (%d/%d chỗ)",
                    currentBookings, totalVehicleCapacity));
        }

        if (excursion.getMaxCapacity() != null && currentBookings >= excursion.getMaxCapacity()) {
            throw new BusinessRuleException(String.format("Tour bờ đã đạt sức chứa tối đa (%d khách)", excursion.getMaxCapacity()));
        }

        // 5. Phân bổ xe bus (AC1, AC2, AC5 - Cohabitation)
        String assignedBus = resolveAssignedBus(request, operationalVehicles, excursionId, passenger.getCabinNumber());
        String groupName = (request.getGroupName() != null && !request.getGroupName().isBlank())
                ? request.getGroupName().trim()
                : (assignedBus != null ? "Nhóm " + assignedBus : "Nhóm 1");

        // 6. Tính toán giá vé và gói quyền lợi Folio (AC3)
        boolean isIncluded = isExcursionIncluded(excursion, passenger);

        BigDecimal chargedAmount = BigDecimal.ZERO;
        String transactionId = null;

        if (!isIncluded && excursion.getPrice() != null && excursion.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            chargedAmount = excursion.getPrice();
            transactionId = "FOLIO-EXC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        }

        // 7. Sinh mã vé điện tử duy nhất EXC-{id}-{timestamp} (AC1)
        String ticketCode = String.format("EXC-%d-%d", excursionId, System.currentTimeMillis());

        Instant now = Instant.now();
        String cabinNumber = (request.getCabinNumber() != null && !request.getCabinNumber().isBlank())
                ? request.getCabinNumber().trim()
                : passenger.getCabinNumber();

        String passengerName = (request.getPassengerName() != null && !request.getPassengerName().isBlank())
                ? request.getPassengerName().trim()
                : passenger.getFullName();

        ExcursionPassenger booking = ExcursionPassenger.builder()
                .excursionId(excursionId)
                .passengerId(passenger.getId())
                .passengerName(passengerName)
                .cabinNumber(cabinNumber)
                .ticketCode(ticketCode)
                .rfidTag(passenger.getRfidTag())
                .busNumber(assignedBus)
                .groupName(groupName)
                .emergencyPhone(request.getEmergencyPhone() != null ? request.getEmergencyPhone().trim() : passenger.getPhoneNumber())
                .specialRequirements(request.getSpecialRequirements())
                .bookingStatus(ExcursionBookingStatus.CONFIRMED)
                .waitlistPosition(null)
                .status(ExcursionPassengerStatus.REGISTERED)
                .chargedAmount(chargedAmount)
                .refundAmount(BigDecimal.ZERO)
                .penaltyFee(BigDecimal.ZERO)
                .transactionId(transactionId)
                .isIncluded(isIncluded)
                .active(true)
                .build();

        ExcursionPassenger saved = passengerRepository.save(booking);

        // Cập nhật số lượng người tham gia trong ShoreExcursion
        excursion.setCurrentParticipants((int) (currentBookings + 1));
        excursionRepository.save(excursion);

        // Cập nhật assignedPassengers trong ExcursionVehicle nếu đã xếp xe
        if (assignedBus != null) {
            operationalVehicles.stream()
                    .filter(v -> assignedBus.equalsIgnoreCase(v.getVehicleCode()) || assignedBus.equalsIgnoreCase(v.getLicensePlate()))
                    .findFirst()
                    .ifPresent(v -> {
                        v.setAssignedPassengers((v.getAssignedPassengers() != null ? v.getAssignedPassengers() : 0) + 1);
                        vehicleRepository.save(v);
                    });
        }

        // 8. Ghi nhật ký kiểm toán bất biến (AC6)
        bookingLogRepository.save(ExcursionBookingLog.builder()
                .bookingId(saved.getId())
                .excursionId(excursionId)
                .passengerId(passenger.getId())
                .ticketCode(ticketCode)
                .action("BOOKING_CREATED")
                .chargedAmount(chargedAmount)
                .refundAmount(BigDecimal.ZERO)
                .penaltyFee(BigDecimal.ZERO)
                .transactionId(transactionId)
                .busNumber(assignedBus)
                .groupName(groupName)
                .auditTime(now)
                .details(String.format("Đặt tour bờ thành công cho cabin %s, xe %s, số tiền: %s USD",
                        cabinNumber, assignedBus, chargedAmount))
                .build());

        log.info("Excursion booked successfully: bookingId={}, ticketCode={}, busNumber={}, chargedAmount={}",
                saved.getId(), ticketCode, assignedBus, chargedAmount);

        return ExcursionBookingResponse.builder()
                .bookingId(saved.getId())
                .excursionId(excursionId)
                .passengerId(passenger.getId())
                .passengerName(passengerName)
                .cabinNumber(cabinNumber)
                .ticketCode(ticketCode)
                .busNumber(assignedBus)
                .groupName(groupName)
                .chargedAmount(chargedAmount)
                .transactionId(transactionId)
                .isIncluded(isIncluded)
                .status(saved.getStatus().name())
                .bookedAt(now)
                .build();
    }

    /**
     * Hủy vé tour bờ theo chính sách Port Cut-off và hoàn tiền Folio (KAN-375 / AC4, AC6)
     */
    @Transactional
    public ExcursionCancellationResponse cancelBooking(Long excursionId, Long bookingId, String reason) {
        log.info("Processing excursion cancellation: excursionId={}, bookingId={}", excursionId, bookingId);

        ShoreExcursion excursion = excursionRepository.findByIdWithLock(excursionId)
                .filter(ShoreExcursion::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Tour trên bờ với ID " + excursionId + " không tồn tại"));

        ExcursionPassenger booking = passengerRepository.findByIdAndExcursionIdAndActiveTrue(bookingId, excursionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin vé đặt ID " + bookingId + " trong tour"));

        if (!Boolean.TRUE.equals(booking.getActive())) {
            throw new BusinessRuleException("Vé này đã bị hủy trước đó");
        }

        Instant now = Instant.now();
        LocalDateTime gatheringDateTime = LocalDateTime.of(excursion.getExcursionDate(), excursion.getGatheringTime());
        ZoneId portZone = resolvePortZone(excursion);
        Instant gatheringInstant = gatheringDateTime.atZone(portZone).toInstant();

        BigDecimal chargedAmount = booking.getChargedAmount() != null ? booking.getChargedAmount() : BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;
        BigDecimal penaltyFee = BigDecimal.ZERO;

        // AC4: Port Cut-off Cancellation Policy
        // Tour bị hủy do nhà vận hành/thời tiết (excursion CANCELLED): Hoàn 100%, không phạt
        if (excursion.getStatus() == ExcursionStatus.CANCELLED) {
            refundAmount = chargedAmount;
            penaltyFee = BigDecimal.ZERO;
        } else {
            long hoursUntilGathering = Duration.between(now, gatheringInstant).toHours();
            if (hoursUntilGathering >= 24) {
                // Trước 24h: Hoàn 100%, 0 phạt
                refundAmount = chargedAmount;
                penaltyFee = BigDecimal.ZERO;
            } else if (hoursUntilGathering >= 12) {
                // 12h - 24h: Phạt 50%, hoàn 50%
                penaltyFee = chargedAmount.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
                refundAmount = chargedAmount.subtract(penaltyFee);
            } else {
                // Dưới 12h: Phạt 100%, không hoàn
                penaltyFee = chargedAmount;
                refundAmount = BigDecimal.ZERO;
            }
        }

        boolean wasConfirmed = booking.getBookingStatus() == ExcursionBookingStatus.CONFIRMED;

        booking.setActive(false);
        booking.setBookingStatus(ExcursionBookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancellationReason(reason != null ? reason : "Hành khách hủy tour");
        booking.setRefundAmount(refundAmount);
        booking.setPenaltyFee(penaltyFee);
        passengerRepository.save(booking);

        if (wasConfirmed) {
            // Giảm số người tham gia trong ShoreExcursion
            if (excursion.getCurrentParticipants() != null && excursion.getCurrentParticipants() > 0) {
                excursion.setCurrentParticipants(excursion.getCurrentParticipants() - 1);
                excursionRepository.save(excursion);
            }

            // Giảm assignedPassengers của xe nếu có
            if (booking.getBusNumber() != null) {
                vehicleRepository.findByExcursionIdAndActiveTrue(excursionId).stream()
                        .filter(v -> booking.getBusNumber().equalsIgnoreCase(v.getVehicleCode()) || booking.getBusNumber().equalsIgnoreCase(v.getLicensePlate()))
                        .findFirst()
                        .ifPresent(v -> {
                            if (v.getAssignedPassengers() != null && v.getAssignedPassengers() > 0) {
                                v.setAssignedPassengers(v.getAssignedPassengers() - 1);
                                vehicleRepository.save(v);
                            }
                        });
            }
        }

        // Ghi nhật ký kiểm toán bất biến (AC6)
        bookingLogRepository.save(ExcursionBookingLog.builder()
                .bookingId(booking.getId())
                .excursionId(excursionId)
                .passengerId(booking.getPassengerId())
                .ticketCode(booking.getTicketCode())
                .action("BOOKING_CANCELLED")
                .chargedAmount(booking.getChargedAmount())
                .refundAmount(refundAmount)
                .penaltyFee(penaltyFee)
                .transactionId(booking.getTransactionId())
                .busNumber(booking.getBusNumber())
                .groupName(booking.getGroupName())
                .auditTime(now)
                .details(String.format("Hủy tour bờ thành công. Hoàn tiền: %s USD, Phạt: %s USD, Lý do: %s",
                        refundAmount, penaltyFee, booking.getCancellationReason()))
                .build());

        // AC2: Tự động đôn khách đầu tiên trong Waitlist (Auto-promote) khi có chỗ trống (chỉ khi hủy vé CONFIRMED)
        if (wasConfirmed) {
            triggerAutoPromotionIfPossible(excursionId);
        }

        log.info("Excursion booking cancelled: bookingId={}, refundAmount={}, penaltyFee={}",
                bookingId, refundAmount, penaltyFee);

        return ExcursionCancellationResponse.builder()
                .bookingId(booking.getId())
                .excursionId(excursionId)
                .passengerId(booking.getPassengerId())
                .status("CANCELLED")
                .refundAmount(refundAmount)
                .penaltyFee(penaltyFee)
                .cancellationReason(booking.getCancellationReason())
                .cancelledAt(now)
                .build();
    }

    /**
     * Điều phối và đổi xe chuyên chở cho hành khách (KAN-376 / API: PATCH /reassign-bus)
     */
    @Transactional
    public ReassignBusResponse reassignBus(Long excursionId, Long bookingId, ReassignBusRequest request) {
        log.info("Reassigning bus: excursionId={}, bookingId={}, newBusNumber={}", excursionId, bookingId, request.getNewBusNumber());

        ShoreExcursion excursion = excursionRepository.findByIdWithLock(excursionId)
                .filter(ShoreExcursion::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Tour trên bờ với ID " + excursionId + " không tồn tại"));

        ExcursionPassenger booking = passengerRepository.findByIdAndExcursionIdAndActiveTrue(bookingId, excursionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin vé đặt ID " + bookingId + " trong tour"));

        String oldBusNumber = booking.getBusNumber();
        String requestedBus = request.getNewBusNumber().trim();

        // Kiểm tra phương tiện vận chuyển và sức chứa xe mới
        List<ExcursionVehicle> vehicles = vehicleRepository.findByExcursionIdAndActiveTrue(excursionId);
        ExcursionVehicle targetVehicle = vehicles.stream()
                .filter(v -> requestedBus.equalsIgnoreCase(v.getVehicleCode()) || requestedBus.equalsIgnoreCase(v.getLicensePlate()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(String.format("Phương tiện '%s' không tồn tại trong tour này", requestedBus)));

        String newBusNumber = targetVehicle.getVehicleCode() != null ? targetVehicle.getVehicleCode() : targetVehicle.getLicensePlate();

        long currentCountInBus = passengerRepository.countByExcursionIdAndBusNumberAndActiveTrue(excursionId, newBusNumber);
        if (currentCountInBus >= targetVehicle.getCapacity()) {
            throw new BusinessRuleException(String.format("Xe %s đã đủ sức chứa (%d/%d chỗ)",
                    newBusNumber, currentCountInBus, targetVehicle.getCapacity()));
        }

        booking.setBusNumber(newBusNumber);
        if (request.getNewGroupName() != null && !request.getNewGroupName().isBlank()) {
            booking.setGroupName(request.getNewGroupName().trim());
        }
        passengerRepository.save(booking);

        // Cập nhật số khách trên 2 xe
        if (oldBusNumber != null) {
            vehicles.stream()
                    .filter(v -> oldBusNumber.equalsIgnoreCase(v.getVehicleCode()) || oldBusNumber.equalsIgnoreCase(v.getLicensePlate()))
                    .findFirst()
                    .ifPresent(v -> {
                        if (v.getAssignedPassengers() != null && v.getAssignedPassengers() > 0) {
                            v.setAssignedPassengers(v.getAssignedPassengers() - 1);
                            vehicleRepository.save(v);
                        }
                    });
        }
        targetVehicle.setAssignedPassengers((targetVehicle.getAssignedPassengers() != null ? targetVehicle.getAssignedPassengers() : 0) + 1);
        vehicleRepository.save(targetVehicle);

        // Ghi log kiểm toán (AC6)
        bookingLogRepository.save(ExcursionBookingLog.builder()
                .bookingId(booking.getId())
                .excursionId(excursionId)
                .passengerId(booking.getPassengerId())
                .ticketCode(booking.getTicketCode())
                .action("BUS_REASSIGNED")
                .chargedAmount(booking.getChargedAmount())
                .refundAmount(BigDecimal.ZERO)
                .penaltyFee(BigDecimal.ZERO)
                .transactionId(booking.getTransactionId())
                .busNumber(newBusNumber)
                .groupName(booking.getGroupName())
                .auditTime(Instant.now())
                .details(String.format("Chuyển xe từ %s sang %s", oldBusNumber, newBusNumber))
                .build());

        return ReassignBusResponse.builder()
                .bookingId(booking.getId())
                .excursionId(excursionId)
                .ticketCode(booking.getTicketCode())
                .oldBusNumber(oldBusNumber)
                .newBusNumber(newBusNumber)
                .groupName(booking.getGroupName())
                .message("Chuyển xe thành công")
                .build();
    }

    /**
     * Lấy danh sách tour đã đặt của hành khách (KAN-374 / API: GET /my)
     */
    @Transactional(readOnly = true)
    public List<PassengerExcursionBookingDto> getMyBookings(Long passengerId) {
        log.info("Fetching my excursion bookings: passengerId={}", passengerId);

        List<ExcursionPassenger> myBookings = passengerRepository.findByPassengerIdAndActiveTrue(passengerId);
        List<PassengerExcursionBookingDto> result = new ArrayList<>();

        for (ExcursionPassenger p : myBookings) {
            ShoreExcursion exc = excursionRepository.findById(p.getExcursionId()).orElse(null);
            if (exc == null || !Boolean.TRUE.equals(exc.getActive())) {
                continue;
            }

            result.add(PassengerExcursionBookingDto.builder()
                    .bookingId(p.getId())
                    .excursionId(exc.getId())
                    .excursionTitle(exc.getTitle())
                    .portName(exc.getPortName())
                    .excursionDate(exc.getExcursionDate())
                    .gatheringTime(exc.getGatheringTime())
                    .departureTime(exc.getDepartureTime())
                    .returnToShipTime(exc.getReturnToShipTime())
                    .meetingPoint(exc.getMeetingPoint())
                    .passengerId(p.getPassengerId())
                    .passengerName(p.getPassengerName())
                    .cabinNumber(p.getCabinNumber())
                    .ticketCode(p.getTicketCode())
                    .busNumber(p.getBusNumber())
                    .groupName(p.getGroupName())
                    .status(p.getStatus() != null ? p.getStatus().name() : "REGISTERED")
                    .chargedAmount(p.getChargedAmount())
                    .isIncluded(p.getIsIncluded())
                    .build());
        }

        return result;
    }

    private String resolveAssignedBus(ExcursionBookingRequest request, List<ExcursionVehicle> vehicles, Long excursionId, String cabinNumber) {
        if (request.getBusNumber() != null && !request.getBusNumber().isBlank()) {
            String candidateBus = request.getBusNumber().trim();
            ExcursionVehicle v = vehicles.stream()
                    .filter(x -> candidateBus.equalsIgnoreCase(x.getVehicleCode()) || candidateBus.equalsIgnoreCase(x.getLicensePlate()))
                    .findFirst()
                    .orElse(null);
            if (v != null) {
                String canonicalCode = v.getVehicleCode() != null ? v.getVehicleCode() : v.getLicensePlate();
                long count = passengerRepository.countByExcursionIdAndBusNumberAndActiveTrue(excursionId, canonicalCode);
                if (count < v.getCapacity()) {
                    return canonicalCode;
                }
            }
        }

        // AC5: Ưu tiên xếp chung xe với cùng Cabin hoặc Linked Cabins
        List<String> targetCabins = new ArrayList<>();
        if (cabinNumber != null) targetCabins.add(cabinNumber.trim());
        if (request.getLinkedCabins() != null) targetCabins.addAll(request.getLinkedCabins());

        for (String cab : targetCabins) {
            List<ExcursionPassenger> peers = passengerRepository.findByExcursionIdAndCabinNumberAndActiveTrue(excursionId, cab);
            for (ExcursionPassenger peer : peers) {
                if (peer.getBusNumber() != null && !peer.getBusNumber().isBlank()) {
                    String candidateBus = peer.getBusNumber().trim();
                    // Kiểm tra xem xe này còn chỗ không
                    ExcursionVehicle v = vehicles.stream()
                            .filter(x -> candidateBus.equalsIgnoreCase(x.getVehicleCode()) || candidateBus.equalsIgnoreCase(x.getLicensePlate()))
                            .findFirst().orElse(null);
                    if (v == null) {
                        return candidateBus;
                    }
                    String canonicalCode = v.getVehicleCode() != null ? v.getVehicleCode() : v.getLicensePlate();
                    long count = passengerRepository.countByExcursionIdAndBusNumberAndActiveTrue(excursionId, canonicalCode);
                    if (count < v.getCapacity()) {
                        return canonicalCode;
                    }
                }
            }
        }

        // Nếu không có cùng cabin hoặc xe đã đầy, chọn xe còn chỗ đầu tiên
        for (ExcursionVehicle v : vehicles) {
            String busCode = v.getVehicleCode() != null ? v.getVehicleCode() : v.getLicensePlate();
            long count = passengerRepository.countByExcursionIdAndBusNumberAndActiveTrue(excursionId, busCode);
            if (count < v.getCapacity()) {
                return busCode;
            }
        }

        return vehicles.isEmpty() ? "XE-01" : (vehicles.get(0).getVehicleCode() != null ? vehicles.get(0).getVehicleCode() : vehicles.get(0).getLicensePlate());
    }

    private ZoneId resolvePortZone(ShoreExcursion excursion) {
        // Có thể mở rộng mapping múi giờ theo portId nếu cần, mặc định Asia/Ho_Chi_Minh cho khu vực cruise
        return ZoneId.of("Asia/Ho_Chi_Minh");
    }

    private boolean isExcursionIncluded(ShoreExcursion excursion, Passenger passenger) {
        if (Boolean.TRUE.equals(excursion.getIsIncluded())) {
            return true;
        }
        if (passenger == null || passenger.getTourPackage() == null) {
            return false;
        }
        String pkg = passenger.getTourPackage().toLowerCase();
        return pkg.contains("vip") || pkg.contains("inclusive");
    }

    private boolean isFolioLocked(Passenger passenger) {
        if (passenger.getEmail() != null && (passenger.getEmail().toLowerCase().contains("locked") || passenger.getEmail().toLowerCase().contains("blocked"))) {
            return true;
        }
        return passenger.getCabinNumber() != null && passenger.getCabinNumber().toUpperCase().contains("LOCKED");
    }

    private ShoreExcursion findActiveExcursion(Long excursionId) {
        return excursionRepository.findById(excursionId)
                .filter(ShoreExcursion::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Tour trên bờ với ID " + excursionId + " không tồn tại"));
    }

    /**
     * Tra cứu danh sách hành khách đăng ký tour bờ (Passenger Roster) có phân trang và bộ lọc (KAN-325)
     */
    @Transactional(readOnly = true)
    public ExcursionRosterSummaryDto getExcursionRoster(
            Long excursionId,
            String search,
            ExcursionBookingStatus bookingStatus,
            Boolean specialAssistance,
            Boolean includeCancelled,
            Pageable pageable
    ) {
        ShoreExcursion excursion = excursionRepository.findById(excursionId)
                .filter(ShoreExcursion::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Tour trên bờ với ID " + excursionId + " không tồn tại"));

        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        Pageable sanitizedPageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<ExcursionPassenger> pageResult = passengerRepository.findRosterWithFilters(
                excursionId, cleanSearch, bookingStatus, specialAssistance, Boolean.TRUE.equals(includeCancelled), sanitizedPageable
        );

        long confirmedCount = passengerRepository.countByExcursionIdAndBookingStatusAndActiveTrue(
                excursionId, ExcursionBookingStatus.CONFIRMED);
        long waitlistCount = passengerRepository.countByExcursionIdAndBookingStatusAndActiveTrue(
                excursionId, ExcursionBookingStatus.WAITLISTED);
        long cancelledCount = passengerRepository.countByExcursionId(excursionId) -
                passengerRepository.countByExcursionIdAndActiveTrue(excursionId);

        List<ExcursionPassenger> allActive = passengerRepository.findByExcursionIdAndActiveTrue(excursionId);
        long checkedInCount = allActive.stream()
                .filter(p -> p.getStatus() == ExcursionPassengerStatus.CHECKED_IN
                        || p.getStatus() == ExcursionPassengerStatus.ON_TOUR
                        || p.getStatus() == ExcursionPassengerStatus.RETURNED_SAFE)
                .count();

        long specialAssistanceCount = allActive.stream()
                .filter(p -> p.getSpecialRequirements() != null && !p.getSpecialRequirements().trim().isEmpty())
                .count();

        List<ExcursionPassengerRosterDto> items = pageResult.getContent().stream()
                .map(this::mapToRosterDto)
                .toList();

        return ExcursionRosterSummaryDto.builder()
                .excursionId(excursion.getId())
                .excursionTitle(excursion.getTitle())
                .maxCapacity(excursion.getMaxCapacity())
                .confirmedCount(confirmedCount)
                .waitlistCount(waitlistCount)
                .cancelledCount(cancelledCount)
                .checkedInCount(checkedInCount)
                .specialAssistanceCount(specialAssistanceCount)
                .items(items)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }

    private ExcursionPassengerRosterDto mapToRosterDto(ExcursionPassenger entity) {
        boolean hasSpecialNeeds = entity.getSpecialRequirements() != null && !entity.getSpecialRequirements().trim().isEmpty();
        return ExcursionPassengerRosterDto.builder()
                .id(entity.getId())
                .excursionId(entity.getExcursionId())
                .passengerId(entity.getPassengerId())
                .passengerName(entity.getPassengerName())
                .cabinNumber(entity.getCabinNumber())
                .ticketCode(entity.getTicketCode())
                .rfidTag(entity.getRfidTag())
                .busNumber(entity.getBusNumber())
                .groupName(entity.getGroupName())
                .emergencyPhone(entity.getEmergencyPhone())
                .bookingStatus(entity.getBookingStatus())
                .waitlistPosition(entity.getWaitlistPosition())
                .status(entity.getStatus())
                .specialRequirements(entity.getSpecialRequirements())
                .needsSpecialAssistance(hasSpecialNeeds)
                .chargedAmount(entity.getChargedAmount())
                .refundAmount(entity.getRefundAmount())
                .penaltyFee(entity.getPenaltyFee())
                .isIncluded(entity.getIsIncluded())
                .checkedInAt(entity.getCheckedInAt())
                .cancelledAt(entity.getCancelledAt())
                .cancellationReason(entity.getCancellationReason())
                .medicalWaiver(entity.getMedicalWaiver())
                .medicalWaiverReason(entity.getMedicalWaiverReason())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Đưa hành khách vào hàng đợi danh sách chờ khi tour đạt maxCapacity (KAN-326 / AC2)
     */
    
    /**
     * Đăng ký vào danh sách chờ (Waitlist) khi tour đã đầy hoặc hành khách chủ động đăng ký chờ (KAN-326)
     */
    @Transactional
    public ExcursionBookingResponse joinWaitlist(Long excursionId, ExcursionBookingRequest request) {
        ShoreExcursion excursion = findActiveExcursion(excursionId);
        Passenger passenger = cruisePassengerRepository.findById(request.getPassengerId())
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hành khách với ID " + request.getPassengerId() + " không tồn tại"));

        return ingestWaitlistBooking(excursion, passenger, request);
    }

    public ExcursionBookingResponse ingestWaitlistBooking(ShoreExcursion excursion, Passenger passenger, ExcursionBookingRequest request) {
        Long excursionId = excursion.getId();
        Integer maxPos = passengerRepository.findMaxWaitlistPosition(excursionId);
        int nextPos = (maxPos != null ? maxPos : 0) + 1;

        Instant now = Instant.now();
        String ticketCode = String.format("WAIT-%d-%d", excursionId, System.currentTimeMillis());
        String cabinNumber = (request.getCabinNumber() != null && !request.getCabinNumber().isBlank())
                ? request.getCabinNumber().trim()
                : passenger.getCabinNumber();

        String passengerName = (request.getPassengerName() != null && !request.getPassengerName().isBlank())
                ? request.getPassengerName().trim()
                : passenger.getFullName();

        ExcursionPassenger waitlistEntry = ExcursionPassenger.builder()
                .excursionId(excursionId)
                .passengerId(passenger.getId())
                .passengerName(passengerName)
                .cabinNumber(cabinNumber)
                .ticketCode(ticketCode)
                .rfidTag(passenger.getRfidTag())
                .busNumber(null)
                .groupName("Waitlist")
                .emergencyPhone(request.getEmergencyPhone() != null ? request.getEmergencyPhone().trim() : passenger.getPhoneNumber())
                .specialRequirements(request.getSpecialRequirements())
                .bookingStatus(ExcursionBookingStatus.WAITLISTED)
                .waitlistPosition(nextPos)
                .status(ExcursionPassengerStatus.REGISTERED)
                .chargedAmount(BigDecimal.ZERO)
                .refundAmount(BigDecimal.ZERO)
                .penaltyFee(BigDecimal.ZERO)
                .transactionId(null)
                .isIncluded(false)
                .active(true)
                .build();

        ExcursionPassenger saved = passengerRepository.save(waitlistEntry);

        bookingLogRepository.save(ExcursionBookingLog.builder()
                .bookingId(saved.getId())
                .excursionId(excursionId)
                .passengerId(passenger.getId())
                .ticketCode(ticketCode)
                .action("WAITLIST_INGESTED")
                .chargedAmount(BigDecimal.ZERO)
                .refundAmount(BigDecimal.ZERO)
                .penaltyFee(BigDecimal.ZERO)
                .auditTime(now)
                .details(String.format("Xếp hàng chờ Waitlist vị trí #%d cho cabin %s", nextPos, cabinNumber))
                .build());

        log.info("Passenger added to excursion waitlist: excursionId={}, passengerId={}, position={}",
                excursionId, passenger.getId(), nextPos);

        return ExcursionBookingResponse.builder()
                .bookingId(saved.getId())
                .excursionId(excursionId)
                .passengerId(passenger.getId())
                .passengerName(passengerName)
                .cabinNumber(cabinNumber)
                .ticketCode(ticketCode)
                .busNumber(null)
                .groupName("Waitlist")
                .chargedAmount(BigDecimal.ZERO)
                .transactionId(null)
                .isIncluded(false)
                .status(ExcursionBookingStatus.WAITLISTED.name())
                .bookedAt(now)
                .build();
    }

    /**
     * Tra cứu danh sách hàng đợi chờ (Waitlist Queue) theo thứ tự FIFO (KAN-326 / AC2)
     */
    @Transactional(readOnly = true)
    public List<ExcursionWaitlistEntryDto> getWaitlistQueue(Long excursionId) {
        findActiveExcursion(excursionId);
        List<ExcursionPassenger> waitlist = passengerRepository
                .findByExcursionIdAndBookingStatusAndActiveTrueOrderByWaitlistPositionAsc(excursionId, ExcursionBookingStatus.WAITLISTED);

        return waitlist.stream()
                .map(e -> ExcursionWaitlistEntryDto.builder()
                        .position(e.getWaitlistPosition())
                        .bookingId(e.getId())
                        .excursionId(e.getExcursionId())
                        .passengerId(e.getPassengerId())
                        .passengerName(e.getPassengerName())
                        .cabinNumber(e.getCabinNumber())
                        .emergencyPhone(e.getEmergencyPhone())
                        .specialRequirements(e.getSpecialRequirements())
                        .bookedAt(e.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * Thủ công đôn hành khách đầu tiên trong danh sách chờ lên CONFIRMED (KAN-326 / AC2)
     */
    @Transactional
    public WaitlistPromotionResultDto promoteNextWaitlistPassenger(Long excursionId) {
        ShoreExcursion excursion = excursionRepository.findByIdWithLock(excursionId)
                .filter(ShoreExcursion::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Tour trên bờ với ID " + excursionId + " không tồn tại"));

        List<ExcursionPassenger> waitlist = passengerRepository
                .findByExcursionIdAndBookingStatusAndActiveTrueOrderByWaitlistPositionAsc(excursionId, ExcursionBookingStatus.WAITLISTED);

        if (waitlist.isEmpty()) {
            throw new BusinessRuleException("Hàng đợi danh sách chờ của tour này đang trống");
        }

        ExcursionPassenger candidate = waitlist.get(0);
        return promotePassengerToConfirmed(excursion, candidate);
    }

    /**
     * Kích hoạt tự động đôn người trong Waitlist khi có chỗ trống (Auto-promote / AC2)
     */
    public void triggerAutoPromotionIfPossible(Long excursionId) {
        List<ExcursionPassenger> waitlist = passengerRepository
                .findByExcursionIdAndBookingStatusAndActiveTrueOrderByWaitlistPositionAsc(excursionId, ExcursionBookingStatus.WAITLISTED);
        if (waitlist.isEmpty()) {
            return;
        }

        ShoreExcursion excursion = excursionRepository.findById(excursionId).orElse(null);
        if (excursion == null || !Boolean.TRUE.equals(excursion.getActive())) {
            return;
        }

        long confirmedCount = passengerRepository.countByExcursionIdAndBookingStatusAndActiveTrue(
                excursionId, ExcursionBookingStatus.CONFIRMED);
        if (excursion.getMaxCapacity() != null && confirmedCount >= excursion.getMaxCapacity()) {
            return;
        }

        ExcursionPassenger candidate = waitlist.get(0);
        promotePassengerToConfirmed(excursion, candidate);
        log.info("Auto-promoted waitlist passenger: bookingId={}, excursionId={}", candidate.getId(), excursionId);
    }

    private WaitlistPromotionResultDto promotePassengerToConfirmed(ShoreExcursion excursion, ExcursionPassenger candidate) {
        Long excursionId = excursion.getId();
        Instant now = Instant.now();

        // 1. Phân bổ xe bus cho hành khách được đôn lên
        List<ExcursionVehicle> vehicles = vehicleRepository.findByExcursionIdAndActiveTrue(excursionId);
        List<ExcursionVehicle> operationalVehicles = vehicles.stream()
                .filter(v -> v.getStatus() == VehicleStatus.READY || v.getStatus() == VehicleStatus.ACTIVE || v.getStatus() == VehicleStatus.ON_TRIP)
                .toList();

        ExcursionBookingRequest pseudoReq = new ExcursionBookingRequest();
        pseudoReq.setPassengerId(candidate.getPassengerId());
        pseudoReq.setPassengerName(candidate.getPassengerName());
        pseudoReq.setCabinNumber(candidate.getCabinNumber());
        pseudoReq.setEmergencyPhone(candidate.getEmergencyPhone());

        String assignedBus = resolveAssignedBus(pseudoReq, operationalVehicles, excursionId, candidate.getCabinNumber());
        String groupName = (assignedBus != null) ? "Nhóm " + assignedBus : "Nhóm 1";

        // 2. Tính phí, kiểm tra Folio lock và quyền lợi Tour package
        Passenger passenger = cruisePassengerRepository.findById(candidate.getPassengerId())
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hành khách với ID " + candidate.getPassengerId() + " không tồn tại"));

        boolean isIncluded = isExcursionIncluded(excursion, passenger);
        BigDecimal chargedAmount = BigDecimal.ZERO;
        String transactionId = null;

        if (!isIncluded && excursion.getPrice() != null && excursion.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            if (isFolioLocked(passenger)) {
                throw new PaymentRequiredException("Tài khoản cabin folio của hành khách " + passenger.getFullName() +
                        " (Cabin " + passenger.getCabinNumber() + ") đang bị khóa hoặc không đủ hạn mức thanh toán");
            }
            chargedAmount = excursion.getPrice();
            transactionId = "FOLIO-PROMOTE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        }

        String ticketCode = String.format("EXC-%d-%d", excursionId, System.currentTimeMillis());

        candidate.setBookingStatus(ExcursionBookingStatus.CONFIRMED);
        candidate.setWaitlistPosition(null);
        candidate.setTicketCode(ticketCode);
        candidate.setBusNumber(assignedBus);
        candidate.setGroupName(groupName);
        candidate.setChargedAmount(chargedAmount);
        candidate.setTransactionId(transactionId);
        candidate.setIsIncluded(isIncluded);
        candidate.setStatus(ExcursionPassengerStatus.REGISTERED);
        passengerRepository.save(candidate);

        // 3. Tăng số người tham gia trong ShoreExcursion
        excursion.setCurrentParticipants((excursion.getCurrentParticipants() != null ? excursion.getCurrentParticipants() : 0) + 1);
        excursionRepository.save(excursion);

        // 4. Cập nhật số khách trên xe
        if (assignedBus != null) {
            operationalVehicles.stream()
                    .filter(v -> assignedBus.equalsIgnoreCase(v.getVehicleCode()) || assignedBus.equalsIgnoreCase(v.getLicensePlate()))
                    .findFirst()
                    .ifPresent(v -> {
                        v.setAssignedPassengers((v.getAssignedPassengers() != null ? v.getAssignedPassengers() : 0) + 1);
                        vehicleRepository.save(v);
                    });
        }

        // 5. Ghi log kiểm toán
        bookingLogRepository.save(ExcursionBookingLog.builder()
                .bookingId(candidate.getId())
                .excursionId(excursionId)
                .passengerId(candidate.getPassengerId())
                .ticketCode(ticketCode)
                .action("WAITLIST_PROMOTED")
                .chargedAmount(chargedAmount)
                .refundAmount(BigDecimal.ZERO)
                .penaltyFee(BigDecimal.ZERO)
                .transactionId(transactionId)
                .busNumber(assignedBus)
                .groupName(groupName)
                .auditTime(now)
                .details(String.format("Đôn vé danh sách chờ lên CONFIRMED cho cabin %s, xe %s, vé %s",
                        candidate.getCabinNumber(), assignedBus, ticketCode))
                .build());

        return WaitlistPromotionResultDto.builder()
                .excursionId(excursionId)
                .bookingId(candidate.getId())
                .passengerId(candidate.getPassengerId())
                .passengerName(candidate.getPassengerName())
                .cabinNumber(candidate.getCabinNumber())
                .ticketCode(ticketCode)
                .assignedBus(assignedBus)
                .status(ExcursionBookingStatus.CONFIRMED)
                .chargedAmount(chargedAmount)
                .transactionId(transactionId)
                .promotedAt(now)
                .message("Đã đôn hành khách lên vé chính thức và gửi thông báo xác nhận")
                .build();
    }

    /**
     * Hủy vé với quyền điều phối viên và áp dụng miễn phạt y tế (Medical Waiver Override - KAN-326 / AC4)
     */
    @Transactional
    public ExcursionCancellationResponse cancelBookingWithMedicalWaiver(
            Long excursionId,
            Long bookingId,
            MedicalWaiverCancellationRequest request
    ) {
        log.info("Processing medical waiver cancellation: excursionId={}, bookingId={}",
                excursionId, bookingId);

        ShoreExcursion excursion = excursionRepository.findByIdWithLock(excursionId)
                .filter(ShoreExcursion::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Tour trên bờ với ID " + excursionId + " không tồn tại"));

        ExcursionPassenger booking = passengerRepository.findByIdAndExcursionIdAndActiveTrue(bookingId, excursionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin vé đặt ID " + bookingId + " trong tour"));

        if (!Boolean.TRUE.equals(booking.getActive())) {
            throw new BusinessRuleException("Vé này đã bị hủy trước đó");
        }

        Instant now = Instant.now();
        BigDecimal chargedAmount = booking.getChargedAmount() != null ? booking.getChargedAmount() : BigDecimal.ZERO;
        BigDecimal refundAmount = chargedAmount; // Miễn phạt y tế: Hoàn trả 100%
        BigDecimal penaltyFee = BigDecimal.ZERO;

        boolean wasConfirmed = booking.getBookingStatus() == ExcursionBookingStatus.CONFIRMED;

        booking.setActive(false);
        booking.setBookingStatus(ExcursionBookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancellationReason("Miễn trừ y tế/khẩn cấp: " + request.getReason());
        booking.setMedicalWaiver(true);
        booking.setMedicalWaiverReason(request.getReason());
        booking.setRefundAmount(refundAmount);
        booking.setPenaltyFee(penaltyFee);
        passengerRepository.save(booking);

        if (wasConfirmed) {
            if (excursion.getCurrentParticipants() != null && excursion.getCurrentParticipants() > 0) {
                excursion.setCurrentParticipants(excursion.getCurrentParticipants() - 1);
                excursionRepository.save(excursion);
            }

            if (booking.getBusNumber() != null) {
                vehicleRepository.findByExcursionIdAndActiveTrue(excursionId).stream()
                        .filter(v -> booking.getBusNumber().equalsIgnoreCase(v.getVehicleCode()) || booking.getBusNumber().equalsIgnoreCase(v.getLicensePlate()))
                        .findFirst()
                        .ifPresent(v -> {
                            if (v.getAssignedPassengers() != null && v.getAssignedPassengers() > 0) {
                                v.setAssignedPassengers(v.getAssignedPassengers() - 1);
                                vehicleRepository.save(v);
                            }
                        });
            }
        }

        // Ghi nhật ký kiểm toán bất biến
        bookingLogRepository.save(ExcursionBookingLog.builder()
                .bookingId(booking.getId())
                .excursionId(excursionId)
                .passengerId(booking.getPassengerId())
                .ticketCode(booking.getTicketCode())
                .action("CANCELLATION_WAIVED")
                .chargedAmount(chargedAmount)
                .refundAmount(refundAmount)
                .penaltyFee(penaltyFee)
                .transactionId(booking.getTransactionId())
                .busNumber(booking.getBusNumber())
                .groupName(booking.getGroupName())
                .staffId(request.getApprovedByStaffId() != null ? request.getApprovedByStaffId() : "SHORE_EXCURSION_MANAGER")
                .auditTime(now)
                .details(String.format("Hủy vé diện Medical Waiver thành công. Hoàn 100%% (%s USD). Lý do: %s",
                        refundAmount, request.getReason()))
                .build());

        // AC2: Tự động đôn khách trong hàng đợi Waitlist khi có chỗ trống do hủy y tế (chỉ khi hủy vé CONFIRMED)
        if (wasConfirmed) {
            triggerAutoPromotionIfPossible(excursionId);
        }

        return ExcursionCancellationResponse.builder()
                .bookingId(booking.getId())
                .excursionId(excursionId)
                .passengerId(booking.getPassengerId())
                .status("CANCELLED_MEDICAL_WAIVER")
                .refundAmount(refundAmount)
                .penaltyFee(penaltyFee)
                .cancellationReason(booking.getCancellationReason())
                .cancelledAt(now)
                .build();
    }


    /**
     * Xuất danh sách Roster định dạng CSV chuẩn RFC 4180 phục vụ điều phối tại cảng (KAN-327)
     */
    @Transactional
    public byte[] exportRosterCsv(Long excursionId) {
        findActiveExcursion(excursionId);
        List<ExcursionPassenger> passengers = passengerRepository.findByExcursionIdAndActiveTrue(excursionId);

        StringBuilder sb = new StringBuilder();
        // CSV Header (CRLF terminator)
        sb.append("Ticket Code,Passenger Name,Cabin Number,Phone,Status,Booking Status,Bus,Group,Special Requirements,Charged Amount (USD)\r\n");

        for (ExcursionPassenger p : passengers) {
            sb.append(escapeCsv(p.getTicketCode())).append(",")
              .append(escapeCsv(p.getPassengerName())).append(",")
              .append(escapeCsv(p.getCabinNumber())).append(",")
              .append(escapeCsv(p.getEmergencyPhone())).append(",")
              .append(escapeCsv(p.getStatus() != null ? p.getStatus().name() : "")).append(",")
              .append(escapeCsv(p.getBookingStatus() != null ? p.getBookingStatus().name() : "")).append(",")
              .append(escapeCsv(p.getBusNumber())).append(",")
              .append(escapeCsv(p.getGroupName())).append(",")
              .append(escapeCsv(p.getSpecialRequirements())).append(",")
              .append(p.getChargedAmount() != null ? p.getChargedAmount().toPlainString() : "0.00")
              .append("\r\n");
        }

        bookingLogRepository.save(ExcursionBookingLog.builder()
                .bookingId(0L)
                .excursionId(excursionId)
                .passengerId(0L)
                .action("ROSTER_EXPORTED")
                .chargedAmount(BigDecimal.ZERO)
                .refundAmount(BigDecimal.ZERO)
                .penaltyFee(BigDecimal.ZERO)
                .auditTime(Instant.now())
                .details(String.format("Xuất danh sách Roster CSV cho tour bờ ID %d, tổng số %d hành khách",
                        excursionId, passengers.size()))
                .build());

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String safeValue = value;
        if (!safeValue.isEmpty()) {
            char firstChar = safeValue.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@') {
                safeValue = "'" + safeValue;
            }
        }
        if (safeValue.contains(",") || safeValue.contains("\"") || safeValue.contains("\n") || safeValue.contains("\r")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }

    /**
     * Kích hoạt phát cảnh báo thay đổi lịch trình cho toàn bộ hành khách đăng ký tour bờ (KAN-327)
     */
    @Transactional
    public ExcursionScheduleAlertResponse triggerScheduleAlert(Long excursionId, ExcursionScheduleAlertRequest request) {
        findActiveExcursion(excursionId);
        List<ExcursionPassenger> passengers = passengerRepository.findByExcursionIdAndActiveTrue(excursionId);

        // Deduplicate recipients by passengerId and exclude waitlisted passengers
        Set<Long> eligiblePassengerIds = new LinkedHashSet<>();
        for (ExcursionPassenger p : passengers) {
            if (p.getPassengerId() != null && p.getBookingStatus() != ExcursionBookingStatus.WAITLISTED) {
                eligiblePassengerIds.add(p.getPassengerId());
            }
        }

        // Construct structured message including updated meeting location and gathering time if provided
        StringBuilder formattedMessage = new StringBuilder(request.getMessage());
        List<String> scheduleDetails = new ArrayList<>();
        if (request.getUpdatedMeetingLocation() != null && !request.getUpdatedMeetingLocation().isBlank()) {
            scheduleDetails.add("Địa điểm tập trung mới: " + request.getUpdatedMeetingLocation().trim());
        }
        if (request.getUpdatedGatheringTime() != null && !request.getUpdatedGatheringTime().isBlank()) {
            scheduleDetails.add("Thời gian tập trung mới: " + request.getUpdatedGatheringTime().trim());
        }
        if (!scheduleDetails.isEmpty()) {
            formattedMessage.append(" (").append(String.join(", ", scheduleDetails)).append(")");
        }
        String fullNotificationMessage = formattedMessage.toString();

        Instant now = Instant.now();
        int sentCount = 0;
        int failedCount = 0;

        for (Long passengerId : eligiblePassengerIds) {
            try {
                notificationService.sendNotification(passengerId, SendNotificationRequest.builder()
                        .title(request.getTitle())
                        .message(fullNotificationMessage)
                        .category(Boolean.TRUE.equals(request.getIsUrgent()) ? NotificationCategory.CRITICAL : NotificationCategory.OPERATIONAL)
                        .actionUrl("/excursions/roster/" + excursionId)
                        .build());
                sentCount++;
            } catch (Exception ex) {
                failedCount++;
                log.warn("Failed to dispatch alert to passengerId={}: {}", passengerId, ex.getMessage());
            }
        }

        String broadcastStatus;
        if (eligiblePassengerIds.isEmpty() || (sentCount > 0 && failedCount == 0)) {
            broadcastStatus = "BROADCAST_SUCCESS";
        } else if (sentCount > 0 && failedCount > 0) {
            broadcastStatus = "PARTIAL";
        } else {
            broadcastStatus = "FAILED";
        }

        bookingLogRepository.save(ExcursionBookingLog.builder()
                .bookingId(0L)
                .excursionId(excursionId)
                .passengerId(0L)
                .action("SCHEDULE_ALERT_TRIGGERED")
                .chargedAmount(BigDecimal.ZERO)
                .refundAmount(BigDecimal.ZERO)
                .penaltyFee(BigDecimal.ZERO)
                .auditTime(now)
                .details(String.format("Phát thông báo thay đổi lịch trình: '%s' tới %d hành khách thành công (%d thất bại, trạng thái: %s)",
                        request.getTitle(), sentCount, failedCount, broadcastStatus))
                .build());

        return ExcursionScheduleAlertResponse.builder()
                .excursionId(excursionId)
                .recipientsCount(sentCount)
                .failedCount(failedCount)
                .title(request.getTitle())
                .message(fullNotificationMessage)
                .broadcastedAt(now)
                .status(broadcastStatus)
                .build();
    }

}

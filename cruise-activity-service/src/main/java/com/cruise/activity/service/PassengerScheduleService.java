package com.cruise.activity.service;

import com.cruise.activity.dto.PersonalScheduleItemDto;
import com.cruise.activity.dto.PersonalScheduleResponse;
import com.cruise.activity.dto.ScheduleConflictDto;
import com.cruise.activity.entity.ActivityAttendance;
import com.cruise.activity.entity.ActivityRegistration;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ActivityAttendanceStatus;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.RegistrationStatus;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ActivityAttendanceRepository;
import com.cruise.activity.repository.ActivityRegistrationRepository;
import com.cruise.activity.repository.ExcursionPassengerRepository;
import com.cruise.activity.repository.OnboardActivityRepository;
import com.cruise.activity.repository.PassengerRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;

/**
 * Service tổng hợp lịch trình cá nhân / cabin và engine phát hiện xung đột thời gian (KAN-349 / KAN-350)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PassengerScheduleService {

    private final PassengerRepository passengerRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final OnboardActivityRepository onboardActivityRepository;
    private final ExcursionPassengerRepository excursionPassengerRepository;
    private final ShoreExcursionRepository shoreExcursionRepository;
    private final ActivityAttendanceRepository activityAttendanceRepository;

    /**
     * Tổng hợp dòng thời gian lịch trình cá nhân hoặc cabin theo ngày hải trình (KAN-349)
     *
     * @param passengerId ID hành khách
     * @param dayNumber   Số ngày trong hải trình (1, 2,...), null nếu lấy toàn bộ
     * @param scope       Phạm vi lịch ("my" hoặc "cabin")
     * @return PersonalScheduleResponse chứa danh sách sự kiện đã đăng ký và các cảnh báo xung đột
     */
    public PersonalScheduleResponse getPersonalSchedule(Long passengerId, Integer dayNumber, String scope) {
        Passenger passenger = passengerRepository.findById(passengerId)
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hành khách với ID " + passengerId + " không tồn tại"));

        String normalizedScope = (scope != null && scope.trim().equalsIgnoreCase("cabin")) ? "cabin" : "my";

        // Xác định danh sách hành khách mục tiêu theo scope
        List<Passenger> targetPassengers = new ArrayList<>();
        if ("cabin".equals(normalizedScope) && passenger.getCabinNumber() != null && !passenger.getCabinNumber().isBlank()) {
            targetPassengers.addAll(passengerRepository.findByCabinNumberAndActiveTrue(passenger.getCabinNumber()));
        }
        if (targetPassengers.isEmpty()) {
            targetPassengers.add(passenger);
        }

        List<PersonalScheduleItemDto> items = new ArrayList<>();

        // 1. Thu thập hoạt động trên tàu (Onboard Activities)
        collectActivityItems(targetPassengers, passenger.getCabinNumber(), dayNumber, items);

        // 2. Thu thập tour tham quan trên bờ (Shore Excursions)
        collectExcursionItems(targetPassengers, passenger, normalizedScope, dayNumber, items);

        // 3. Sắp xếp theo thứ tự startTime tăng dần, sau đó theo endTime và title
        items.sort(Comparator
                .comparing(PersonalScheduleItemDto::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PersonalScheduleItemDto::getEndTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PersonalScheduleItemDto::getTitle, Comparator.nullsLast(Comparator.naturalOrder())));

        // 4. Chạy engine phát hiện xung đột lịch và cảnh báo giờ tập trung (KAN-350)
        List<ScheduleConflictDto> conflicts = detectScheduleConflicts(items, normalizedScope);

        return PersonalScheduleResponse.builder()
                .passengerId(passenger.getId())
                .passengerName(passenger.getFullName())
                .cabinNumber(passenger.getCabinNumber())
                .dayNumber(dayNumber)
                .scope(normalizedScope)
                .totalItems(items.size())
                .totalConflicts(conflicts.size())
                .items(items)
                .conflicts(conflicts)
                .build();
    }

    /**
     * Lấy danh sách xung đột lịch trình cá nhân / cabin theo ngày (KAN-350)
     *
     * @param passengerId ID hành khách
     * @param dayNumber   Số ngày trong hải trình
     * @return Danh sách các xung đột lịch phát hiện được
     */
    public List<ScheduleConflictDto> getScheduleConflicts(Long passengerId, Integer dayNumber) {
        PersonalScheduleResponse schedule = getPersonalSchedule(passengerId, dayNumber, "my");
        return schedule.getConflicts();
    }

    private void collectActivityItems(
            List<Passenger> targetPassengers,
            String cabinNumber,
            Integer dayNumber,
            List<PersonalScheduleItemDto> items
    ) {
        Set<String> processedKeys = new HashSet<>();

        for (Passenger p : targetPassengers) {
            List<ActivityRegistration> registrations =
                    activityRegistrationRepository.findByPassengerIdAndActiveTrueOrderByRegisteredAtDesc(p.getId());

            for (ActivityRegistration reg : registrations) {
                String dedupeKey = "ACT-" + reg.getActivityId() + "-" + reg.getPassengerId();
                if (!processedKeys.add(dedupeKey)) {
                    continue;
                }

                OnboardActivity act = onboardActivityRepository.findById(reg.getActivityId()).orElse(null);
                if (act == null || !Boolean.TRUE.equals(act.getActive())) {
                    continue;
                }

                if (dayNumber != null && !dayNumber.equals(act.getDayNumber())) {
                    continue;
                }

                // Xác định trạng thái điểm danh / đăng ký
                String status = resolveActivityStatus(reg);

                items.add(PersonalScheduleItemDto.builder()
                        .eventId(act.getId())
                        .itemType("ACTIVITY")
                        .title(act.getTitle())
                        .category(act.getCategory() != null ? act.getCategory() : "Hoạt động")
                        .dayNumber(act.getDayNumber())
                        .activityDate(act.getActivityDate())
                        .startTime(act.getStartTime())
                        .endTime(act.getEndTime())
                        .assemblyTime(act.getStartTime())
                        .departureTime(null)
                        .assemblyWarning(null)
                        .location(act.getLocation())
                        .deck("Boong tàu")
                        .status(status)
                        .passengerId(reg.getPassengerId())
                        .passengerName(reg.getPassengerName() != null ? reg.getPassengerName() : p.getFullName())
                        .cabinNumber(reg.getCabinNumber() != null ? reg.getCabinNumber() : cabinNumber)
                        .ticketCode(reg.getTransactionId() != null ? reg.getTransactionId() : ("ACT-" + reg.getId()))
                        .price(act.getPrice() != null ? act.getPrice() : BigDecimal.ZERO)
                        .isIncluded(act.getIsIncluded())
                        .hasConflict(false)
                        .conflictReason(null)
                        .conflictWithIds(new ArrayList<>())
                        .build());
            }
        }
    }

    private void collectExcursionItems(
            List<Passenger> targetPassengers,
            Passenger primaryPassenger,
            String scope,
            Integer dayNumber,
            List<PersonalScheduleItemDto> items
    ) {
        List<ExcursionPassenger> excBookings = new ArrayList<>();
        if ("cabin".equals(scope) && primaryPassenger.getCabinNumber() != null) {
            excBookings = excursionPassengerRepository.findByCabinNumberAndActiveTrue(primaryPassenger.getCabinNumber());
        } else {
            // "my" scope: match by cabin and filter to this passenger, or match by name
            List<ExcursionPassenger> cabinBookings = primaryPassenger.getCabinNumber() != null
                    ? excursionPassengerRepository.findByCabinNumberAndActiveTrue(primaryPassenger.getCabinNumber())
                    : Collections.emptyList();

            for (ExcursionPassenger ep : cabinBookings) {
                if (matchesPassenger(ep, primaryPassenger)) {
                    excBookings.add(ep);
                }
            }
            if (excBookings.isEmpty()) {
                excBookings = excursionPassengerRepository.findByPassengerNameIgnoreCaseAndActiveTrue(primaryPassenger.getFullName());
            }
        }

        Set<String> processedKeys = new HashSet<>();

        for (ExcursionPassenger ep : excBookings) {
            String dedupeKey = "EXC-" + ep.getExcursionId() + "-" + (ep.getTicketCode() != null ? ep.getTicketCode() : ep.getId());
            if (!processedKeys.add(dedupeKey)) {
                continue;
            }

            ShoreExcursion exc = shoreExcursionRepository.findById(ep.getExcursionId()).orElse(null);
            if (exc == null || !Boolean.TRUE.equals(exc.getActive())) {
                continue;
            }

            if (dayNumber != null && !dayNumber.equals(exc.getDayNumber())) {
                continue;
            }

            String status = resolveExcursionStatus(ep);

            // Tính toán chênh lệch giờ tập trung (assembly time vs departure time) cho excursion (KAN-350)
            String assemblyWarning = null;
            if (exc.getGatheringTime() != null && exc.getDepartureTime() != null) {
                long diffMinutes = Duration.between(exc.getGatheringTime(), exc.getDepartureTime()).toMinutes();
                if (diffMinutes > 0) {
                    assemblyWarning = String.format("Tập trung trước giờ khởi hành %d phút tại %s",
                            diffMinutes,
                            exc.getMeetingPoint() != null ? exc.getMeetingPoint() : "Điểm hẹn");
                }
            }

            LocalTime effectiveStartTime = exc.getGatheringTime() != null ? exc.getGatheringTime() : exc.getDepartureTime();
            LocalTime effectiveEndTime = exc.getReturnToShipTime() != null ? exc.getReturnToShipTime() : exc.getDepartureTime();

            BigDecimal price = (exc.getPrice() != null && !Boolean.TRUE.equals(exc.getIsIncluded()))
                    ? exc.getPrice()
                    : BigDecimal.ZERO;

            items.add(PersonalScheduleItemDto.builder()
                    .eventId(exc.getId())
                    .itemType("EXCURSION")
                    .title(exc.getTitle())
                    .category("Tour trên bờ")
                    .dayNumber(exc.getDayNumber())
                    .activityDate(exc.getExcursionDate())
                    .startTime(effectiveStartTime)
                    .endTime(effectiveEndTime)
                    .assemblyTime(exc.getGatheringTime())
                    .departureTime(exc.getDepartureTime())
                    .assemblyWarning(assemblyWarning)
                    .location(exc.getPortName() != null ? exc.getPortName() : exc.getMeetingPoint())
                    .deck(exc.getMeetingPoint())
                    .status(status)
                    .passengerId(ep.getPassengerId() != null ? ep.getPassengerId() : primaryPassenger.getId())
                    .passengerName(ep.getPassengerName())
                    .cabinNumber(ep.getCabinNumber())
                    .ticketCode(ep.getTicketCode())
                    .price(price)
                    .isIncluded(exc.getIsIncluded())
                    .hasConflict(false)
                    .conflictReason(null)
                    .conflictWithIds(new ArrayList<>())
                    .build());
        }
    }

    private boolean matchesPassenger(ExcursionPassenger ep, Passenger passenger) {
        if (ep.getPassengerId() != null && ep.getPassengerId().equals(passenger.getId())) {
            return true;
        }
        return ep.getPassengerName() != null && ep.getPassengerName().trim().equalsIgnoreCase(passenger.getFullName().trim());
    }

    private String resolveActivityStatus(ActivityRegistration reg) {
        Optional<ActivityAttendance> attendance =
                activityAttendanceRepository.findByActivityIdAndPassengerIdAndActiveTrue(reg.getActivityId(), reg.getPassengerId());

        if (attendance.isPresent()) {
            if (attendance.get().getStatus() == ActivityAttendanceStatus.CHECKED_IN) {
                return "CHECKED_IN";
            }
            if (attendance.get().getStatus() == ActivityAttendanceStatus.CANCELLED) {
                return "CANCELLED";
            }
        }

        if (reg.getStatus() == RegistrationStatus.CANCELLED) {
            return "CANCELLED";
        }

        return "REGISTERED";
    }

    private String resolveExcursionStatus(ExcursionPassenger ep) {
        if (ep.getStatus() == null) {
            return "REGISTERED";
        }
        if (ep.getStatus() == ExcursionPassengerStatus.CHECKED_IN || ep.getStatus() == ExcursionPassengerStatus.ON_TOUR) {
            return "CHECKED_IN";
        }
        if (ep.getStatus() == ExcursionPassengerStatus.RETURNED_SAFE) {
            return "COMPLETED";
        }
        return "REGISTERED";
    }

    /**
     * Thuật toán phát hiện overlap giữa các time interval (start, end) của các sự kiện (KAN-350)
     */
    private List<ScheduleConflictDto> detectScheduleConflicts(List<PersonalScheduleItemDto> items, String scope) {
        List<ScheduleConflictDto> conflicts = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            PersonalScheduleItemDto a = items.get(i);
            if ("CANCELLED".equals(a.getStatus()) || a.getStartTime() == null || a.getEndTime() == null) {
                continue;
            }

            for (int j = i + 1; j < items.size(); j++) {
                PersonalScheduleItemDto b = items.get(j);
                if ("CANCELLED".equals(b.getStatus()) || b.getStartTime() == null || b.getEndTime() == null) {
                    continue;
                }

                // Nếu trong phạm vi cabin, chỉ gắn cờ xung đột nếu hai sự kiện thuộc về CÙNG một người
                if ("cabin".equals(scope)) {
                    boolean samePassenger = (a.getPassengerId() != null && a.getPassengerId().equals(b.getPassengerId()))
                            || (a.getPassengerName() != null && a.getPassengerName().equalsIgnoreCase(b.getPassengerName()));
                    if (!samePassenger) {
                        continue;
                    }
                }

                // Tính toán khoảng trùng thời gian (overlap interval)
                LocalTime startA = a.getStartTime();
                LocalTime endA = a.getEndTime();
                LocalTime startB = b.getStartTime();
                LocalTime endB = b.getEndTime();

                LocalTime overlapStart = startA.isAfter(startB) ? startA : startB;
                LocalTime overlapEnd = endA.isBefore(endB) ? endA : endB;

                if (overlapEnd.isAfter(overlapStart)) {
                    long overlapMinutes = Duration.between(overlapStart, overlapEnd).toMinutes();
                    if (overlapMinutes >= 1) {
                        a.setHasConflict(true);
                        b.setHasConflict(true);

                        if (!a.getConflictWithIds().contains(b.getEventId())) {
                            a.getConflictWithIds().add(b.getEventId());
                        }
                        if (!b.getConflictWithIds().contains(a.getEventId())) {
                            b.getConflictWithIds().add(a.getEventId());
                        }

                        a.setConflictReason(String.format("Trùng khung giờ với '%s' (%d phút)", b.getTitle(), overlapMinutes));
                        b.setConflictReason(String.format("Trùng khung giờ với '%s' (%d phút)", a.getTitle(), overlapMinutes));

                        conflicts.add(ScheduleConflictDto.builder()
                                .firstEventId(a.getEventId())
                                .firstEventTitle(a.getTitle())
                                .firstEventType(a.getItemType())
                                .firstStartTime(a.getStartTime())
                                .firstEndTime(a.getEndTime())
                                .secondEventId(b.getEventId())
                                .secondEventTitle(b.getTitle())
                                .secondEventType(b.getItemType())
                                .secondStartTime(b.getStartTime())
                                .secondEndTime(b.getEndTime())
                                .overlapMinutes(overlapMinutes)
                                .description(String.format("Xung đột thời gian %d phút giữa '%s' và '%s'",
                                        overlapMinutes, a.getTitle(), b.getTitle()))
                                .build());
                    }
                }
            }
        }

        return conflicts;
    }
}

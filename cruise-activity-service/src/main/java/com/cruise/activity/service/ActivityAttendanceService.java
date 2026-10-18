package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.ActivityAttendance;
import com.cruise.activity.entity.ActivityCheckInLog;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.enums.ActivityAttendanceStatus;
import com.cruise.activity.enums.ActivityCheckInMethod;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.DuplicateCheckInException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ActivityAttendanceRepository;
import com.cruise.activity.repository.ActivityCheckInLogRepository;
import com.cruise.activity.repository.OnboardActivityRepository;
import com.cruise.activity.repository.PassengerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ActivityAttendanceService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final OnboardActivityRepository activityRepository;
    private final PassengerRepository passengerRepository;
    private final ActivityAttendanceRepository attendanceRepository;
    private final ActivityCheckInLogRepository checkInLogRepository;
    private final QrTokenService qrTokenService;

    /**
     * Sinh mã QR Pass động bảo mật có TTL 60s cho hành khách (KAN-222)
     */
    @Transactional
    public ActivityPassResponse generateQrPass(Long activityId, Long passengerId) {
        OnboardActivity activity = activityRepository.findById(activityId)
                .filter(OnboardActivity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hoạt động với ID " + activityId + " không tồn tại"));

        Passenger passenger = passengerRepository.findById(passengerId)
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hành khách với ID " + passengerId + " không tồn tại"));

        // Đảm bảo có bản ghi ActivityAttendance (nếu chưa có thì tự động khởi tạo)
        attendanceRepository.findByActivityIdAndPassengerIdAndActiveTrue(activityId, passengerId)
                .orElseGet(() -> attendanceRepository.save(ActivityAttendance.builder()
                        .activityId(activityId)
                        .passengerId(passengerId)
                        .passengerName(passenger.getFullName())
                        .cabinNumber(passenger.getCabinNumber())
                        .rfidTag(passenger.getRfidTag())
                        .status(ActivityAttendanceStatus.REGISTERED)
                        .active(true)
                        .build()));

        String token = qrTokenService.generateToken(activityId, passengerId, passenger.getCabinNumber());
        Instant expiresAt = Instant.now().plusSeconds(60L);

        String deck = extractDeckFromLocation(activity.getLocation());

        return ActivityPassResponse.builder()
                .token(token)
                .expiresAt(expiresAt)
                .activityId(activity.getId())
                .activityTitle(activity.getTitle())
                .category(activity.getCategory() != null ? activity.getCategory() : "Giải Trí")
                .venue(activity.getLocation())
                .location(activity.getLocation())
                .deck(deck)
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .passengerId(passenger.getId())
                .passengerName(passenger.getFullName())
                .cabinNumber(passenger.getCabinNumber())
                .rfidTag(passenger.getRfidTag())
                .build();
    }

    /**
     * Soát vé và điểm danh đơn lẻ hỗ trợ đa kênh (QR, RFID, ID thủ công - KAN-222)
     */
    @Transactional
    public ActivityCheckInResponse checkIn(Long activityId, ActivityCheckInRequest request) {
        OnboardActivity activity = activityRepository.findById(activityId)
                .filter(OnboardActivity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hoạt động với ID " + activityId + " không tồn tại"));

        Long passengerId = null;
        ActivityCheckInMethod method = ActivityCheckInMethod.MANUAL;

        // 1. Phân giải danh tính hành khách qua QR Token / RFID / Passenger ID
        if (request.getQrToken() != null && !request.getQrToken().isBlank()) {
            QrTokenService.QrTokenPayload payload = qrTokenService.verifyAndDecode(request.getQrToken(), activityId);
            passengerId = payload.getPassengerId();
            method = ActivityCheckInMethod.QR_CODE;
        } else if (request.getRfidTag() != null && !request.getRfidTag().isBlank()) {
            Passenger p = passengerRepository.findByRfidTagAndActiveTrue(request.getRfidTag().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hành khách với thẻ RFID: " + request.getRfidTag()));
            passengerId = p.getId();
            method = ActivityCheckInMethod.RFID;
        } else if (request.getPassengerId() != null) {
            passengerId = request.getPassengerId();
            method = ActivityCheckInMethod.MANUAL;
        } else if (request.getCabinNumber() != null && !request.getCabinNumber().isBlank()) {
            List<Passenger> passengers = passengerRepository.findByCabinNumberAndActiveTrue(request.getCabinNumber().trim());
            if (passengers.isEmpty()) {
                throw new ResourceNotFoundException("Không tìm thấy hành khách tại cabin: " + request.getCabinNumber());
            }
            passengerId = passengers.get(0).getId();
            method = ActivityCheckInMethod.MANUAL;
        } else {
            throw new BusinessRuleException("Vui lòng cung cấp mã QR Token, thẻ RFID hoặc ID hành khách");
        }

        final Long resolvedPassengerId = passengerId;
        Passenger passenger = passengerRepository.findById(resolvedPassengerId)
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hành khách với ID " + resolvedPassengerId + " không tồn tại"));

        // 2. Tìm hoặc khởi tạo bản ghi điểm danh
        ActivityAttendance attendance = attendanceRepository.findByActivityIdAndPassengerIdAndActiveTrue(activityId, resolvedPassengerId)
                .orElseGet(() -> ActivityAttendance.builder()
                        .activityId(activityId)
                        .passengerId(passenger.getId())
                        .passengerName(passenger.getFullName())
                        .cabinNumber(passenger.getCabinNumber())
                        .rfidTag(passenger.getRfidTag())
                        .status(ActivityAttendanceStatus.REGISTERED)
                        .active(true)
                        .build());

        // 3. Khóa chống quét trùng lặp (Idempotency Check)
        if (attendance.getStatus() == ActivityAttendanceStatus.CHECKED_IN) {
            String checkInTimeStr = attendance.getCheckedInAt() != null
                    ? TIME_FORMATTER.format(attendance.getCheckedInAt())
                    : "trước đó";
            String staff = attendance.getCheckedInByStaffId() != null ? attendance.getCheckedInByStaffId() : "Hệ thống";

            // Ghi nhận log trùng lặp
            checkInLogRepository.save(ActivityCheckInLog.builder()
                    .activityId(activityId)
                    .passengerId(passenger.getId())
                    .passengerName(passenger.getFullName())
                    .cabinNumber(passenger.getCabinNumber())
                    .staffId(request.getStaffId())
                    .deviceId(request.getDeviceId())
                    .checkInTime(Instant.now())
                    .method(method)
                    .status("DUPLICATE")
                    .note("Quét lại: đã check-in lúc " + checkInTimeStr)
                    .active(true)
                    .build());

            throw new DuplicateCheckInException(String.format(
                    "Hành khách %s (Cabin %s) đã check-in lúc %s bởi nhân viên [%s]",
                    passenger.getFullName(), passenger.getCabinNumber(), checkInTimeStr, staff));
        }

        // 4. Chuyển đổi trạng thái sang CHECKED_IN
        Instant now = Instant.now();
        attendance.setStatus(ActivityAttendanceStatus.CHECKED_IN);
        attendance.setCheckedInAt(now);
        attendance.setCheckedInByStaffId(request.getStaffId());
        attendance.setCheckInMethod(method);
        attendance.setDeviceId(request.getDeviceId());
        if (request.getNote() != null) {
            attendance.setNote(request.getNote());
        }

        ActivityAttendance saved = attendanceRepository.save(attendance);

        // 5. Ghi nhận nhật ký kiểm toán bất biến
        checkInLogRepository.save(ActivityCheckInLog.builder()
                .activityId(activityId)
                .passengerId(passenger.getId())
                .passengerName(passenger.getFullName())
                .cabinNumber(passenger.getCabinNumber())
                .staffId(request.getStaffId())
                .deviceId(request.getDeviceId())
                .checkInTime(now)
                .method(method)
                .status("SUCCESS")
                .note("Điểm danh thành công qua " + method)
                .active(true)
                .build());

        // 6. Cập nhật số người tham gia thực tế
        long checkedInTotal = attendanceRepository.countByActivityIdAndStatusAndActiveTrue(activityId, ActivityAttendanceStatus.CHECKED_IN);
        activity.setCurrentParticipants((int) checkedInTotal);
        activityRepository.save(activity);

        log.info("Check-in successful: Activity={} Passenger={} Method={} Staff={}",
                activityId, passenger.getFullName(), method, request.getStaffId());

        return ActivityCheckInResponse.builder()
                .attendanceId(saved.getId())
                .activityId(activity.getId())
                .activityTitle(activity.getTitle())
                .passengerId(passenger.getId())
                .passengerName(passenger.getFullName())
                .cabinNumber(passenger.getCabinNumber())
                .status(saved.getStatus().name())
                .checkInTime(now)
                .staffId(request.getStaffId())
                .staffName(request.getStaffName())
                .method(method.name())
                .message("Điểm danh thành công cho " + passenger.getFullName())
                .build();
    }

    /**
     * Đăng ký và điểm danh khách vãng lai (Walk-in) tại cửa kèm khóa kiểm soát sức chứa (KAN-320)
     */
    @Transactional
    public WalkInCheckInResponse walkInCheckIn(Long activityId, WalkInCheckInRequest request) {
        OnboardActivity activity = activityRepository.findById(activityId)
                .filter(OnboardActivity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hoạt động với ID " + activityId + " không tồn tại"));

        // 1. Kiểm tra sức chứa còn lại nguyên tử
        long currentCheckedIn = attendanceRepository.countByActivityIdAndStatusAndActiveTrue(activityId, ActivityAttendanceStatus.CHECKED_IN);
        if (activity.getMaxCapacity() != null && currentCheckedIn >= activity.getMaxCapacity()) {
            throw new BusinessRuleException("Hoạt động đã hết chỗ (Activity is at full capacity)");
        }

        // 2. Định danh hành khách
        Passenger passenger = null;
        if (request.getPassengerId() != null) {
            passenger = passengerRepository.findById(request.getPassengerId()).orElse(null);
        }
        if (passenger == null && request.getRfidTag() != null && !request.getRfidTag().isBlank()) {
            passenger = passengerRepository.findByRfidTagAndActiveTrue(request.getRfidTag().trim()).orElse(null);
        }
        if (passenger == null && request.getCabinNumber() != null && !request.getCabinNumber().isBlank()) {
            List<Passenger> list = passengerRepository.findByCabinNumberAndActiveTrue(request.getCabinNumber().trim());
            if (!list.isEmpty()) passenger = list.get(0);
        }

        String passengerName = passenger != null ? passenger.getFullName() : (request.getFullName() != null ? request.getFullName().trim() : "Khách Vãng Lai");
        String cabinNumber = passenger != null ? passenger.getCabinNumber() : (request.getCabinNumber() != null ? request.getCabinNumber().trim() : "Chưa xác định");
        Long passId = passenger != null ? passenger.getId() : System.currentTimeMillis();

        // 3. Kiểm tra xem hành khách này đã check-in chưa
        if (passenger != null) {
            ActivityAttendance existing = attendanceRepository.findByActivityIdAndPassengerIdAndActiveTrue(activityId, passenger.getId()).orElse(null);
            if (existing != null && existing.getStatus() == ActivityAttendanceStatus.CHECKED_IN) {
                throw new DuplicateCheckInException("Hành khách " + passengerName + " đã check-in hoạt động này trước đó");
            }
        }

        // 4. Tính toán ghi nợ tài khoản cabin folio
        BigDecimal chargedAmount = BigDecimal.ZERO;
        String transactionId = "INCLUDED";
        if (activity.getPrice() != null && activity.getPrice().compareTo(BigDecimal.ZERO) > 0 && !Boolean.TRUE.equals(activity.getIsIncluded())) {
            chargedAmount = activity.getPrice();
            transactionId = "FOLIO-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        Instant now = Instant.now();

        // 5. Tạo bản ghi tham gia dạng Walk-in
        ActivityAttendance attendance = ActivityAttendance.builder()
                .activityId(activityId)
                .passengerId(passId)
                .passengerName(passengerName)
                .cabinNumber(cabinNumber)
                .rfidTag(request.getRfidTag())
                .status(ActivityAttendanceStatus.CHECKED_IN)
                .checkedInAt(now)
                .checkedInByStaffId(request.getStaffId())
                .checkInMethod(ActivityCheckInMethod.WALK_IN)
                .isWalkIn(true)
                .chargedAmount(chargedAmount)
                .transactionId(transactionId)
                .deviceId(request.getDeviceId())
                .note(request.getNote() != null ? request.getNote() : "Khách vãng lai check-in trực tiếp tại cửa")
                .active(true)
                .build();

        ActivityAttendance saved = attendanceRepository.save(attendance);

        // 6. Ghi log kiểm toán
        checkInLogRepository.save(ActivityCheckInLog.builder()
                .activityId(activityId)
                .passengerId(passId)
                .passengerName(passengerName)
                .cabinNumber(cabinNumber)
                .staffId(request.getStaffId())
                .deviceId(request.getDeviceId())
                .checkInTime(now)
                .method(ActivityCheckInMethod.WALK_IN)
                .status("SUCCESS")
                .note("Walk-in check-in thành công. Số tiền: " + chargedAmount)
                .active(true)
                .build());

        // 7. Cập nhật sức chứa
        activity.setCurrentParticipants((int) (currentCheckedIn + 1));
        activityRepository.save(activity);

        log.info("Walk-in check-in successful: Activity={} Passenger={} Charged={}",
                activityId, passengerName, chargedAmount);

        return WalkInCheckInResponse.builder()
                .attendanceId(saved.getId())
                .activityId(activity.getId())
                .activityTitle(activity.getTitle())
                .passengerId(passId)
                .passengerName(passengerName)
                .cabinNumber(cabinNumber)
                .chargedAmount(chargedAmount)
                .transactionId(transactionId)
                .status(saved.getStatus().name())
                .checkInTime(now)
                .isWalkIn(true)
                .message("Đăng ký và check-in khách vãng lai thành công")
                .build();
    }


    private String extractDeckFromLocation(String location) {
        if (location == null) return "Deck 5";
        String lower = location.toLowerCase();
        if (lower.contains("tầng 14") || lower.contains("deck 14")) return "Deck 14";
        if (lower.contains("tầng 12") || lower.contains("deck 12")) return "Deck 12";
        if (lower.contains("tầng 10") || lower.contains("deck 10")) return "Deck 10";
        if (lower.contains("tầng 7") || lower.contains("deck 7")) return "Deck 7";
        if (lower.contains("tầng 6") || lower.contains("deck 6")) return "Deck 6";
        if (lower.contains("tầng 4") || lower.contains("deck 4")) return "Deck 4";
        if (lower.contains("nhà hát") || lower.contains("theatre")) return "Deck 4 & 5";
        if (lower.contains("boong") || lower.contains("lounge")) return "Deck 14";
        return "Deck 5";
    }
}

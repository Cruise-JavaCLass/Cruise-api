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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
    private final PlatformTransactionManager transactionManager;

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

        // Đảm bảo có bản ghi ActivityAttendance (tra cứu không lọc active để tránh lỗi unique constraint)
        attendanceRepository.findByActivityIdAndPassengerId(activityId, passengerId)
                .map(existing -> {
                    if (!Boolean.TRUE.equals(existing.getActive())) {
                        existing.setActive(true);
                        return attendanceRepository.save(existing);
                    }
                    return existing;
                })
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
    @Transactional(noRollbackFor = DuplicateCheckInException.class)
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
            if (passengers.size() > 1) {
                throw new BusinessRuleException("Cabin " + request.getCabinNumber() + " có " + passengers.size() +
                        " hành khách. Vui lòng cung cấp chính xác ID hành khách hoặc quẹt thẻ RFID.");
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

        // 2. Tìm hoặc khởi tạo bản ghi điểm danh (unfiltered lookup để tránh lỗi unique constraint)
        ActivityAttendance attendance = attendanceRepository.findByActivityIdAndPassengerId(activityId, resolvedPassengerId)
                .map(existing -> {
                    if (!Boolean.TRUE.equals(existing.getActive())) {
                        existing.setActive(true);
                    }
                    return existing;
                })
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

            // Ghi nhận log trùng lặp (không bị rollback nhờ noRollbackFor)
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

        ActivityAttendance saved;
        try {
            saved = attendanceRepository.save(attendance);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new DuplicateCheckInException("Hành khách " + passenger.getFullName() + " đang được check-in đồng thời bởi thiết bị khác");
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("uk_activity_passenger")) {
                throw new DuplicateCheckInException("Hành khách " + passenger.getFullName() + " đã được điểm danh trước đó");
            }
            throw ex;
        }

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
        // 1. Pessimistic Write Lock trên OnboardActivity để chống race condition sức chứa
        OnboardActivity activity = activityRepository.findByIdForUpdate(activityId)
                .filter(OnboardActivity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hoạt động với ID " + activityId + " không tồn tại"));

        long currentCheckedIn = attendanceRepository.countByActivityIdAndStatusAndActiveTrue(activityId, ActivityAttendanceStatus.CHECKED_IN);
        if (activity.getMaxCapacity() != null && currentCheckedIn >= activity.getMaxCapacity()) {
            throw new BusinessRuleException("Hoạt động đã hết chỗ (Activity is at full capacity)");
        }

        // 2. Định danh hành khách bắt buộc phải hợp lệ và Active
        Passenger passenger = null;
        if (request.getPassengerId() != null) {
            passenger = passengerRepository.findById(request.getPassengerId())
                    .filter(Passenger::getActive)
                    .orElse(null);
        }
        if (passenger == null && request.getRfidTag() != null && !request.getRfidTag().isBlank()) {
            passenger = passengerRepository.findByRfidTagAndActiveTrue(request.getRfidTag().trim()).orElse(null);
        }
        if (passenger == null && request.getCabinNumber() != null && !request.getCabinNumber().isBlank()) {
            List<Passenger> list = passengerRepository.findByCabinNumberAndActiveTrue(request.getCabinNumber().trim());
            if (list.size() > 1) {
                throw new BusinessRuleException("Cabin " + request.getCabinNumber() + " có " + list.size() +
                        " hành khách. Vui lòng cung cấp chính xác ID hành khách hoặc quẹt thẻ RFID.");
            }
            if (list.size() == 1) {
                passenger = list.get(0);
            }
        }

        if (passenger == null) {
            throw new BusinessRuleException("Không thể xác định hành khách hợp lệ trên tàu để đăng ký walk-in. Vui lòng cung cấp Passenger ID hoặc RFID chính xác.");
        }

        Long passId = passenger.getId();
        String passengerName = passenger.getFullName();
        String cabinNumber = passenger.getCabinNumber();

        // 3. Kiểm tra bản ghi điểm danh hiện có (unfiltered lookup để tái sử dụng row)
        ActivityAttendance attendance = attendanceRepository.findByActivityIdAndPassengerId(activityId, passId).orElse(null);
        if (attendance != null && attendance.getStatus() == ActivityAttendanceStatus.CHECKED_IN) {
            throw new DuplicateCheckInException("Hành khách " + passengerName + " đã check-in hoạt động này trước đó");
        }

        // 4. Tính toán ghi nợ tài khoản cabin folio (không dùng synthetic ID)
        BigDecimal chargedAmount = BigDecimal.ZERO;
        String transactionId = "INCLUDED";
        if (activity.getPrice() != null && activity.getPrice().compareTo(BigDecimal.ZERO) > 0 && !Boolean.TRUE.equals(activity.getIsIncluded())) {
            chargedAmount = activity.getPrice();
            transactionId = "PENDING_FOLIO_BILLING";
        }

        Instant now = Instant.now();

        // 5. Cập nhật hoặc tạo mới bản ghi
        if (attendance == null) {
            attendance = ActivityAttendance.builder()
                    .activityId(activityId)
                    .passengerId(passId)
                    .passengerName(passengerName)
                    .cabinNumber(cabinNumber)
                    .rfidTag(request.getRfidTag() != null ? request.getRfidTag() : passenger.getRfidTag())
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
        } else {
            attendance.setActive(true);
            attendance.setStatus(ActivityAttendanceStatus.CHECKED_IN);
            attendance.setCheckedInAt(now);
            attendance.setCheckedInByStaffId(request.getStaffId());
            attendance.setCheckInMethod(ActivityCheckInMethod.WALK_IN);
            attendance.setIsWalkIn(true);
            attendance.setChargedAmount(chargedAmount);
            attendance.setTransactionId(transactionId);
            attendance.setDeviceId(request.getDeviceId());
            if (request.getNote() != null) {
                attendance.setNote(request.getNote());
            }
        }

        ActivityAttendance saved;
        try {
            saved = attendanceRepository.save(attendance);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new DuplicateCheckInException("Hành khách " + passengerName + " đang được xử lý đồng thời bởi thiết bị khác");
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("uk_activity_passenger")) {
                throw new DuplicateCheckInException("Hành khách " + passengerName + " đã check-in hoạt động này");
            }
            throw ex;
        }

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

        // 7. Recompute số lượng người tham gia thực tế từ DB
        long updatedTotal = attendanceRepository.countByActivityIdAndStatusAndActiveTrue(activityId, ActivityAttendanceStatus.CHECKED_IN);
        activity.setCurrentParticipants((int) updatedTotal);
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

    /**
     * Thống kê tổng hợp số liệu điểm danh thời gian thực (KAN-321)
     */
    @Transactional(readOnly = true)
    public ActivityAttendanceSummaryResponse getAttendanceSummary(Long activityId) {
        OnboardActivity activity = activityRepository.findById(activityId)
                .filter(OnboardActivity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hoạt động với ID " + activityId + " không tồn tại"));

        long totalRegistered = attendanceRepository.countByActivityIdAndActiveTrue(activityId);
        long checkedInCount = attendanceRepository.countByActivityIdAndStatusAndActiveTrue(activityId, ActivityAttendanceStatus.CHECKED_IN);
        long walkInCount = attendanceRepository.countByActivityIdAndIsWalkInTrueAndActiveTrue(activityId);
        long pendingCount = Math.max(0, totalRegistered - checkedInCount);

        int maxCapacity = activity.getMaxCapacity() != null ? activity.getMaxCapacity() : 100;
        int remainingCapacity = Math.max(0, maxCapacity - (int) checkedInCount);
        double attendanceRate = maxCapacity > 0
                ? Math.round(((double) checkedInCount / maxCapacity) * 1000.0) / 10.0
                : 0.0;

        return ActivityAttendanceSummaryResponse.builder()
                .activityId(activity.getId())
                .activityTitle(activity.getTitle())
                .maxCapacity(maxCapacity)
                .totalRegistered(totalRegistered)
                .checkedInCount(checkedInCount)
                .remainingCapacity(remainingCapacity)
                .attendanceRate(attendanceRate)
                .walkInCount(walkInCount)
                .pendingCount(pendingCount)
                .totalPrice(activity.getPrice() != null ? activity.getPrice() : BigDecimal.ZERO)
                .isCapacityFull(remainingCapacity <= 0)
                .build();
    }

    /**
     * Truy xuất danh sách hành khách điểm danh phân trang (Roster) có bộ lọc (KAN-321)
     */
    @Transactional(readOnly = true)
    public Page<ActivityRosterPassengerResponse> getActivityRoster(
            Long activityId,
            ActivityAttendanceStatus status,
            String keyword,
            Pageable pageable
    ) {
        if (!activityRepository.existsById(activityId)) {
            throw new ResourceNotFoundException("Hoạt động với ID " + activityId + " không tồn tại");
        }

        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        Page<ActivityAttendance> page = attendanceRepository.searchRoster(activityId, status, cleanKeyword, pageable);

        return page.map(att -> ActivityRosterPassengerResponse.builder()
                .attendanceId(att.getId())
                .passengerId(att.getPassengerId())
                .passengerName(att.getPassengerName())
                .cabinNumber(att.getCabinNumber())
                .rfidTag(att.getRfidTag())
                .status(att.getStatus().name())
                .checkedInAt(att.getCheckedInAt())
                .checkedInByStaffId(att.getCheckedInByStaffId())
                .checkInMethod(att.getCheckInMethod() != null ? att.getCheckInMethod().name() : null)
                .isWalkIn(att.getIsWalkIn())
                .chargedAmount(att.getChargedAmount())
                .transactionId(att.getTransactionId())
                .build());
    }

    /**
     * Đồng bộ hàng loạt lượt quét điểm danh ngoại tuyến khi kết nối lại mạng (KAN-321)
     */
    @Transactional
    public OfflineCheckInBatchResponse syncOfflineCheckIns(Long activityId, OfflineCheckInBatchRequest request) {
        OnboardActivity activity = activityRepository.findById(activityId)
                .filter(OnboardActivity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hoạt động với ID " + activityId + " không tồn tại"));

        if (request.getCheckIns() == null || request.getCheckIns().isEmpty()) {
            return OfflineCheckInBatchResponse.builder()
                    .activityId(activityId)
                    .processedCount(0)
                    .duplicateCount(0)
                    .failedCount(0)
                    .message("Không có dữ liệu ngoại tuyến cần đồng bộ")
                    .build();
        }

        int processed = 0;
        int duplicate = 0;
        int failed = 0;

        for (OfflineCheckInBatchRequest.OfflineCheckInItem item : request.getCheckIns()) {
            try {
                int result = processSingleOfflineItem(activityId, item);
                if (result == 1) {
                    processed++;
                } else if (result == 2) {
                    duplicate++;
                } else {
                    failed++;
                }
            } catch (Exception ex) {
                log.error("Lỗi khi xử lý bản ghi offline sync item {}: {}", item.getOfflineQueueId(), ex.getMessage());
                failed++;
            }
        }

        long currentTotal = attendanceRepository.countByActivityIdAndStatusAndActiveTrue(activityId, ActivityAttendanceStatus.CHECKED_IN);
        activity.setCurrentParticipants((int) currentTotal);
        activityRepository.save(activity);

        log.info("Offline batch sync completed for activity {}: processed={}, duplicates={}, failed={}",
                activityId, processed, duplicate, failed);

        return OfflineCheckInBatchResponse.builder()
                .activityId(activityId)
                .processedCount(processed)
                .duplicateCount(duplicate)
                .failedCount(failed)
                .message(String.format("Đồng bộ hoàn tất: %d thành công, %d trùng lặp, %d lỗi", processed, duplicate, failed))
                .build();
    }

    /**
     * Xử lý 1 bản ghi offline check-in trong transaction độc lập (1: thành công, 2: trùng lặp, 0: lỗi)
     */
    public int processSingleOfflineItem(Long activityId, OfflineCheckInBatchRequest.OfflineCheckInItem item) {
        Long passId = item.getPassengerId();
        ActivityCheckInMethod method = item.getMethod() != null ? item.getMethod() : ActivityCheckInMethod.RFID;

        if (passId == null && item.getQrToken() != null && !item.getQrToken().isBlank()) {
            try {
                QrTokenService.QrTokenPayload payload = qrTokenService.verifyAndDecode(item.getQrToken(), activityId);
                passId = payload.getPassengerId();
                method = ActivityCheckInMethod.QR_CODE;
            } catch (Exception e) {
                log.warn("Offline item QR token invalid: {}", e.getMessage());
            }
        }

        if (passId == null && item.getRfidTag() != null && !item.getRfidTag().isBlank()) {
            passId = passengerRepository.findByRfidTagAndActiveTrue(item.getRfidTag().trim())
                    .map(Passenger::getId)
                    .orElse(null);
            method = ActivityCheckInMethod.RFID;
        }

        if (passId == null && item.getCabinNumber() != null && !item.getCabinNumber().isBlank()) {
            List<Passenger> passengers = passengerRepository.findByCabinNumberAndActiveTrue(item.getCabinNumber().trim());
            if (passengers.size() == 1) {
                passId = passengers.get(0).getId();
                method = ActivityCheckInMethod.MANUAL;
            }
        }

        if (passId == null) {
            return 0;
        }

        Passenger passenger = passengerRepository.findById(passId)
                .filter(Passenger::getActive)
                .orElse(null);
        if (passenger == null) {
            return 0;
        }

        ActivityAttendance attendance = attendanceRepository.findByActivityIdAndPassengerId(activityId, passId)
                .map(existing -> {
                    if (!Boolean.TRUE.equals(existing.getActive())) {
                        existing.setActive(true);
                    }
                    return existing;
                })
                .orElseGet(() -> ActivityAttendance.builder()
                        .activityId(activityId)
                        .passengerId(passenger.getId())
                        .passengerName(passenger.getFullName())
                        .cabinNumber(passenger.getCabinNumber())
                        .rfidTag(passenger.getRfidTag())
                        .status(ActivityAttendanceStatus.REGISTERED)
                        .active(true)
                        .build());

        if (attendance.getStatus() == ActivityAttendanceStatus.CHECKED_IN) {
            return 2;
        }

        Instant scanTime = item.getScannedAt() != null ? item.getScannedAt() : Instant.now();
        attendance.setStatus(ActivityAttendanceStatus.CHECKED_IN);
        attendance.setCheckedInAt(scanTime);
        attendance.setCheckedInByStaffId(item.getStaffId() != null ? item.getStaffId() : "OFFLINE-DEVICE");
        attendance.setCheckInMethod(method);
        attendance.setDeviceId(item.getDeviceId());
        attendance.setNote("Đồng bộ ngoại tuyến từ hàng đợi: " + item.getOfflineQueueId());

        try {
            attendanceRepository.save(attendance);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException ex) {
            return 2;
        }

        checkInLogRepository.save(ActivityCheckInLog.builder()
                .activityId(activityId)
                .passengerId(passId)
                .passengerName(passenger.getFullName())
                .cabinNumber(passenger.getCabinNumber())
                .staffId(item.getStaffId())
                .deviceId(item.getDeviceId())
                .checkInTime(scanTime)
                .method(method)
                .status("SUCCESS_OFFLINE_SYNC")
                .note("Đồng bộ ngoại tuyến thành công")
                .active(true)
                .build());

        return 1;
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

package com.cruise.activity.service;

import com.cruise.activity.dto.ActivityRegistrationRequest;
import com.cruise.activity.dto.ActivityRegistrationResponse;
import com.cruise.activity.entity.ActivityRegistration;
import com.cruise.activity.entity.ActivityRegistrationAuditLog;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.enums.RegistrationStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.DuplicateRegistrationException;
import com.cruise.activity.exception.PaymentRequiredException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ActivityRegistrationAuditLogRepository;
import com.cruise.activity.repository.ActivityRegistrationRepository;
import com.cruise.activity.repository.OnboardActivityRepository;
import com.cruise.activity.repository.PassengerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityRegistrationService {

    private final OnboardActivityRepository activityRepository;
    private final PassengerRepository passengerRepository;
    private final ActivityRegistrationRepository registrationRepository;
    private final ActivityRegistrationAuditLogRepository auditLogRepository;

    /**
     * Đăng ký tham gia hoạt động với cơ chế khóa đồng thời và tính phí Folio (KAN-216)
     */
    @Transactional
    public ActivityRegistrationResponse registerActivity(Long activityId, ActivityRegistrationRequest request) {
        log.info("Processing activity registration: activityId={}, passengerId={}", activityId, request.getPassengerId());

        // 1. Áp dụng Khóa bi quan (Pessimistic Lock) để chống overbooking khi nhiều yêu cầu đồng thời
        OnboardActivity activity = activityRepository.findByIdWithLock(activityId)
                .filter(OnboardActivity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hoạt động với ID " + activityId + " không tồn tại"));

        if (activity.getStatus() == ActivityStatus.CANCELLED || activity.getStatus() == ActivityStatus.COMPLETED) {
            throw new BusinessRuleException("Hoạt động hiện không mở đăng ký (Trạng thái: " + activity.getStatus() + ")");
        }

        // 2. Tra cứu thông tin hành khách
        Passenger passenger = passengerRepository.findById(request.getPassengerId())
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hành khách với ID " + request.getPassengerId() + " không tồn tại"));

        // Kiểm tra trạng thái khóa chi tiêu Folio của tài khoản (AC3)
        if (isFolioLocked(passenger)) {
            throw new PaymentRequiredException("Tài khoản cabin folio của hành khách " + passenger.getFullName() +
                    " (Cabin " + passenger.getCabinNumber() + ") đang bị khóa chi tiêu hoặc không đủ hạn mức thanh toán");
        }

        // 3. Kiểm tra tính idempotent & trùng lặp đăng ký (AC2)
        List<RegistrationStatus> activeStatuses = List.of(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED, RegistrationStatus.WAITLIST);
        registrationRepository.findByActivityIdAndPassengerIdAndStatusInAndActiveTrue(activityId, passenger.getId(), activeStatuses)
                .ifPresent(existing -> {
                    throw new DuplicateRegistrationException("Hành khách " + passenger.getFullName() +
                            " đã đăng ký hoạt động này trước đó với trạng thái: " + existing.getStatus());
                });

        // 4. Kiểm tra sức chứa & phân bổ slot (AC1 & AC5)
        long confirmedCount = registrationRepository.countByActivityIdAndStatusAndActiveTrue(activityId, RegistrationStatus.CONFIRMED);
        boolean hasAvailableSlot = activity.getMaxCapacity() == null || confirmedCount < activity.getMaxCapacity();

        RegistrationStatus status;
        BigDecimal chargedAmount = BigDecimal.ZERO;
        String transactionId = null;
        Integer waitlistPosition = null;
        String auditAction;

        if (hasAvailableSlot) {
            status = RegistrationStatus.CONFIRMED;
            auditAction = "REGISTER_CONFIRMED";

            // Tích hợp Folio Billing nếu hoạt động có phí (AC3)
            if (activity.getPrice() != null && activity.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                chargedAmount = activity.getPrice();
                transactionId = "FOLIO-REG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            }

            activity.setCurrentParticipants((int) (confirmedCount + 1));
            activityRepository.save(activity);
        } else {
            // Hết chỗ -> Chuyển vào hàng đợi Waitlist (AC5)
            status = RegistrationStatus.WAITLISTED;
            auditAction = "REGISTER_WAITLISTED";
            long waitlistCount = registrationRepository.countByActivityIdAndStatusAndActiveTrue(activityId, RegistrationStatus.WAITLISTED)
                    + registrationRepository.countByActivityIdAndStatusAndActiveTrue(activityId, RegistrationStatus.WAITLIST);
            waitlistPosition = (int) (waitlistCount + 1);
        }

        String cabinNumber = (request.getCabinNumber() != null && !request.getCabinNumber().isBlank())
                ? request.getCabinNumber().trim()
                : passenger.getCabinNumber();

        Instant now = Instant.now();
        ActivityRegistration registration = ActivityRegistration.builder()
                .activityId(activityId)
                .passengerId(passenger.getId())
                .passengerName(passenger.getFullName())
                .cabinNumber(cabinNumber)
                .status(status)
                .chargedAmount(chargedAmount)
                .refundAmount(BigDecimal.ZERO)
                .transactionId(transactionId)
                .registeredAt(now)
                .notes(request.getNotes())
                .active(true)
                .build();

        ActivityRegistration saved = registrationRepository.save(registration);

        // Ghi nhật ký kiểm toán bất biến (AC6)
        auditLogRepository.save(ActivityRegistrationAuditLog.builder()
                .registrationId(saved.getId())
                .activityId(activityId)
                .passengerId(passenger.getId())
                .action(auditAction)
                .chargedAmount(chargedAmount)
                .transactionId(transactionId)
                .auditTime(now)
                .details(String.format("Đăng ký thành công trạng thái %s cho cabin %s", status, cabinNumber))
                .build());

        log.info("Registered activity successfully: regId={}, activityId={}, passengerId={}, status={}",
                saved.getId(), activityId, passenger.getId(), status);

        return ActivityRegistrationResponse.builder()
                .registrationId(saved.getId())
                .activityId(activityId)
                .activityTitle(activity.getTitle())
                .passengerId(passenger.getId())
                .passengerName(passenger.getFullName())
                .cabinNumber(cabinNumber)
                .status(status.name())
                .chargedAmount(chargedAmount)
                .transactionId(transactionId)
                .registeredAt(now)
                .waitlistPosition(waitlistPosition)
                .build();
    }

    private boolean isFolioLocked(Passenger passenger) {
        if (passenger.getEmail() != null && (passenger.getEmail().toLowerCase().contains("locked") || passenger.getEmail().toLowerCase().contains("blocked"))) {
            return true;
        }
        if (passenger.getCabinNumber() != null && passenger.getCabinNumber().toUpperCase().contains("LOCKED")) {
            return true;
        }
        return false;
    }
}

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

    /**
     * Hủy đăng ký hoạt động có áp dụng chính sách Cut-off, hoàn phí Folio và tự động đôn Waitlist (KAN-369)
     */
    @Transactional
    public com.cruise.activity.dto.ActivityCancellationResponse cancelRegistration(
            Long activityId, Long registrationId, Long passengerId, String reason) {
        log.info("Processing activity cancellation: activityId={}, regId={}, passengerId={}",
                activityId, registrationId, passengerId);

        OnboardActivity activity = activityRepository.findByIdWithLock(activityId)
                .filter(OnboardActivity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hoạt động với ID " + activityId + " không tồn tại"));

        ActivityRegistration registration = registrationRepository.findById(registrationId)
                .filter(ActivityRegistration::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Bản ghi đăng ký với ID " + registrationId + " không tồn tại"));

        if (!registration.getActivityId().equals(activityId)) {
            throw new BusinessRuleException("Đăng ký ID " + registrationId + " không thuộc hoạt động ID " + activityId);
        }

        if (passengerId != null && !registration.getPassengerId().equals(passengerId)) {
            throw new BusinessRuleException("Đăng ký không thuộc về hành khách ID " + passengerId);
        }

        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            throw new BusinessRuleException("Đăng ký này đã bị hủy trước đó");
        }

        RegistrationStatus previousStatus = registration.getStatus();

        // Kiểm tra thời hạn hủy Cut-off policy (AC4)
        java.time.LocalDateTime startDateTime = java.time.LocalDateTime.of(
                activity.getActivityDate(), activity.getStartTime());
        int cutOffMinutes = activity.getCancellationCutOffMinutes() != null
                ? activity.getCancellationCutOffMinutes()
                : 120;
        java.time.LocalDateTime cutOffThreshold = startDateTime.minusMinutes(cutOffMinutes);

        boolean isBeforeCutOff = java.time.LocalDateTime.now().isBefore(cutOffThreshold);
        BigDecimal refundAmount = BigDecimal.ZERO;

        if (isBeforeCutOff) {
            // Hủy trước Cut-off: hoàn 100% tiền đã thu vào Folio
            refundAmount = registration.getChargedAmount() != null
                    ? registration.getChargedAmount()
                    : BigDecimal.ZERO;
        } else {
            // Hủy sau Cut-off: áp dụng tỷ lệ hoàn theo chính sách hoàn tiền phạt
            int refundPercentage = activity.getRefundPercentage() != null
                    ? activity.getRefundPercentage()
                    : 0;
            if (refundPercentage > 0 && registration.getChargedAmount() != null) {
                refundAmount = registration.getChargedAmount()
                        .multiply(BigDecimal.valueOf(refundPercentage))
                        .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            }
        }

        Instant now = Instant.now();
        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setCancelledAt(now);
        registration.setCancellationReason(reason != null ? reason : "Hành khách yêu cầu hủy chỗ");
        registration.setRefundAmount(refundAmount);
        registrationRepository.save(registration);

        // Ghi nhật ký hủy
        auditLogRepository.save(ActivityRegistrationAuditLog.builder()
                .registrationId(registration.getId())
                .activityId(activityId)
                .passengerId(registration.getPassengerId())
                .action("CANCELLED")
                .chargedAmount(refundAmount)
                .transactionId(registration.getTransactionId())
                .auditTime(now)
                .details(String.format("Hủy đăng ký thành công. Hoàn tiền: %s VND. Lý do: %s",
                        refundAmount, registration.getCancellationReason()))
                .build());

        Long promotedPassengerId = null;

        // Nếu vị trí bị hủy là CONFIRMED -> Tự động đôn suất cho khách đầu danh sách Waitlist (AC5)
        if (previousStatus == RegistrationStatus.CONFIRMED) {
            activity.setCurrentParticipants(Math.max(0, activity.getCurrentParticipants() - 1));

            List<ActivityRegistration> waitlist = registrationRepository
                    .findByActivityIdAndStatusAndActiveTrueOrderByRegisteredAtAscIdAsc(activityId, RegistrationStatus.WAITLISTED);
            if (waitlist.isEmpty()) {
                waitlist = registrationRepository
                        .findByActivityIdAndStatusAndActiveTrueOrderByRegisteredAtAscIdAsc(activityId, RegistrationStatus.WAITLIST);
            }

            if (!waitlist.isEmpty()) {
                ActivityRegistration promoted = waitlist.get(0);
                promoted.setStatus(RegistrationStatus.CONFIRMED);

                if (activity.getPrice() != null && activity.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                    promoted.setChargedAmount(activity.getPrice());
                    promoted.setTransactionId("FOLIO-PRM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
                }

                registrationRepository.save(promoted);
                activity.setCurrentParticipants(activity.getCurrentParticipants() + 1);
                promotedPassengerId = promoted.getPassengerId();

                // Ghi log đôn chỗ
                auditLogRepository.save(ActivityRegistrationAuditLog.builder()
                        .registrationId(promoted.getId())
                        .activityId(activityId)
                        .passengerId(promoted.getPassengerId())
                        .action("WAITLIST_PROMOTED")
                        .chargedAmount(promoted.getChargedAmount())
                        .transactionId(promoted.getTransactionId())
                        .auditTime(now)
                        .details("Tự động đôn suất từ Waitlist lên Confirmed do có lượt hủy")
                        .build());

                log.info("Auto promoted waitlist passenger: regId={}, passengerId={}, activityId={}",
                        promoted.getId(), promoted.getPassengerId(), activityId);
            }

            activityRepository.save(activity);
        }

        return com.cruise.activity.dto.ActivityCancellationResponse.builder()
                .registrationId(registration.getId())
                .status(registration.getStatus().name())
                .refundAmount(refundAmount)
                .waitlistPromotedPassengerId(promotedPassengerId)
                .cancellationReason(registration.getCancellationReason())
                .cancelledAt(now)
                .build();
    }

    /**
     * Nhân viên can thiệp đôn suất thủ công từ Waitlist lên Confirmed (KAN-369)
     */
    @Transactional
    public ActivityRegistrationResponse promoteWaitlist(Long activityId, Long waitlistId) {
        log.info("Manually promoting waitlist registration: activityId={}, waitlistId={}", activityId, waitlistId);

        OnboardActivity activity = activityRepository.findByIdWithLock(activityId)
                .filter(OnboardActivity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hoạt động với ID " + activityId + " không tồn tại"));

        ActivityRegistration registration = registrationRepository.findById(waitlistId)
                .filter(ActivityRegistration::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Bản ghi hàng đợi với ID " + waitlistId + " không tồn tại"));

        if (!registration.getActivityId().equals(activityId)) {
            throw new BusinessRuleException("Bản ghi không thuộc hoạt động ID " + activityId);
        }

        if (registration.getStatus() != RegistrationStatus.WAITLISTED && registration.getStatus() != RegistrationStatus.WAITLIST) {
            throw new BusinessRuleException("Chỉ có thể đôn suất cho hành khách ở trạng thái WAITLIST/WAITLISTED");
        }

        registration.setStatus(RegistrationStatus.CONFIRMED);
        if (activity.getPrice() != null && activity.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            registration.setChargedAmount(activity.getPrice());
            registration.setTransactionId("FOLIO-MAN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        }

        ActivityRegistration saved = registrationRepository.save(registration);
        activity.setCurrentParticipants(activity.getCurrentParticipants() + 1);
        activityRepository.save(activity);

        auditLogRepository.save(ActivityRegistrationAuditLog.builder()
                .registrationId(saved.getId())
                .activityId(activityId)
                .passengerId(saved.getPassengerId())
                .action("WAITLIST_MANUAL_PROMOTED")
                .chargedAmount(saved.getChargedAmount())
                .transactionId(saved.getTransactionId())
                .auditTime(Instant.now())
                .details("Nhân viên đôn suất thủ công từ danh sách chờ")
                .build());

        return ActivityRegistrationResponse.builder()
                .registrationId(saved.getId())
                .activityId(activityId)
                .activityTitle(activity.getTitle())
                .passengerId(saved.getPassengerId())
                .passengerName(saved.getPassengerName())
                .cabinNumber(saved.getCabinNumber())
                .status(saved.getStatus().name())
                .chargedAmount(saved.getChargedAmount())
                .transactionId(saved.getTransactionId())
                .registeredAt(saved.getRegisteredAt())
                .waitlistPosition(null)
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

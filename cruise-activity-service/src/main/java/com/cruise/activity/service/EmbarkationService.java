package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.EmbarkationCheckInLog;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.enums.CheckInStatus;
import com.cruise.activity.exception.DuplicateCheckInException;
import com.cruise.activity.exception.DuplicateRegistrationException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.EmbarkationCheckInLogRepository;
import com.cruise.activity.repository.PassengerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class EmbarkationService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private final PassengerRepository passengerRepository;
    private final EmbarkationCheckInLogRepository embarkationCheckInLogRepository;

    @Transactional(readOnly = true)
    public PassengerEmbarkationProfileDto lookupPassenger(String keyword) {
        if (keyword == null || keyword.trim().isBlank()) {
            throw new IllegalArgumentException("Từ khóa tìm kiếm không được để trống");
        }
        String cleanKeyword = keyword.trim();
        List<Passenger> results = passengerRepository.searchForEmbarkationLookup(cleanKeyword);
        if (results.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin hành khách với từ khóa: " + cleanKeyword);
        }
        return toProfileDto(results.get(0));
    }

    public EmbarkationCheckInResponse processCheckIn(Long passengerId, EmbarkationCheckInRequest request) {
        Passenger passenger = passengerRepository.findById(passengerId)
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hành khách với ID: " + passengerId));

        if (passenger.getCheckInStatus() == CheckInStatus.BOARDED) {
            String boardedTime = passenger.getBoardedAt() != null
                    ? TIME_FORMATTER.format(passenger.getBoardedAt())
                    : "không xác định";
            throw new DuplicateCheckInException("Hành khách " + passenger.getFullName() +
                    " đã làm thủ tục lên tàu lúc " + boardedTime);
        }

        Instant now = Instant.now();
        passenger.setCheckInStatus(CheckInStatus.BOARDED);
        passenger.setBoardedAt(now);
        passenger.setFolioStatus("ACTIVE");

        if (request != null && request.getRfidTag() != null && !request.getRfidTag().trim().isBlank()) {
            String tag = request.getRfidTag().trim();
            // Check if RFID tag is already assigned to another passenger
            Optional<Passenger> existing = passengerRepository.findByRfidTagAndActiveTrue(tag);
            if (existing.isPresent() && !existing.get().getId().equals(passenger.getId())) {
                throw new DuplicateRegistrationException("Mã thẻ RFID " + tag + " đã được gán cho hành khách " + existing.get().getFullName());
            }
            passenger.setRfidTag(tag);
        }

        Passenger saved = passengerRepository.save(passenger);

        // Record audit trail log (AC6)
        EmbarkationCheckInLog logEntry = EmbarkationCheckInLog.builder()
                .passengerId(saved.getId())
                .tourId(saved.getTourId())
                .staffId(request != null ? request.getStaffId() : null)
                .terminalDeviceId(request != null ? request.getTerminalDeviceId() : null)
                .rfidTag(saved.getRfidTag())
                .gateLane(request != null ? request.getGateLane() : null)
                .boardedAt(now)
                .verificationMethod(request != null ? request.getVerificationMethod() : "PASSPORT_QR")
                .build();
        embarkationCheckInLogRepository.save(logEntry);

        log.info("Passenger embarked successfully: id={}, name={}, cabin={}, rfid={}",
                saved.getId(), saved.getFullName(), saved.getCabinNumber(), saved.getRfidTag());

        return EmbarkationCheckInResponse.builder()
                .passengerId(saved.getId())
                .checkInStatus(saved.getCheckInStatus())
                .boardedAt(saved.getBoardedAt())
                .rfidTag(saved.getRfidTag())
                .cabinNumber(saved.getCabinNumber())
                .folioStatus(saved.getFolioStatus())
                .message("Làm thủ tục lên tàu thành công")
                .build();
    }

    public BindRfidResponse bindRfid(Long passengerId, BindRfidRequest request) {
        Passenger passenger = passengerRepository.findById(passengerId)
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hành khách với ID: " + passengerId));

        String rfidTag = request.getRfidTag().trim();

        // AC2: Atomic RFID tag binding. Reject 409 Conflict if RFID is already bound to another passenger.
        Optional<Passenger> existing = passengerRepository.findByRfidTagAndActiveTrue(rfidTag);
        if (existing.isPresent() && !existing.get().getId().equals(passenger.getId())) {
            throw new DuplicateRegistrationException("Mã thẻ RFID " + rfidTag + " đã được gán cho hành khách khác: " + existing.get().getFullName());
        }

        passenger.setRfidTag(rfidTag);
        if (request.getQrCode() != null && !request.getQrCode().trim().isBlank()) {
            passenger.setQrCode(request.getQrCode().trim());
        }

        // AC3: Automatic Cashless Folio Activation
        if (Boolean.TRUE.equals(request.getActivateFolio()) || "INACTIVE".equals(passenger.getFolioStatus())) {
            passenger.setFolioStatus("ACTIVE");
        }

        Passenger saved = passengerRepository.save(passenger);

        log.info("Bound RFID tag to passenger: id={}, tag={}, folioStatus={}",
                saved.getId(), rfidTag, saved.getFolioStatus());

        return BindRfidResponse.builder()
                .passengerId(saved.getId())
                .rfidTag(saved.getRfidTag())
                .qrCode(saved.getQrCode())
                .cabinNumber(saved.getCabinNumber())
                .folioStatus(saved.getFolioStatus())
                .message("Gán thẻ RFID và kích hoạt tài khoản chi tiêu Folio thành công")
                .build();
    }

    public PassengerEmbarkationProfileDto toProfileDto(Passenger p) {
        return PassengerEmbarkationProfileDto.builder()
                .id(p.getId())
                .tourId(p.getTourId())
                .bookingCode(p.getBookingCode())
                .fullName(p.getFullName())
                .passportNumber(p.getPassportNumber())
                .nationality(p.getNationality())
                .emergencyContact(p.getEmergencyContact())
                .email(p.getEmail())
                .phoneNumber(p.getPhoneNumber())
                .cabinNumber(p.getCabinNumber())
                .cabinCategory(p.getCabinCategory() != null ? p.getCabinCategory() : "Standard Stateroom")
                .isVip(p.getIsVip())
                .healthDeclarationCompleted(p.getHealthDeclarationCompleted())
                .rfidTag(p.getRfidTag())
                .qrCode(p.getQrCode())
                .tourPackage(p.getTourPackage())
                .bleZone(p.getBleZone())
                .folioStatus(p.getFolioStatus())
                .checkInStatus(p.getCheckInStatus())
                .boardedAt(p.getBoardedAt())
                .build();
    }
}

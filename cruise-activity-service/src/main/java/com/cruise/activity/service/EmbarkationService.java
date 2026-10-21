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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

        // 1. Prioritize exact match on identifiers (passportNumber, bookingCode, qrCode, rfidTag)
        List<Passenger> exactMatches = passengerRepository.findExactIdentifierForEmbarkation(cleanKeyword);
        if (exactMatches.size() > 1) {
            throw new DuplicateRegistrationException("Tìm thấy nhiều hơn một hành khách phù hợp với định danh: " + cleanKeyword);
        }
        if (exactMatches.size() == 1) {
            return toProfileDto(exactMatches.get(0));
        }

        // 2. Fallback to partial fullName matching with escaped LIKE wildcards
        String escapedKeyword = escapeLikeWildcards(cleanKeyword);
        List<Passenger> nameMatches = passengerRepository.findByFullNameLikeIgnoreCaseForEmbarkation(escapedKeyword);
        if (nameMatches.size() > 1) {
            throw new DuplicateRegistrationException("Tìm thấy nhiều hơn một hành khách phù hợp với họ tên: " + cleanKeyword);
        }
        if (nameMatches.size() == 1) {
            return toProfileDto(nameMatches.get(0));
        }

        throw new ResourceNotFoundException("Không tìm thấy thông tin hành khách với từ khóa: " + cleanKeyword);
    }

    public EmbarkationCheckInResponse processCheckIn(Long passengerId, EmbarkationCheckInRequest request) {
        Passenger passenger = passengerRepository.findByIdForUpdate(passengerId)
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
            // Check if RFID tag is already assigned to another passenger (active or inactive)
            Optional<Passenger> existing = passengerRepository.findByRfidTag(tag);
            if (existing.isPresent() && !existing.get().getId().equals(passenger.getId())) {
                throw new DuplicateRegistrationException("Mã thẻ RFID " + tag + " đã được gán cho hành khách " + existing.get().getFullName());
            }
            passenger.setRfidTag(tag);
        }

        Passenger saved = savePassengerAndFlush(passenger, passenger.getRfidTag());

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
        Passenger passenger = passengerRepository.findByIdForUpdate(passengerId)
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hành khách với ID: " + passengerId));

        String rfidTag = request.getRfidTag().trim();

        // AC2: Atomic RFID tag binding. Reject 409 Conflict if RFID is already bound to another passenger.
        Optional<Passenger> existing = passengerRepository.findByRfidTag(rfidTag);
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

        Passenger saved = savePassengerAndFlush(passenger, rfidTag);

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

    private Passenger savePassengerAndFlush(Passenger passenger, String rfidTag) {
        try {
            return passengerRepository.saveAndFlush(passenger);
        } catch (DataIntegrityViolationException ex) {
            if (isRfidUniqueViolation(ex)) {
                throw new DuplicateRegistrationException("Mã thẻ RFID " + (rfidTag != null ? rfidTag : "") + " đã được gán cho hành khách khác");
            }
            throw ex;
        }
    }

    private boolean isRfidUniqueViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if (name != null && name.toLowerCase(Locale.ROOT).contains("rfid")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        Throwable rootCause = ex.getMostSpecificCause();
        String message = rootCause != null && rootCause.getMessage() != null
                ? rootCause.getMessage().toLowerCase(Locale.ROOT)
                : "";
        String exMessage = ex.getMessage() != null ? ex.getMessage().toLowerCase(Locale.ROOT) : "";
        String combined = message + " " + exMessage;
        return combined.contains("rfid") && (combined.contains("unique") || combined.contains("duplicate") || combined.contains("trùng"));
    }

    private String escapeLikeWildcards(String term) {
        if (term == null) {
            return "";
        }
        return term.replace("\\", "\\\\")
                   .replace("%", "\\%")
                   .replace("_", "\\_");
    }

    @Transactional(readOnly = true)
    public EmbarkationSummaryDto getEmbarkationSummary(Long tourId) {
        List<Passenger> passengers = passengerRepository.findByTourIdAndActiveTrue(tourId);

        int totalExpected = passengers.size();
        long boardedCount = passengers.stream()
                .filter(p -> p.getCheckInStatus() == CheckInStatus.BOARDED)
                .count();
        long remainingCount = Math.max(0, totalExpected - boardedCount);

        double progressPercent = totalExpected > 0
                ? Math.round(((double) boardedCount / totalExpected) * 1000.0) / 10.0
                : 0.0;

        long vipTotal = passengers.stream().filter(p -> Boolean.TRUE.equals(p.getIsVip())).count();
        long vipBoarded = passengers.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsVip()) && p.getCheckInStatus() == CheckInStatus.BOARDED)
                .count();

        Map<String, Long> categoryBreakdown = new LinkedHashMap<>();
        for (Passenger p : passengers) {
            String category = p.getCabinCategory() != null ? p.getCabinCategory() : "Standard Stateroom";
            categoryBreakdown.put(category, categoryBreakdown.getOrDefault(category, 0L) + 1);
        }

        return EmbarkationSummaryDto.builder()
                .tourId(tourId)
                .expectedTotal(totalExpected)
                .boardedCount(boardedCount)
                .remainingCount(remainingCount)
                .boardingProgressPercent(progressPercent)
                .vipTotal(vipTotal)
                .vipBoarded(vipBoarded)
                .categoryBreakdown(categoryBreakdown)
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] exportBorderControlManifestCsv(Long tourId) {
        List<Passenger> passengers = passengerRepository.findByTourIdAndActiveTrue(tourId);

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM for Microsoft Excel compatibility
        sb.append("\ufeff");
        sb.append("ID,BookingCode,FullName,PassportNumber,Nationality,CabinNumber,CabinCategory,VIP,HealthDeclaration,RFIDTag,CheckInStatus,BoardedAt,FolioStatus\n");

        for (Passenger p : passengers) {
            sb.append(escapeCsv(String.valueOf(p.getId()))).append(",");
            sb.append(escapeCsv(p.getBookingCode())).append(",");
            sb.append(escapeCsv(p.getFullName())).append(",");
            sb.append(escapeCsv(p.getPassportNumber())).append(",");
            sb.append(escapeCsv(p.getNationality())).append(",");
            sb.append(escapeCsv(p.getCabinNumber())).append(",");
            sb.append(escapeCsv(p.getCabinCategory())).append(",");
            sb.append(escapeCsv(Boolean.TRUE.equals(p.getIsVip()) ? "YES" : "NO")).append(",");
            sb.append(escapeCsv(Boolean.TRUE.equals(p.getHealthDeclarationCompleted()) ? "YES" : "NO")).append(",");
            sb.append(escapeCsv(p.getRfidTag())).append(",");
            sb.append(escapeCsv(p.getCheckInStatus() != null ? p.getCheckInStatus().name() : "")).append(",");
            sb.append(escapeCsv(p.getBoardedAt() != null ? p.getBoardedAt().toString() : "")).append(",");
            sb.append(escapeCsv(p.getFolioStatus())).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String val) {
        if (val == null) {
            return "";
        }
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    public PassengerEmbarkationProfileDto toProfileDto(Passenger passenger) {
        return PassengerEmbarkationProfileDto.builder()
                .id(passenger.getId())
                .tourId(passenger.getTourId())
                .bookingCode(passenger.getBookingCode())
                .fullName(passenger.getFullName())
                .passportNumber(passenger.getPassportNumber())
                .nationality(passenger.getNationality())
                .emergencyContact(passenger.getEmergencyContact())
                .email(passenger.getEmail())
                .phoneNumber(passenger.getPhoneNumber())
                .cabinNumber(passenger.getCabinNumber())
                .cabinCategory(passenger.getCabinCategory() != null ? passenger.getCabinCategory() : "Standard Stateroom")
                .isVip(passenger.getIsVip())
                .healthDeclarationCompleted(passenger.getHealthDeclarationCompleted())
                .rfidTag(passenger.getRfidTag())
                .qrCode(passenger.getQrCode())
                .tourPackage(passenger.getTourPackage())
                .bleZone(passenger.getBleZone())
                .checkInStatus(passenger.getCheckInStatus())
                .boardedAt(passenger.getBoardedAt())
                .folioStatus(passenger.getFolioStatus())
                .build();
    }
}

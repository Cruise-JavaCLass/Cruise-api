package com.cruise.activity.service;

import com.cruise.activity.dto.PassengerResponse;
import com.cruise.activity.dto.RegisterPassengerRequest;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.enums.CheckInStatus;
import com.cruise.activity.repository.PassengerRepository;
import com.cruise.activity.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class PassengerService {

    private final PassengerRepository passengerRepository;

    public PassengerService(PassengerRepository passengerRepository) {
        this.passengerRepository = passengerRepository;
    }

    public PassengerResponse registerPassenger(RegisterPassengerRequest request) {
        Passenger passenger = Passenger.builder()
                .tourId(request.getTourId())
                .fullName(request.getFullName().trim())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .cabinNumber(request.getCabinNumber().trim())
                .rfidTag(request.getRfidTag() != null ? request.getRfidTag().trim() : null)
                .qrCode(request.getQrCode() != null ? request.getQrCode().trim() : null)
                .tourPackage(request.getTourPackage())
                .bleZone(request.getBleZone())
                .registrationStatus(request.getRegistrationStatus() != null ? request.getRegistrationStatus() : com.cruise.activity.enums.RegistrationStatus.CONFIRMED)
                .checkInStatus(request.getCheckInStatus() != null ? request.getCheckInStatus() : CheckInStatus.NOT_CHECKED_IN)
                .active(true)
                .build();

        if (passenger.getCheckInStatus() == CheckInStatus.BOARDED) {
            passenger.setBoardedAt(Instant.now());
        }

        Passenger saved = passengerRepository.save(passenger);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PassengerResponse> getRegisteredPassengersByTour(Long tourId) {
        return passengerRepository.findByTourIdAndActiveTrue(tourId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PassengerResponse> getRegisteredPassengersByTourAndStatus(
            Long tourId,
            com.cruise.activity.enums.RegistrationStatus status
    ) {
        if (status == null) {
            return getRegisteredPassengersByTour(tourId);
        }
        return passengerRepository.findByTourIdAndRegistrationStatusAndActiveTrue(tourId, status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PassengerResponse> getRegisteredPassengersByTourPaged(
            Long tourId,
            String keyword,
            com.cruise.activity.enums.RegistrationStatus status,
            Pageable pageable
    ) {
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        Page<Passenger> page = passengerRepository.searchPassengersWithStatus(tourId, cleanKeyword, status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PassengerResponse getPassengerById(Long id) {
        Passenger p = passengerRepository.findById(id)
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hành khách với ID " + id + " không tồn tại"));
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public PassengerResponse getPassengerByRfidTag(String rfidTag) {
        Passenger p = passengerRepository.findByRfidTagAndActiveTrue(rfidTag)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hành khách với thẻ RFID: " + rfidTag));
        return toResponse(p);
    }

    public PassengerResponse updateCheckInStatus(Long passengerId, CheckInStatus status) {
        Passenger p = passengerRepository.findById(passengerId)
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hành khách với ID " + passengerId + " không tồn tại"));

        p.setCheckInStatus(status);
        if (status == CheckInStatus.BOARDED && p.getBoardedAt() == null) {
            p.setBoardedAt(Instant.now());
        }
        return toResponse(passengerRepository.save(p));
    }

    public PassengerResponse updateRegistrationStatus(Long passengerId, com.cruise.activity.enums.RegistrationStatus status) {
        Passenger p = passengerRepository.findById(passengerId)
                .filter(Passenger::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hành khách với ID " + passengerId + " không tồn tại"));

        p.setRegistrationStatus(status);
        return toResponse(passengerRepository.save(p));
    }

    @Transactional(readOnly = true)
    public byte[] exportRegisteredPassengersCsv(Long tourId) {
        List<Passenger> passengers = passengerRepository.findByTourIdAndActiveTrue(tourId);
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM for Excel compatibility
        sb.append('\ufeff');
        sb.append("ID,Tour ID,Họ và Tên,Số Cabin,Mã Thẻ RFID,Mã QR,Gói Tour,Vị Trí BLE,Trạng Thái Check-in,Thời Gian Lên Tàu\n");

        for (Passenger p : passengers) {
            sb.append(escapeCsv(String.valueOf(p.getId()))).append(",");
            sb.append(escapeCsv(String.valueOf(p.getTourId()))).append(",");
            sb.append(escapeCsv(p.getFullName())).append(",");
            sb.append(escapeCsv(p.getCabinNumber())).append(",");
            sb.append(escapeCsv(p.getRfidTag())).append(",");
            sb.append(escapeCsv(p.getQrCode())).append(",");
            sb.append(escapeCsv(p.getTourPackage())).append(",");
            sb.append(escapeCsv(p.getBleZone())).append(",");
            sb.append(escapeCsv(p.getCheckInStatus() != null ? p.getCheckInStatus().name() : "")).append(",");
            sb.append(escapeCsv(p.getBoardedAt() != null ? p.getBoardedAt().toString() : "")).append("\n");
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
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

    private PassengerResponse toResponse(Passenger p) {
        return PassengerResponse.builder()
                .id(p.getId())
                .tourId(p.getTourId())
                .fullName(p.getFullName())
                .email(p.getEmail())
                .phoneNumber(p.getPhoneNumber())
                .cabinNumber(p.getCabinNumber())
                .rfidTag(p.getRfidTag())
                .qrCode(p.getQrCode())
                .tourPackage(p.getTourPackage())
                .bleZone(p.getBleZone())
                .registrationStatus(p.getRegistrationStatus())
                .checkInStatus(p.getCheckInStatus())
                .boardedAt(p.getBoardedAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}

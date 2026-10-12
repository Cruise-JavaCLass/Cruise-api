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
    public Page<PassengerResponse> getRegisteredPassengersByTourPaged(Long tourId, String keyword, Pageable pageable) {
        Page<Passenger> page;
        if (keyword != null && !keyword.isBlank()) {
            page = passengerRepository.searchPassengers(tourId, keyword.trim(), pageable);
        } else {
            page = passengerRepository.findByTourIdAndActiveTrue(tourId, pageable);
        }
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
                .checkInStatus(p.getCheckInStatus())
                .boardedAt(p.getBoardedAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}

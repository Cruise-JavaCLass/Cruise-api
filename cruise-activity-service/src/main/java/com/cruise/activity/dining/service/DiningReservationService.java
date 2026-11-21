package com.cruise.activity.dining.service;

import com.cruise.activity.dining.dto.CreateReservationRequest;
import com.cruise.activity.dining.dto.DiningReservationResponse;
import com.cruise.activity.dining.dto.DietaryAlertResponse;
import com.cruise.activity.dining.entity.DiningReservation;
import com.cruise.activity.dining.entity.DiningTable;
import com.cruise.activity.dining.entity.GuestDietaryProfile;
import com.cruise.activity.dining.enums.ReservationStatus;
import com.cruise.activity.dining.repository.DiningReservationRepository;
import com.cruise.activity.dining.repository.DiningTableRepository;
import com.cruise.activity.dining.repository.DietaryProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiningReservationService {

    private final DiningReservationRepository reservationRepository;
    private final DiningTableRepository tableRepository;
    private final DietaryProfileRepository dietaryProfileRepository;
    private final com.cruise.activity.repository.PassengerRepository passengerRepository;
    private final com.cruise.activity.dining.repository.RestaurantRepository restaurantRepository;

    @Transactional
    public DiningReservationResponse makeReservation(
            CreateReservationRequest request,
            Long passengerId
    ) {
        // Serialize both a passenger's overlapping requests and table allocation across passengers.
        passengerRepository.findByIdForUpdate(passengerId)
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found"));
        restaurantRepository.findByIdForUpdate(request.getRestaurantId())
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
        List<DiningReservation> conflicts =
                reservationRepository.findConflictingReservations(
                        passengerId, request.getReservationTime());

        if (!conflicts.isEmpty()) {
            throw new ReservationConflictException(
                    "Passenger already has a reservation at this time");
        }

        List<DiningTable> availableTables =
                tableRepository.findAvailableTables(
                        request.getRestaurantId(),
                        request.getReservationTime(),
                        request.getGuestCount());

        if (availableTables.isEmpty()) {
            throw new TableNotAvailableException("No available table");
        }

        DiningTable table = availableTables.get(0);

        DiningReservation reservation = DiningReservation.builder()
                .reservationCode(UUID.randomUUID().toString())
                .passengerId(passengerId)
                .cabinNumber(request.getCabinNumber())
                .diningTable(table)
                .reservationTime(request.getReservationTime())
                .guestCount(request.getGuestCount())
                .status(ReservationStatus.CONFIRMED)
                .build();

        DiningReservation saved = reservationRepository.save(reservation);

        return toResponse(saved);
    }

    @Transactional
    public void cancelReservation(Long reservationId, Long passengerId) {
        DiningReservation reservation =
                reservationRepository.findById(reservationId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Reservation not found"));

        if (!reservation.getPassengerId().equals(passengerId)) {
            throw new IllegalArgumentException(
                    "Reservation does not belong to passenger");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public List<DiningReservationResponse> getMyBookings(Long passengerId) {
        return reservationRepository.findByPassengerId(passengerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DietaryAlertResponse> checkGuestAllergens(Long passengerId) {
        List<GuestDietaryProfile> profiles =
                dietaryProfileRepository.findByPassengerId(passengerId);

        return profiles.stream()
                .map(profile -> DietaryAlertResponse.builder()
                        .passengerId(profile.getPassengerId())
                        .allergenType(profile.getAllergenType())
                        .severityLevel(profile.getSeverityLevel())
                        .specialNotes(profile.getSpecialNotes())
                        .build())
                .toList();
    }

    private DiningReservationResponse toResponse(
            DiningReservation reservation
    ) {
        DiningTable table = reservation.getDiningTable();

        return DiningReservationResponse.builder()
                .id(reservation.getId())
                .reservationCode(reservation.getReservationCode())
                .restaurantId(table.getRestaurant().getId())
                .restaurantName(table.getRestaurant().getName())
                .tableNumber(table.getTableNumber())
                .reservationTime(reservation.getReservationTime())
                .guestCount(reservation.getGuestCount())
                .status(reservation.getStatus())
                .build();
    }
}

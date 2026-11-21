package com.cruise.activity.dining.service;

import com.cruise.activity.dining.dto.*;
import com.cruise.activity.dining.entity.*;
import com.cruise.activity.dining.enums.*;
import com.cruise.activity.dining.repository.*;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.repository.PassengerRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiningReservationServiceTest {
    @Mock DiningReservationRepository reservationRepository;
    @Mock DiningTableRepository tableRepository;
    @Mock DietaryProfileRepository dietaryProfileRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock PassengerRepository passengerRepository;
    @InjectMocks DiningReservationService service;
    private Restaurant restaurant;
    private DiningTable table;
    private CreateReservationRequest request;
    @BeforeEach void setup() {
        restaurant=Restaurant.builder().name("Ocean Dining").location("Deck 5").maxCapacity(50).build();restaurant.setId(1L);
        table=DiningTable.builder().restaurant(restaurant).tableNumber("T1").capacity(4).tableType(DiningTableType.STANDARD).build();table.setId(2L);
        request=new CreateReservationRequest();request.setRestaurantId(1L);request.setCabinNumber("401");request.setGuestCount(2);request.setReservationTime(LocalDateTime.now().plusDays(1));
        Passenger passenger=Passenger.builder().tourId(1L).fullName("Guest").cabinNumber("401").build();passenger.setId(10L);
        lenient().when(passengerRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(passenger));
        lenient().when(restaurantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(restaurant));
    }
    private DiningReservation booking() {
        DiningReservation value=DiningReservation.builder().reservationCode("RES-1").passengerId(10L).cabinNumber("401").diningTable(table)
            .reservationTime(request.getReservationTime()).guestCount(2).status(ReservationStatus.CONFIRMED).build();value.setId(3L);return value;
    }
    @Test void booksAnAvailableTableAndMapsResponse() {
        when(reservationRepository.findConflictingReservations(10L,request.getReservationTime())).thenReturn(List.of());
        when(tableRepository.findAvailableTables(1L,request.getReservationTime(),2)).thenReturn(List.of(table));
        when(reservationRepository.save(any())).thenAnswer(invocation->{DiningReservation value=invocation.getArgument(0);value.setId(3L);return value;});
        DiningReservationResponse response=service.makeReservation(request,10L);
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);assertThat(response.getRestaurantName()).isEqualTo("Ocean Dining");
        assertThat(response.getTableNumber()).isEqualTo("T1");assertThat(response.getReservationCode()).isNotBlank();
        ArgumentCaptor<DiningReservation> captured=ArgumentCaptor.forClass(DiningReservation.class);verify(reservationRepository).save(captured.capture());
        assertThat(captured.getValue().getPassengerId()).isEqualTo(10L);assertThat(captured.getValue().getCabinNumber()).isEqualTo("401");
    }
    @Test void rejectsAlreadyReservedPassengerWithoutAllocatingAnotherTable() {
        when(reservationRepository.findConflictingReservations(10L,request.getReservationTime())).thenReturn(List.of(booking()));
        assertThatThrownBy(()->service.makeReservation(request,10L)).isInstanceOf(ReservationConflictException.class);
        verifyNoInteractions(tableRepository);verify(reservationRepository,never()).save(any());
    }
    @Test void rejectsAnAlreadyBookedOrUndersizedTableWhenNoAvailableTableIsReturned() {
        when(tableRepository.findAvailableTables(1L,request.getReservationTime(),2)).thenReturn(List.of());
        assertThatThrownBy(()->service.makeReservation(request,10L)).isInstanceOf(TableNotAvailableException.class);
        verify(reservationRepository,never()).save(any());
    }
    @Test void cancellingReleasesReservationByMarkingItCancelled() {
        DiningReservation booking=booking();when(reservationRepository.findById(3L)).thenReturn(Optional.of(booking));
        service.cancelReservation(3L,10L);assertThat(booking.getStatus()).isEqualTo(ReservationStatus.CANCELLED);verify(reservationRepository).save(booking);
    }
    @Test void cannotCancelAnotherPassengersBooking() {
        when(reservationRepository.findById(3L)).thenReturn(Optional.of(booking()));
        assertThatThrownBy(()->service.cancelReservation(3L,11L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not belong");
        verify(reservationRepository,never()).save(any());
    }
    @Test void unknownReservationIsRejected() {
        assertThatThrownBy(()->service.cancelReservation(999L,10L)).hasMessageContaining("not found");
    }
    @Test void listsOnlyRequestedPassengersBookingsAndSupportsEmptyResults() {
        when(reservationRepository.findByPassengerId(10L)).thenReturn(List.of(booking()));
        assertThat(service.getMyBookings(10L)).singleElement().extracting(DiningReservationResponse::getRestaurantId).isEqualTo(1L);
        assertThat(service.getMyBookings(11L)).isEmpty();
    }
    @Test void returnsAccurateAllergenAndSeverityWarningsWithoutInventingMissingProfiles() {
        GuestDietaryProfile profile=GuestDietaryProfile.builder().passengerId(10L).allergenType(AllergenType.PEANUT).severityLevel(SeverityLevel.CRITICAL).specialNotes("Avoid cross contact").build();
        when(dietaryProfileRepository.findByPassengerId(10L)).thenReturn(List.of(profile));
        assertThat(service.checkGuestAllergens(10L)).singleElement().satisfies(alert->{
            assertThat(alert.getAllergenType()).isEqualTo(AllergenType.PEANUT);assertThat(alert.getSeverityLevel()).isEqualTo(SeverityLevel.CRITICAL);
            assertThat(alert.getSpecialNotes()).isEqualTo("Avoid cross contact");assertThat(alert.getPassengerId()).isEqualTo(10L);
        });
        assertThat(service.checkGuestAllergens(11L)).isEmpty();
    }
    @Test void restaurantFiltersAndPaginationArePassedToRepository() {
        PageRequest page=PageRequest.of(0,10);Page<Restaurant> expected=new PageImpl<>(List.of(restaurant));
        when(restaurantRepository.searchRestaurants("Deck 5","SEAFOOD",page)).thenReturn(expected);
        assertThat(new DiningRestaurantService(restaurantRepository).searchRestaurants("Deck 5","SEAFOOD",page).getContent()).containsExactly(restaurant);
    }
    @Test void unknownPassengerOrRestaurantCannotReserve() {
        when(passengerRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.makeReservation(request,10L)).hasMessageContaining("Passenger not found");
        when(passengerRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(new Passenger()));
        when(restaurantRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.makeReservation(request,10L)).hasMessageContaining("Restaurant not found");
        verify(reservationRepository,never()).save(any());
    }
}

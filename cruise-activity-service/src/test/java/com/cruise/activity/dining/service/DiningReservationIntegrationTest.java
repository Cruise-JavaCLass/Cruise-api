package com.cruise.activity.dining.service;
import com.cruise.activity.dining.dto.*;
import com.cruise.activity.dining.entity.*;
import com.cruise.activity.dining.enums.*;
import com.cruise.activity.dining.repository.*;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.repository.PassengerRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.flyway.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:dining_lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000"})
class DiningReservationIntegrationTest {
    @Autowired DiningReservationService service;
    @Autowired RestaurantRepository restaurants;
    @Autowired DiningTableRepository tables;
    @Autowired PassengerRepository passengers;
    @Autowired DietaryProfileRepository profiles;
    private Restaurant restaurant;
    private DiningTable table;
    private CreateReservationRequest request;
    @BeforeEach void setup() {
        restaurant=restaurants.saveAndFlush(Restaurant.builder().name("Dining "+UUID.randomUUID()).location("Deck 5").maxCapacity(4).build());
        table=tables.saveAndFlush(DiningTable.builder().restaurant(restaurant).tableNumber("T1").capacity(4).tableType(DiningTableType.STANDARD).build());
        request=new CreateReservationRequest();request.setRestaurantId(restaurant.getId());request.setGuestCount(2);request.setCabinNumber("401");request.setReservationTime(LocalDateTime.now().plusDays(1).withNano(0));
    }
    private Long guest() { return passengers.saveAndFlush(Passenger.builder().tourId(1L).fullName("Guest "+UUID.randomUUID()).cabinNumber("401").active(true).build()).getId(); }
    @Test void twoPassengersCannotReserveSameTableConcurrentlyAndCancellationReallyFreesIt() throws Exception {
        Long first=guest();Long second=guest();CountDownLatch gate=new CountDownLatch(1);AtomicInteger rejected=new AtomicInteger();
        record Booking(Long passengerId,Long reservationId) {}
        List<Booking> successful=new ArrayList<>();
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            List<Future<Booking>> futures=new ArrayList<>();
            for(Long passenger:List.of(first,second)) futures.add(executor.submit(()->{
                gate.await();try{return new Booking(passenger,service.makeReservation(request,passenger).getId());}
                catch(TableNotAvailableException expected){rejected.incrementAndGet();return null;}
            }));
            gate.countDown();for(Future<Booking> future:futures){Booking booked=future.get(30,TimeUnit.SECONDS);if(booked!=null)successful.add(booked);}
        }
        assertThat(successful).hasSize(1);assertThat(rejected.get()).isEqualTo(1);
        assertThat(tables.findAvailableTables(restaurant.getId(),request.getReservationTime(),2)).isEmpty();
        Booking winner=successful.getFirst();service.cancelReservation(winner.reservationId(),winner.passengerId());
        assertThat(tables.findAvailableTables(restaurant.getId(),request.getReservationTime(),2)).extracting(DiningTable::getId).containsExactly(table.getId());
        Long other=winner.passengerId().equals(first)?second:first;
        assertThat(service.makeReservation(request,other).getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }
    @Test void samePassengerCannotBookTwoTablesInSameSlotAndAllergenProfilesRemainAccurate() {
        Long passenger=guest();
        tables.saveAndFlush(DiningTable.builder().restaurant(restaurant).tableNumber("T2").capacity(4).tableType(DiningTableType.WINDOW_VIEW).build());
        service.makeReservation(request,passenger);
        assertThatThrownBy(()->service.makeReservation(request,passenger)).isInstanceOf(ReservationConflictException.class);
        profiles.saveAndFlush(GuestDietaryProfile.builder().passengerId(passenger).allergenType(AllergenType.PEANUT).severityLevel(SeverityLevel.CRITICAL).specialNotes("Test profile").build());
        assertThat(service.checkGuestAllergens(passenger)).singleElement().satisfies(alert->{
            assertThat(alert.getAllergenType()).isEqualTo(AllergenType.PEANUT);assertThat(alert.getSeverityLevel()).isEqualTo(SeverityLevel.CRITICAL);
        });
    }
}

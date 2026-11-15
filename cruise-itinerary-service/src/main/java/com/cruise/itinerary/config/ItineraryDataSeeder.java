package com.cruise.itinerary.config;

import com.cruise.itinerary.entity.*;
import com.cruise.itinerary.enums.ShipStatus;
import com.cruise.itinerary.enums.TourStatus;
import com.cruise.itinerary.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Data seeder for cruise-itinerary-service.
 * Pre-populates default ships, ports, tour #1, daily schedules, stops, deck zones,
 * and system policies for development, test, and containerized environments.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ItineraryDataSeeder implements ApplicationRunner {

    private final CruiseShipRepository shipRepository;
    private final PortRepository portRepository;
    private final CruiseTourRepository tourRepository;
    private final DailyItineraryRepository dailyItineraryRepository;
    private final CruiseStopRepository stopRepository;
    private final ShipDeckZoneRepository deckZoneRepository;
    private final SystemCancellationPolicyRepository cancellationPolicyRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking itinerary seed data...");
        CruiseShip ship1 = seedShips();
        Port[] ports = seedPorts();
        Port port1 = ports[0];
        Port port2 = ports[1];

        seedTours(ship1, port1, port2);
        seedDeckZones(ship1);
        seedCancellationPolicies();
        log.info("Itinerary seed check completed.");
    }

    private CruiseShip seedShips() {
        return shipRepository.findByCode("SHIP-001").orElseGet(() -> {
            log.info("Seeding default cruise ship SHIP-001...");
            CruiseShip ship = CruiseShip.builder()
                    .name("Grand Pearl Voyager")
                    .code("SHIP-001")
                    .operator("Paradise Cruises")
                    .capacity(2500)
                    .crewCapacity(800)
                    .totalCabins(1200)
                    .totalDecks(14)
                    .manufacturingYear(2022)
                    .lengthMeters(315.5)
                    .beamMeters(38.0)
                    .draftMeters(8.5)
                    .status(ShipStatus.ACTIVE)
                    .description("Flagship luxury cruise liner operating coastal Vietnam routes.")
                    .amenities("Spa, Infinity Pool, Casino, Theater, Fitness Center, 5 Restaurants")
                    .imageUrl("https://images.unsplash.com/photo-1548574505-5e239809ee19")
                    .active(true)
                    .build();
            return shipRepository.save(ship);
        });
    }

    private Port[] seedPorts() {
        Port halongPort = portRepository.findByCodeIgnoreCase("VNHLG").orElseGet(() -> {
            log.info("Seeding default port VNHLG...");
            return portRepository.save(Port.builder()
                    .name("Cảng Quốc Tế Hạ Long")
                    .code("VNHLG")
                    .country("Việt Nam")
                    .city("Quảng Ninh")
                    .timezone("Asia/Ho_Chi_Minh")
                    .latitude(new BigDecimal("20.9500000"))
                    .longitude(new BigDecimal("107.0300000"))
                    .description("Cảng tàu khách quốc tế chuyên dụng tại Vịnh Hạ Long")
                    .active(true)
                    .build());
        });

        Port danangPort = portRepository.findByCodeIgnoreCase("VNDAD").orElseGet(() -> {
            log.info("Seeding default port VNDAD...");
            return portRepository.save(Port.builder()
                    .name("Cảng Tiên Sa Đà Nẵng")
                    .code("VNDAD")
                    .country("Việt Nam")
                    .city("Đà Nẵng")
                    .timezone("Asia/Ho_Chi_Minh")
                    .latitude(new BigDecimal("16.1200000"))
                    .longitude(new BigDecimal("108.2200000"))
                    .description("Cảng biển nước sâu phục vụ tàu du lịch tại Đà Nẵng")
                    .active(true)
                    .build());
        });

        return new Port[]{halongPort, danangPort};
    }

    private void seedTours(CruiseShip ship, Port port1, Port port2) {
        if (tourRepository.count() == 0) {
            log.info("Seeding Tour #1 and its schedule...");
            LocalDate startDate = LocalDate.now().plusDays(7);
            LocalDate endDate = startDate.plusDays(3);

            CruiseTour tour1 = CruiseTour.builder()
                    .name("Hải Trình Kỳ Quan Hạ Long - Đà Nẵng")
                    .code("TOUR-001")
                    .description("Hải trình 3 ngày 2 đêm khám phá kỳ quan thiên nhiên miền Trung.")
                    .ship(ship)
                    .durationDays(3)
                    .durationNights(2)
                    .departurePort(port1 != null ? port1.getName() : "Cảng Quốc Tế Hạ Long")
                    .destinationPort(port2 != null ? port2.getName() : "Cảng Tiên Sa Đà Nẵng")
                    .startDate(startDate)
                    .endDate(endDate)
                    .maxCapacity(2000)
                    .basePrice(new BigDecimal("12500000.00"))
                    .currency("VND")
                    .itineraryOverview("Hạ Long -> Vịnh Bắc Bộ -> Đà Nẵng")
                    .imageUrl("https://images.unsplash.com/photo-1507525428034-b723cf961d3e")
                    .status(TourStatus.PUBLISHED)
                    .active(true)
                    .build();

            CruiseTour savedTour = tourRepository.save(tour1);

            // Seed Daily Itineraries
            dailyItineraryRepository.save(DailyItinerary.builder()
                    .tour(savedTour)
                    .dayNumber(1)
                    .itineraryDate(startDate)
                    .title("Khởi hành từ Cảng Hạ Long")
                    .description("Làm thủ tục lên tàu, diễn tập cứu sinh SOLAS và thưởng ngoạn Vịnh Hạ Long.")
                    .port(port1)
                    .portName(port1 != null ? port1.getName() : "Cảng Quốc Tế Hạ Long")
                    .arrivalTime(LocalTime.of(8, 0))
                    .departureTime(LocalTime.of(12, 0))
                    .allAboardTime(LocalTime.of(11, 30))
                    .isSeaDay(false)
                    .weatherForecast("Nắng nhẹ, gió cấp 2-3, sóng êm")
                    .active(true)
                    .build());

            dailyItineraryRepository.save(DailyItinerary.builder()
                    .tour(savedTour)
                    .dayNumber(2)
                    .itineraryDate(startDate.plusDays(1))
                    .title("Hải trình trên Biển Đông")
                    .description("Ngày trên biển với các hoạt động giải trí, dạ tiệc thuyền trưởng và spa thư giãn.")
                    .isSeaDay(true)
                    .weatherForecast("Trời quang, tầm nhìn xa trên 10km")
                    .active(true)
                    .build());

            dailyItineraryRepository.save(DailyItinerary.builder()
                    .tour(savedTour)
                    .dayNumber(3)
                    .itineraryDate(startDate.plusDays(2))
                    .title("Cập cảng Tiên Sa Đà Nẵng")
                    .description("Cập cảng, tham quan danh thắng Ngũ Hành Sơn và kết thúc hải trình.")
                    .port(port2)
                    .portName(port2 != null ? port2.getName() : "Cảng Tiên Sa Đà Nẵng")
                    .arrivalTime(LocalTime.of(7, 30))
                    .departureTime(LocalTime.of(16, 0))
                    .allAboardTime(LocalTime.of(15, 30))
                    .isSeaDay(false)
                    .weatherForecast("Nắng đẹp, nhiệt độ 28C")
                    .active(true)
                    .build());

            // Seed Stops
            if (port1 != null) {
                stopRepository.save(CruiseStop.builder()
                        .tour(savedTour)
                        .port(port1)
                        .stopOrder(1)
                        .arrivalTime(startDate.atTime(8, 0))
                        .departureTime(startDate.atTime(12, 0))
                        .allAboardTime(startDate.atTime(11, 30))
                        .description("Điểm khởi hành hành trình tại Hạ Long")
                        .active(true)
                        .build());
            }

            if (port2 != null) {
                stopRepository.save(CruiseStop.builder()
                        .tour(savedTour)
                        .port(port2)
                        .stopOrder(2)
                        .arrivalTime(startDate.plusDays(2).atTime(7, 30))
                        .departureTime(startDate.plusDays(2).atTime(16, 0))
                        .allAboardTime(startDate.plusDays(2).atTime(15, 30))
                        .description("Điểm kết thúc hành trình tại Đà Nẵng")
                        .active(true)
                        .build());
            }
        }
    }

    private void seedDeckZones(CruiseShip ship) {
        if (ship != null && deckZoneRepository.findByShipIdOrderByDeckNumberAsc(ship.getId()).isEmpty()) {
            log.info("Seeding deck zones for ship ID {}...", ship.getId());
            deckZoneRepository.save(ShipDeckZone.builder()
                    .shipId(ship.getId())
                    .deckNumber(5)
                    .zoneName("Starlight Theater")
                    .zoneCode("DECK5-THEATER")
                    .maxSafetyCapacity(800)
                    .zoneType("THEATER")
                    .hasLifeboatAccess(false)
                    .isRestricted(false)
                    .build());

            deckZoneRepository.save(ShipDeckZone.builder()
                    .shipId(ship.getId())
                    .deckNumber(7)
                    .zoneName("Main Pool Deck")
                    .zoneCode("DECK7-POOL")
                    .maxSafetyCapacity(450)
                    .zoneType("POOL_DECK")
                    .hasLifeboatAccess(true)
                    .isRestricted(false)
                    .build());

            deckZoneRepository.save(ShipDeckZone.builder()
                    .shipId(ship.getId())
                    .deckNumber(9)
                    .zoneName("Bridge Navigation Deck")
                    .zoneCode("DECK9-BRIDGE")
                    .maxSafetyCapacity(30)
                    .zoneType("RESTRICTED_BRIDGE")
                    .hasLifeboatAccess(true)
                    .isRestricted(true)
                    .build());
        }
    }

    private void seedCancellationPolicies() {
        if (cancellationPolicyRepository.count() == 0) {
            log.info("Seeding system cancellation policies...");
            cancellationPolicyRepository.save(SystemCancellationPolicy.builder()
                    .policyName("Chính Sách Hủy Excursion Chuẩn 24H")
                    .serviceType("EXCURSION")
                    .hoursBeforeCutoff(24)
                    .refundPercentage(100)
                    .cancellationFee(BigDecimal.ZERO)
                    .isActive(true)
                    .description("Hoàn tiền 100% nếu hủy trước 24 giờ")
                    .build());

            cancellationPolicyRepository.save(SystemCancellationPolicy.builder()
                    .policyName("Chính Sách Hủy Tour Dài Ngày 72H")
                    .serviceType("TOUR")
                    .hoursBeforeCutoff(72)
                    .refundPercentage(80)
                    .cancellationFee(new BigDecimal("200000.00"))
                    .isActive(true)
                    .description("Hoàn 80% giá trị tour nếu hủy trước 72 giờ")
                    .build());

            cancellationPolicyRepository.save(SystemCancellationPolicy.builder()
                    .policyName("Chính Sách Hoạt Động Onboard 6H")
                    .serviceType("ONBOARD_ACTIVITY")
                    .hoursBeforeCutoff(6)
                    .refundPercentage(100)
                    .cancellationFee(BigDecimal.ZERO)
                    .isActive(true)
                    .description("Hủy miễn phí trước 6 giờ hoạt động")
                    .build());
        }
    }
}

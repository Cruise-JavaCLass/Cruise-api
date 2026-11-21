package com.cruise.activity.config;

import com.cruise.activity.entity.*;
import com.cruise.activity.enums.*;
import com.cruise.activity.repository.*;
import com.cruise.activity.service.AllAboardWatchdogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Data seeder for cruise-activity-service (KAN-700).
 * Pre-populates default activities, excursions, providers, RFID cards,
 * SOLAS muster status, notifications, and gangway watchdog schedules.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ActivityDataSeeder implements ApplicationRunner {

    private final OnboardActivityRepository activityRepository;
    private final ShoreExcursionRepository excursionRepository;
    private final TourProviderRepository providerRepository;
    private final RfidCardInventoryRepository rfidRepository;
    private final SolasMusterIncidentRepository incidentRepository;
    private final PassengerNotificationRepository notificationRepository;
    private final AllAboardWatchdogService watchdogService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking activity service seed data...");
        seedTourProviders();
        seedOnboardActivities();
        seedShoreExcursions();
        seedRfidCards();
        seedSolasMusterIncident();
        seedNotifications();
        initWatchdogSchedule();
        log.info("Activity service seed check completed.");
    }

    private void seedTourProviders() {
        if (!providerRepository.existsByCodeIgnoreCase("PROV-001")) {
            log.info("Seeding default tour provider PROV-001...");
            TourProvider provider = TourProvider.builder()
                    .code("PROV-001")
                    .name("Hạ Long Bay Marine Adventures")
                    .taxCode("0109988776")
                    .licenseNumber("GP-LH-2022-001")
                    .contactPerson("Nguyễn Văn Hùng")
                    .email("operations@halongmarine.vn")
                    .phone("+84901234567")
                    .emergencyHotline("+84909999888")
                    .portId("VNHLG")
                    .status(TourProviderStatus.ACTIVE)
                    .build();
            providerRepository.save(provider);
        }
    }

    private void seedOnboardActivities() {
        if (activityRepository.count() == 0) {
            log.info("Seeding default onboard activities for Tour #1...");
            LocalDate baseDate = LocalDate.now().plusDays(1);

            activityRepository.save(OnboardActivity.builder()
                    .tourId(1L)
                    .dayNumber(1)
                    .title("Captain's Welcome Gala Dinner")
                    .description("Exclusive evening reception with ship master and senior officers.")
                    .category("Dining & Entertainment")
                    .location("Grand Ballroom - Deck 7")
                    .activityDate(baseDate)
                    .startTime(LocalTime.of(19, 0))
                    .endTime(LocalTime.of(21, 30))
                    .maxCapacity(250)
                    .currentParticipants(0)
                    .price(BigDecimal.ZERO)
                    .isIncluded(true)
                    .status(ActivityStatus.SCHEDULED)
                    .active(true)
                    .build());

            activityRepository.save(OnboardActivity.builder()
                    .tourId(1L)
                    .dayNumber(2)
                    .title("Sunset Acoustic Lounge & Cocktails")
                    .description("Live jazz music and signature tropical cocktails on the open deck.")
                    .category("Music & Lounge")
                    .location("Starlight Lounge - Deck 9")
                    .activityDate(baseDate.plusDays(1))
                    .startTime(LocalTime.of(17, 30))
                    .endTime(LocalTime.of(19, 30))
                    .maxCapacity(120)
                    .currentParticipants(0)
                    .price(new BigDecimal("150000.00"))
                    .isIncluded(false)
                    .status(ActivityStatus.SCHEDULED)
                    .active(true)
                    .build());
        }
    }

    private void seedShoreExcursions() {
        if (excursionRepository.count() == 0) {
            log.info("Seeding default shore excursions for Tour #1...");
            LocalDate baseDate = LocalDate.now().plusDays(1);

            excursionRepository.save(ShoreExcursion.builder()
                    .tourId(1L)
                    .portId(1L)
                    .dayNumber(1)
                    .title("Khám Phá Hang Sửng Sốt & Chèo Thuyền Kayak")
                    .description("Tham quan hang động kỳ vĩ nhất Vịnh Hạ Long kết hợp chèo thuyền kayak tại Hang Luồn.")
                    .portName("Cảng Quốc Tế Hạ Long")
                    .meetingPoint("Cầu tàu số 2 - Ga Quốc tế")
                    .localProvider("Hạ Long Bay Marine Adventures")
                    .providerContact("+84901234567")
                    .excursionDate(baseDate)
                    .gatheringTime(LocalTime.of(8, 30))
                    .departureTime(LocalTime.of(9, 0))
                    .returnToShipTime(LocalTime.of(13, 0))
                    .maxCapacity(80)
                    .currentParticipants(0)
                    .price(new BigDecimal("850000.00"))
                    .currency("VND")
                    .isIncluded(false)
                    .status(ExcursionStatus.OPEN_FOR_BOOKING)
                    .active(true)
                    .build());
        }
    }

    private void seedRfidCards() {
        if (rfidRepository.count() == 0) {
            log.info("Seeding default blank RFID cards...");
            for (int i = 1; i <= 10; i++) {
                String uid = String.format("RFID-UID-%04d", i);
                rfidRepository.save(RfidCardInventory.builder()
                        .cardUid(uid)
                        .cardType("MIFARE_CLASSIC_1K")
                        .batchNumber("BATCH-2026-Q1-001")
                        .status(RfidCardStatus.AVAILABLE)
                        .build());
            }
        }
    }

    private void seedSolasMusterIncident() {
        if (incidentRepository.count() == 0) {
            log.info("Seeding default SOLAS muster drill incident...");
            incidentRepository.save(SolasMusterIncident.builder()
                    .incidentCode("SOLAS-DRILL-001")
                    .emergencyType(SolasEmergencyType.GENERAL_EMERGENCY)
                    .status(MusterStatus.DRILL_IN_PROGRESS)
                    .triggeredBy("SAFETY_OFFICER")
                    .announcementText("Diễn tập an toàn cứu sinh SOLAS bắt buộc trước giờ tàu xuất bến.")
                    .acousticSignalCode("7_SHORT_1_LONG")
                    .totalSoulsOnBoard(100)
                    .musteredSoulsCount(0)
                    .triggeredAt(Instant.now())
                    .build());
        }
    }

    private void seedNotifications() {
        if (notificationRepository.count() == 0) {
            log.info("Seeding default operational notification...");
            notificationRepository.save(PassengerNotification.builder()
                    .passengerId(1L)
                    .title("Chào mừng Quý khách lên tàu Grand Pearl Voyager")
                    .message("Hành trình khám phá Hạ Long - Đà Nẵng đã bắt đầu. Xin vui lòng tham dự diễn tập an toàn SOLAS lúc 10:00.")
                    .category(NotificationCategory.OPERATIONAL)
                    .actionUrl("/portal/itinerary")
                    .build());
        }
    }

    private void initWatchdogSchedule() {
        try {
            Instant allAboard = Instant.now().plus(Duration.ofHours(4));
            Instant departure = Instant.now().plus(Duration.ofHours(5));
            watchdogService.configurePortDepartureSchedule(1L, "Cảng Quốc Tế Hạ Long", allAboard, departure);
            log.info("Default all-aboard watchdog schedule initialized for PortCall ID 1.");
        } catch (Exception e) {
            log.warn("Failed to initialize watchdog schedule: {}", e.getMessage());
        }
    }
}

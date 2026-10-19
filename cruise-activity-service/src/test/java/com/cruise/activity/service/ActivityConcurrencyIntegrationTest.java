package com.cruise.activity.service;

import com.cruise.activity.dto.ActivityRegistrationRequest;
import com.cruise.activity.dto.ActivityRegistrationResponse;
import com.cruise.activity.entity.ActivityRegistration;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.enums.RegistrationStatus;
import com.cruise.activity.repository.ActivityRegistrationAuditLogRepository;
import com.cruise.activity.repository.ActivityRegistrationRepository;
import com.cruise.activity.repository.OnboardActivityRepository;
import com.cruise.activity.repository.PassengerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Activity Concurrency & Capacity Enforcement Integration Test (KAN-218)")
class ActivityConcurrencyIntegrationTest {

    @Autowired
    private ActivityRegistrationService registrationService;

    @Autowired
    private OnboardActivityRepository activityRepository;

    @Autowired
    private PassengerRepository passengerRepository;

    @Autowired
    private ActivityRegistrationRepository registrationRepository;

    @Autowired
    private ActivityRegistrationAuditLogRepository auditLogRepository;

    private Long activityId;
    private final List<Long> passengerIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        registrationRepository.deleteAll();

        // Tạo 1 hoạt động với giới hạn 3 suất tham gia
        OnboardActivity activity = activityRepository.save(OnboardActivity.builder()
                .tourId(1L)
                .dayNumber(2)
                .title("Exclusive Wine Tasting Workshop")
                .description("Giới hạn chỉ 3 suất tham gia đặc biệt")
                .location("Cellar Deck 3")
                .activityDate(LocalDate.now().plusDays(3))
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .maxCapacity(3)
                .currentParticipants(0)
                .price(BigDecimal.valueOf(250000))
                .isIncluded(false)
                .status(ActivityStatus.SCHEDULED)
                .cancellationCutOffMinutes(60)
                .refundPercentage(100)
                .active(true)
                .build());
        this.activityId = activity.getId();

        // Tạo 20 hành khách độc lập
        for (int i = 1; i <= 20; i++) {
            Passenger p = passengerRepository.save(Passenger.builder()
                    .tourId(1L)
                    .fullName("Concurrent Passenger " + i)
                    .email("passenger" + i + "@concurrency.test")
                    .cabinNumber("CABIN-" + (100 + i))
                    .active(true)
                    .build());
            passengerIds.add(p.getId());
        }
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        registrationRepository.deleteAll();
    }

    @Test
    @DisplayName("20 luồng đồng thời tranh chấp 3 suất: Đảm bảo đúng 3 CONFIRMED và 17 WAITLISTED (Zero-Overbooking)")
    void testConcurrentRegistrations_ZeroOverbooking() throws InterruptedException, ExecutionException {
        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<ActivityRegistrationResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final Long passengerId = passengerIds.get(i);
            final String cabin = "CABIN-" + (100 + i + 1);

            futures.add(executorService.submit(() -> {
                readyLatch.countDown();
                // Chờ hiệu lệnh xuất phát đồng thời
                startLatch.await();

                ActivityRegistrationRequest request = ActivityRegistrationRequest.builder()
                        .passengerId(passengerId)
                        .cabinNumber(cabin)
                        .build();

                return registrationService.registerActivity(activityId, request);
            }));
        }

        // Chờ toàn bộ 20 luồng sẵn sàng
        readyLatch.await(5, TimeUnit.SECONDS);
        // Kích hoạt tất cả 20 luồng cùng lúc
        startLatch.countDown();

        int confirmedCount = 0;
        int waitlistedCount = 0;

        for (Future<ActivityRegistrationResponse> future : futures) {
            ActivityRegistrationResponse response = future.get();
            if ("CONFIRMED".equals(response.getStatus())) {
                confirmedCount++;
            } else if ("WAITLISTED".equals(response.getStatus())) {
                waitlistedCount++;
            }
        }

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // Xác nhận chính xác 3 suất được xác nhận và 17 suất vào hàng đợi
        assertThat(confirmedCount).isEqualTo(3);
        assertThat(waitlistedCount).isEqualTo(17);

        // Xác minh trong DB
        long dbConfirmed = registrationRepository.countByActivityIdAndStatusAndActiveTrue(activityId, RegistrationStatus.CONFIRMED);
        long dbWaitlisted = registrationRepository.countByActivityIdAndStatusAndActiveTrue(activityId, RegistrationStatus.WAITLISTED);

        assertThat(dbConfirmed).isEqualTo(3);
        assertThat(dbWaitlisted).isEqualTo(17);

        OnboardActivity updatedActivity = activityRepository.findById(activityId).orElseThrow();
        assertThat(updatedActivity.getCurrentParticipants()).isEqualTo(3);
    }
}

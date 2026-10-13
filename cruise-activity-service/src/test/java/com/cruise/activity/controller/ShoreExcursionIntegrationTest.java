package com.cruise.activity.controller;

import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.repository.ShoreExcursionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Shore Excursion End-to-End Integration Tests (KAN-168)")
class ShoreExcursionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShoreExcursionRepository excursionRepository;

    private ShoreExcursion existingExcursion;

    @BeforeEach
    void setUp() {
        excursionRepository.deleteAll();

        existingExcursion = ShoreExcursion.builder()
                .tourId(10L)
                .portId(20L)
                .dayNumber(2)
                .title("Tour Thăm Đảo Khỉ & Lặn Ngắm San Hô")
                .description("Tham quan đảo khỉ tự nhiên và lặn biển ngắm san hô")
                .portName("Cảng Nha Trang")
                .meetingPoint("Bến tàu du lịch Cầu Đá")
                .localProvider("Nha Trang Sea Adventures")
                .providerContact("+84 987 654 321")
                .excursionDate(LocalDate.of(2026, 11, 15))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(13, 0))
                .maxCapacity(50)
                .currentParticipants(5)
                .price(BigDecimal.valueOf(950000))
                .childPrice(BigDecimal.valueOf(500000))
                .currency("VND")
                .isIncluded(false)
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();

        existingExcursion = excursionRepository.save(existingExcursion);
    }

    @Test
    @DisplayName("End-to-End: configurePriceCapacityAndSchedule successfully updates database (KAN-168)")
    void configurePriceCapacityAndSchedule_E2E() throws Exception {
        String reqJson = """
                {
                    "excursionDate": "2026-11-15",
                    "gatheringTime": "07:30:00",
                    "departureTime": "08:00:00",
                    "returnToShipTime": "12:30:00",
                    "maxCapacity": 45,
                    "price": 1100000,
                    "childPrice": 550000,
                    "isIncluded": false
                }
                """;

        mockMvc.perform(patch("/api/excursions/" + existingExcursion.getId() + "/price-capacity-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maxCapacity").value(45))
                .andExpect(jsonPath("$.data.remainingCapacity").value(40)) // 45 - 5 current participants
                .andExpect(jsonPath("$.data.price").value(1100000))
                .andExpect(jsonPath("$.data.childPrice").value(550000))
                .andExpect(jsonPath("$.data.gatheringTime").value("07:30:00"))
                .andExpect(jsonPath("$.data.departureTime").value("08:00:00"))
                .andExpect(jsonPath("$.data.returnToShipTime").value("12:30:00"));

        ShoreExcursion updatedInDb = excursionRepository.findById(existingExcursion.getId()).orElseThrow();
        assertThat(updatedInDb.getMaxCapacity()).isEqualTo(45);
        assertThat(updatedInDb.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(1100000));
        assertThat(updatedInDb.getReturnToShipTime()).isEqualTo(LocalTime.of(12, 30));
    }

    @Test
    @DisplayName("End-to-End: Create new excursion, configure price/capacity/schedule, transition status and delete")
    void fullExcursionLifecycle_E2E() throws Exception {
        // 1. Create excursion
        String createJson = """
                {
                    "tourId": 10,
                    "portId": 20,
                    "dayNumber": 3,
                    "title": "Tour Khám Phá Tháp Bà Ponagar",
                    "description": "Di tích văn hóa Chăm pa",
                    "portName": "Cảng Nha Trang",
                    "meetingPoint": "Cầu tàu B",
                    "excursionDate": "2026-11-16",
                    "gatheringTime": "14:00:00",
                    "departureTime": "14:30:00",
                    "returnToShipTime": "17:30:00",
                    "maxCapacity": 30,
                    "price": 600000,
                    "childPrice": 300000
                }
                """;

        mockMvc.perform(post("/api/excursions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Tour Khám Phá Tháp Bà Ponagar"));

        ShoreExcursion created = excursionRepository.findByTourIdAndActiveTrueOrderByExcursionDateAscGatheringTimeAsc(10L)
                .stream()
                .filter(e -> "Tour Khám Phá Tháp Bà Ponagar".equals(e.getTitle()))
                .findFirst()
                .orElseThrow();

        Long excursionId = created.getId();

        // 2. Configure Price, Capacity and Schedule (KAN-168)
        String configJson = """
                {
                    "gatheringTime": "13:30:00",
                    "departureTime": "14:00:00",
                    "returnToShipTime": "17:00:00",
                    "maxCapacity": 35,
                    "price": 650000,
                    "childPrice": 320000
                }
                """;

        mockMvc.perform(patch("/api/excursions/" + excursionId + "/price-capacity-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxCapacity").value(35))
                .andExpect(jsonPath("$.data.price").value(650000));

        // 3. Transition status to OPEN_FOR_BOOKING
        String statusJson = """
                {
                    "status": "OPEN_FOR_BOOKING"
                }
                """;

        mockMvc.perform(patch("/api/excursions/" + excursionId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN_FOR_BOOKING"));

        // 4. Soft Delete excursion
        mockMvc.perform(delete("/api/excursions/" + excursionId))
                .andExpect(status().isOk());

        ShoreExcursion deleted = excursionRepository.findById(excursionId).orElseThrow();
        assertThat(deleted.getActive()).isFalse();
        assertThat(deleted.getStatus()).isEqualTo(ExcursionStatus.CANCELLED);
    }
}

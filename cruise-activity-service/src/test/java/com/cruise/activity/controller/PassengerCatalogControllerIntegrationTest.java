package com.cruise.activity.controller;

import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.enums.CatalogItemType;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.repository.OnboardActivityRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Test cho Passenger Catalog Browsing & Realtime Capacity (KAN-213)
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
class PassengerCatalogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OnboardActivityRepository activityRepository;

    @Autowired
    private ShoreExcursionRepository excursionRepository;

    private Long tourId = 9999L;
    private OnboardActivity activity1;
    private ShoreExcursion excursion1;

    @BeforeEach
    void setUp() {
        activityRepository.deleteAll();
        excursionRepository.deleteAll();

        activity1 = OnboardActivity.builder()
                .tourId(tourId)
                .dayNumber(1)
                .title("Evening Jazz Night")
                .description("Relaxing jazz performance at Royal Lounge")
                .category("ENTERTAINMENT")
                .location("Deck 5 Royal Lounge")
                .activityDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .maxCapacity(50)
                .currentParticipants(20)
                .price(BigDecimal.ZERO)
                .isIncluded(true)
                .status(ActivityStatus.SCHEDULED)
                .active(true)
                .build();
        activity1 = activityRepository.save(activity1);

        excursion1 = ShoreExcursion.builder()
                .tourId(tourId)
                .portId(101L)
                .dayNumber(2)
                .title("Halong Bay Luxury Kayak Adventure")
                .description("Explore mysterious caves and emerald waters")
                .portName("Halong International Port")
                .meetingPoint("Pier 3 Customer Kiosk")
                .localProvider("Halong Heritage Tours")
                .providerContact("+84988776655")
                .excursionDate(LocalDate.now().plusDays(2))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(16, 0))
                .maxCapacity(30)
                .currentParticipants(30) // Sold out
                .price(new BigDecimal("120.00"))
                .childPrice(new BigDecimal("80.00"))
                .currency("USD")
                .isIncluded(false)
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();
        excursion1 = excursionRepository.save(excursion1);
    }

    @Test
    @DisplayName("GET /api/passenger-portal/catalog/tour/{tourId} - Search all catalog items")
    void shouldReturnAllCatalogItems() throws Exception {
        mockMvc.perform(get("/api/passenger-portal/catalog/tour/{tourId}", tourId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/passenger-portal/catalog/tour/{tourId} - Filter by itemType ONBOARD_ACTIVITY")
    void shouldFilterByOnboardActivity() throws Exception {
        mockMvc.perform(get("/api/passenger-portal/catalog/tour/{tourId}", tourId)
                        .param("itemType", "ONBOARD_ACTIVITY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Evening Jazz Night"))
                .andExpect(jsonPath("$.data.content[0].remainingCapacity").value(30))
                .andExpect(jsonPath("$.data.content[0].isSoldOut").value(false));
    }

    @Test
    @DisplayName("GET /api/passenger-portal/catalog/tour/{tourId} - Filter by itemType SHORE_EXCURSION and check Sold Out")
    void shouldFilterByShoreExcursionAndCheckSoldOut() throws Exception {
        mockMvc.perform(get("/api/passenger-portal/catalog/tour/{tourId}", tourId)
                        .param("itemType", "SHORE_EXCURSION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Halong Bay Luxury Kayak Adventure"))
                .andExpect(jsonPath("$.data.content[0].remainingCapacity").value(0))
                .andExpect(jsonPath("$.data.content[0].isSoldOut").value(true));
    }

    @Test
    @DisplayName("GET /api/passenger-portal/catalog/tour/{tourId} - Filter by isIncluded true (Free activities)")
    void shouldFilterByIsIncluded() throws Exception {
        mockMvc.perform(get("/api/passenger-portal/catalog/tour/{tourId}", tourId)
                        .param("isIncluded", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].isIncluded").value(true));
    }

    @Test
    @DisplayName("GET /api/passenger-portal/catalog/tour/{tourId} - Filter by keyword search")
    void shouldFilterByKeyword() throws Exception {
        mockMvc.perform(get("/api/passenger-portal/catalog/tour/{tourId}", tourId)
                        .param("keyword", "kayak"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Halong Bay Luxury Kayak Adventure"));
    }

    @Test
    @DisplayName("GET /api/passenger-portal/catalog/detail/ONBOARD_ACTIVITY/{id} - Get activity detail")
    void shouldGetActivityDetail() throws Exception {
        mockMvc.perform(get("/api/passenger-portal/catalog/detail/ONBOARD_ACTIVITY/{id}", activity1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Evening Jazz Night"))
                .andExpect(jsonPath("$.data.remainingCapacity").value(30));
    }

    @Test
    @DisplayName("GET /api/passenger-portal/catalog/detail/SHORE_EXCURSION/{id} - Get excursion detail with operational timeline")
    void shouldGetExcursionDetail() throws Exception {
        mockMvc.perform(get("/api/passenger-portal/catalog/detail/SHORE_EXCURSION/{id}", excursion1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Halong Bay Luxury Kayak Adventure"))
                .andExpect(jsonPath("$.data.portId").value(101))
                .andExpect(jsonPath("$.data.gatheringTime").value("08:00:00"))
                .andExpect(jsonPath("$.data.returnToShipTime").value("16:00:00"));
    }

    @Test
    @DisplayName("GET /api/passenger-portal/catalog/tour/{tourId} - Filter by dayNumber and portId")
    void shouldFilterByDayNumberAndPortId() throws Exception {
        mockMvc.perform(get("/api/passenger-portal/catalog/tour/{tourId}", tourId)
                        .param("dayNumber", "2")
                        .param("portId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(excursion1.getId()));
    }

    @Test
    @DisplayName("GET /api/passenger-portal/catalog/tour/{tourId} - Filter by keyword matching description and location")
    void shouldFilterByKeywordMatchingDescAndLocation() throws Exception {
        // match activity description
        mockMvc.perform(get("/api/passenger-portal/catalog/tour/{tourId}", tourId)
                        .param("keyword", "jazz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(activity1.getId()));

        // match activity location
        mockMvc.perform(get("/api/passenger-portal/catalog/tour/{tourId}", tourId)
                        .param("keyword", "Lounge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(activity1.getId()));

        // match excursion meeting point
        mockMvc.perform(get("/api/passenger-portal/catalog/tour/{tourId}", tourId)
                        .param("keyword", "Pier 3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(excursion1.getId()));
    }

    @Test
    @DisplayName("GET /api/passenger-portal/catalog/detail/ONBOARD_ACTIVITY/{id} - Not found throws exception")
    void shouldThrowNotFoundForNonExistentActivity() throws Exception {
        mockMvc.perform(get("/api/passenger-portal/catalog/detail/ONBOARD_ACTIVITY/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/passenger-portal/catalog/detail/SHORE_EXCURSION/{id} - Not found throws exception")
    void shouldThrowNotFoundForNonExistentExcursion() throws Exception {
        mockMvc.perform(get("/api/passenger-portal/catalog/detail/SHORE_EXCURSION/{id}", 999999L))
                .andExpect(status().isNotFound());
    }
}

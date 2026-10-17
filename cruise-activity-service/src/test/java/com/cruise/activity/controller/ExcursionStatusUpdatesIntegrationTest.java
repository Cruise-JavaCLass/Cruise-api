package com.cruise.activity.controller;

import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.repository.ShoreExcursionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test suite kiểm thử tích hợp toàn diện cho các thao tác cập nhật trạng thái tour bờ (KAN-191).
 * Kiểm thử thông qua MockMvc và verify trực tiếp trong database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Excursion Status Updates Integration Tests (KAN-191)")
class ExcursionStatusUpdatesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShoreExcursionRepository excursionRepository;

    private ShoreExcursion excursion;

    @BeforeEach
    void setUp() {
        excursionRepository.deleteAll();

        excursion = ShoreExcursion.builder()
                .tourId(10L)
                .portId(20L)
                .dayNumber(2)
                .title("Tour Khám Phá Địa Đạo Củ Chi")
                .description("Tham quan di tích lịch sử địa đạo")
                .portName("Cảng Hiệp Phước - TP.HCM")
                .meetingPoint("Cầu tàu đón khách số 1")
                .localProvider("Saigon Tourist")
                .providerContact("+84 901 234 567")
                .excursionDate(LocalDate.of(2026, 11, 25))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(13, 0))
                .maxCapacity(40)
                .currentParticipants(10)
                .price(BigDecimal.valueOf(800000))
                .childPrice(BigDecimal.valueOf(400000))
                .currency("VND")
                .isIncluded(false)
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();

        excursion = excursionRepository.save(excursion);
    }

    @Nested
    @DisplayName("API PATCH /api/excursions/{id}/status — Cập nhật vòng đời trạng thái")
    class StatusTransitionApiTests {

        @Test
        @DisplayName("Chuyển trạng thái từ SCHEDULED -> OPEN_FOR_BOOKING thành công")
        void shouldTransitionToOpenForBooking() throws Exception {
            String payload = """
                    {
                        "status": "OPEN_FOR_BOOKING"
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/" + excursion.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("OPEN_FOR_BOOKING"))
                    .andExpect(jsonPath("$.data.cancellationReason").doesNotExist());

            ShoreExcursion updatedInDb = excursionRepository.findById(excursion.getId()).orElseThrow();
            assertThat(updatedInDb.getStatus()).isEqualTo(ExcursionStatus.OPEN_FOR_BOOKING);
        }

        @Test
        @DisplayName("Chuyển trạng thái sang DELAYED khi gặp thời tiết xấu hoặc trục trặc")
        void shouldTransitionToDelayed() throws Exception {
            String payload = """
                    {
                        "status": "DELAYED"
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/" + excursion.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("DELAYED"));

            ShoreExcursion updatedInDb = excursionRepository.findById(excursion.getId()).orElseThrow();
            assertThat(updatedInDb.getStatus()).isEqualTo(ExcursionStatus.DELAYED);
        }

        @Test
        @DisplayName("Hủy tour (CANCELLED) kèm lý do giải thích chi tiết")
        void shouldTransitionToCancelledWithReason() throws Exception {
            String reason = "Tàu cập cảng trễ 3 tiếng do thủy triều thấp";
            String payload = String.format("""
                    {
                        "status": "CANCELLED",
                        "cancellationReason": "%s"
                    }
                    """, reason);

            mockMvc.perform(patch("/api/excursions/" + excursion.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.cancellationReason").value(reason));

            ShoreExcursion updatedInDb = excursionRepository.findById(excursion.getId()).orElseThrow();
            assertThat(updatedInDb.getStatus()).isEqualTo(ExcursionStatus.CANCELLED);
            assertThat(updatedInDb.getCancellationReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("Luồng trọn vẹn: SCHEDULED -> OPEN_FOR_BOOKING -> IN_PROGRESS -> COMPLETED")
        void shouldCompleteFullLifecycle() throws Exception {
            Long id = excursion.getId();

            // 1. OPEN_FOR_BOOKING
            mockMvc.perform(patch("/api/excursions/" + id + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"OPEN_FOR_BOOKING\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("OPEN_FOR_BOOKING"));

            // 2. IN_PROGRESS
            mockMvc.perform(patch("/api/excursions/" + id + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"IN_PROGRESS\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

            // 3. COMPLETED
            mockMvc.perform(patch("/api/excursions/" + id + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"COMPLETED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));

            ShoreExcursion inDb = excursionRepository.findById(id).orElseThrow();
            assertThat(inDb.getStatus()).isEqualTo(ExcursionStatus.COMPLETED);

            // 4. COMPLETED là trạng thái kết thúc, cố tình chuyển sang trạng thái khác bị từ chối 400
            mockMvc.perform(patch("/api/excursions/" + id + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"IN_PROGRESS\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("Không thể chuyển trạng thái tour bờ")));
        }

        @Test
        @DisplayName("Chuyển đổi trạng thái bất hợp lệ (nhảy cóc từ SCHEDULED sang COMPLETED) trả về 400 Bad Request")
        void shouldRejectInvalidJumpTransition() throws Exception {
            String payload = """
                    {
                        "status": "COMPLETED"
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/" + excursion.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("Không thể chuyển trạng thái tour bờ từ 'SCHEDULED' sang 'COMPLETED'")));

            ShoreExcursion unmodified = excursionRepository.findById(excursion.getId()).orElseThrow();
            assertThat(unmodified.getStatus()).isEqualTo(ExcursionStatus.SCHEDULED);
        }
    }

    @Nested
    @DisplayName("API GET /api/excursions/{id}/status/allowed-transitions & Lọc theo Status")
    class StatusQueriesApiTests {

        @Test
        @DisplayName("Lấy danh sách các trạng thái kế tiếp được phép chuyển đổi")
        void shouldGetAllowedTransitions() throws Exception {
            mockMvc.perform(get("/api/excursions/" + excursion.getId() + "/status/allowed-transitions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", containsInAnyOrder("OPEN_FOR_BOOKING", "DELAYED", "CANCELLED")));
        }

        @Test
        @DisplayName("Lọc danh sách tour bờ theo trạng thái tour /api/excursions/tour/{tourId}/status/{status}")
        void shouldFilterExcursionsByStatus() throws Exception {
            mockMvc.perform(get("/api/excursions/tour/10/status/SCHEDULED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].id").value(excursion.getId()))
                    .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"));

            mockMvc.perform(get("/api/excursions/tour/10/status/IN_PROGRESS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }
    }
}

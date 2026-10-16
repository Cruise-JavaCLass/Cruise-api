package com.cruise.activity.controller;

import com.cruise.activity.entity.ExcursionGuide;
import com.cruise.activity.entity.ExcursionVehicle;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.enums.GuideRole;
import com.cruise.activity.enums.GuideStatus;
import com.cruise.activity.enums.VehicleStatus;
import com.cruise.activity.repository.ExcursionGuideRepository;
import com.cruise.activity.repository.ExcursionVehicleRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bộ kiểm thử tích hợp API cho việc Quản lý Phương tiện và Hướng dẫn viên tour bờ (KAN-177)
 * Kiểm thử toàn diện các endpoint:
 * 1. POST /api/excursions/{id}/vehicles & GET & PATCH & DELETE
 * 2. POST /api/excursions/{id}/guides & GET & PATCH & DELETE
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Excursion Logistics (Vehicles & Guides) API Integration Tests (KAN-177)")
class ExcursionLogisticsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShoreExcursionRepository excursionRepository;

    @Autowired
    private ExcursionVehicleRepository vehicleRepository;

    @Autowired
    private ExcursionGuideRepository guideRepository;

    private ShoreExcursion seededExcursion;
    private ExcursionVehicle seededVehicle;
    private ExcursionGuide seededGuide;

    @BeforeEach
    void setUp() {
        seededExcursion = ShoreExcursion.builder()
                .tourId(1L)
                .portId(2L)
                .dayNumber(2)
                .title("Tour Khám Phá Vịnh Lan Hạ")
                .portName("Cảng Gia Luận")
                .meetingPoint("Bến phà Gia Luận")
                .localProvider("Lan Ha Discovery Co.")
                .providerContact("+84 225 388 9900")
                .excursionDate(LocalDate.now().plusDays(4))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 0))
                .maxCapacity(60)
                .currentParticipants(0)
                .price(BigDecimal.valueOf(850000))
                .currency("VND")
                .isIncluded(true)
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();
        seededExcursion = excursionRepository.save(seededExcursion);

        seededVehicle = ExcursionVehicle.builder()
                .excursionId(seededExcursion.getId())
                .vehicleCode("VEH-01")
                .licensePlate("14B-012.34")
                .vehicleType("Tàu trung chuyển Tender 60 Chỗ")
                .driverName("Trần Văn Mạnh")
                .driverPhone("+84 903 456 789")
                .capacity(60)
                .assignedPassengers(48)
                .status(VehicleStatus.READY)
                .operatorName("Halong Heritage Travel Co.")
                .active(true)
                .build();
        seededVehicle = vehicleRepository.save(seededVehicle);

        seededGuide = ExcursionGuide.builder()
                .excursionId(seededExcursion.getId())
                .guideCode("GUD-01")
                .fullName("Hoàng Hải Đăng")
                .phoneNumber("+84 912 888 999")
                .role(GuideRole.LEAD)
                .languages("Tiếng Việt, English")
                .licenseNumber("HDV-QG-88219")
                .status(GuideStatus.ACTIVE)
                .rating(4.9)
                .active(true)
                .build();
        seededGuide = guideRepository.save(seededGuide);
    }

    @Nested
    @DisplayName("1. Phương tiện vận chuyển (Vehicles API Tests)")
    class VehicleApiTests {

        @Test
        @DisplayName("POST /api/excursions/{id}/vehicles - Thêm phương tiện thành công (201 Created)")
        void addVehicle_Success() throws Exception {
            String json = """
                    {
                        "licensePlate": "43B-088.99",
                        "vehicleType": "Xe du lịch Universe 45 Chỗ",
                        "driverName": "Phạm Hùng Cường",
                        "driverPhone": "+84 905 889 900",
                        "capacity": 45,
                        "assignedPassengers": 0,
                        "status": "READY",
                        "operatorName": "Danang Green Horizon Tours"
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/vehicles", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.vehicles", hasSize(2)));
        }

        @Test
        @DisplayName("GET /api/excursions/{id}/vehicles - Lấy danh sách phương tiện (200 OK)")
        void getVehicles_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/vehicles", seededExcursion.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].licensePlate").value("14B-012.34"));
        }

        @Test
        @DisplayName("PATCH /api/excursions/{id}/vehicles/{vehicleId} - Cập nhật phương tiện thành công (200 OK)")
        void updateVehicle_Success() throws Exception {
            String patchJson = """
                    {
                        "driverName": "Trần Văn Mạnh (Tài xế chính)",
                        "capacity": 65,
                        "status": "ON_TRIP"
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/vehicles/{vehicleId}",
                            seededExcursion.getId(), seededVehicle.getVehicleCode())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            ExcursionVehicle updated = vehicleRepository.findById(seededVehicle.getId()).orElseThrow();
            assertThat(updated.getDriverName()).isEqualTo("Trần Văn Mạnh (Tài xế chính)");
            assertThat(updated.getCapacity()).isEqualTo(65);
            assertThat(updated.getStatus()).isEqualTo(VehicleStatus.ON_TRIP);
        }

        @Test
        @DisplayName("DELETE /api/excursions/{id}/vehicles/{vehicleId} - Xóa phương tiện thành công (200 OK)")
        void removeVehicle_Success() throws Exception {
            mockMvc.perform(delete("/api/excursions/{id}/vehicles/{vehicleId}",
                            seededExcursion.getId(), seededVehicle.getVehicleCode())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            ExcursionVehicle v = vehicleRepository.findById(seededVehicle.getId()).orElseThrow();
            assertThat(v.getActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("2. Hướng dẫn viên (Guides API Tests)")
    class GuideApiTests {

        @Test
        @DisplayName("POST /api/excursions/{id}/guides - Thêm hướng dẫn viên thành công (201 Created)")
        void addGuide_Success() throws Exception {
            String json = """
                    {
                        "fullName": "Đặng Mai Phương",
                        "phoneNumber": "+84 987 112 233",
                        "role": "ASSISTANT",
                        "languages": ["Tiếng Việt", "Français"],
                        "licenseNumber": "HDV-ND-10492",
                        "status": "ACTIVE",
                        "rating": 4.8
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/guides", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.guides", hasSize(2)));
        }

        @Test
        @DisplayName("GET /api/excursions/{id}/guides - Lấy danh sách hướng dẫn viên (200 OK)")
        void getGuides_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/guides", seededExcursion.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].fullName").value("Hoàng Hải Đăng"));
        }

        @Test
        @DisplayName("PATCH /api/excursions/{id}/guides/{guideId} - Cập nhật hướng dẫn viên thành công (200 OK)")
        void updateGuide_Success() throws Exception {
            String patchJson = """
                    {
                        "role": "SPECIALIST",
                        "rating": 5.0
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/guides/{guideId}",
                            seededExcursion.getId(), seededGuide.getGuideCode())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            ExcursionGuide updated = guideRepository.findById(seededGuide.getId()).orElseThrow();
            assertThat(updated.getRole()).isEqualTo(GuideRole.SPECIALIST);
            assertThat(updated.getRating()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("DELETE /api/excursions/{id}/guides/{guideId} - Xóa hướng dẫn viên thành công (200 OK)")
        void removeGuide_Success() throws Exception {
            mockMvc.perform(delete("/api/excursions/{id}/guides/{guideId}",
                            seededExcursion.getId(), seededGuide.getGuideCode())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            ExcursionGuide g = guideRepository.findById(seededGuide.getId()).orElseThrow();
            assertThat(g.getActive()).isFalse();
        }
    }
}

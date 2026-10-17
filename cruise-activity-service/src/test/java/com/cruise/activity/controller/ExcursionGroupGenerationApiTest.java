package com.cruise.activity.controller;

import com.cruise.activity.entity.ExcursionGuide;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ExcursionVehicle;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.*;
import com.cruise.activity.repository.ExcursionGuideRepository;
import com.cruise.activity.repository.ExcursionPassengerRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bộ kiểm thử tích hợp API cho Tạo danh sách hành khách theo nhóm (KAN-178)
 * Kiểm thử toàn diện các endpoint:
 * 1. GET /api/excursions/{id}/groups/manifest
 * 2. POST /api/excursions/{id}/groups/auto-generate
 * 3. GET /api/excursions/{id}/groups/export/csv
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Excursion Group Generation & Manifest API Integration Tests (KAN-178)")
class ExcursionGroupGenerationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShoreExcursionRepository excursionRepository;

    @Autowired
    private ExcursionPassengerRepository passengerRepository;

    @Autowired
    private ExcursionVehicleRepository vehicleRepository;

    @Autowired
    private ExcursionGuideRepository guideRepository;

    private ShoreExcursion seededExcursion;
    private ExcursionPassenger seededPassenger1;
    private ExcursionPassenger seededPassenger2;
    private ExcursionVehicle seededVehicle;
    private ExcursionGuide seededGuide;

    @BeforeEach
    void setUp() {
        seededExcursion = ShoreExcursion.builder()
                .tourId(1L)
                .portId(5L)
                .dayNumber(2)
                .title("Tour Khám Phá Rừng Quốc Gia Cát Bà")
                .portName("Cảng Bến Bèo")
                .meetingPoint("Quầy hướng dẫn Cảng Bến Bèo")
                .localProvider("Cat Ba Eco Discovery")
                .providerContact("+84 225 388 7766")
                .excursionDate(LocalDate.now().plusDays(4))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 30))
                .maxCapacity(50)
                .currentParticipants(2)
                .price(BigDecimal.valueOf(950000))
                .currency("VND")
                .isIncluded(false)
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();
        seededExcursion = excursionRepository.save(seededExcursion);

        seededVehicle = ExcursionVehicle.builder()
                .excursionId(seededExcursion.getId())
                .vehicleCode("VEH-01")
                .licensePlate("15B-099.88")
                .vehicleType("Bus 29 Chỗ Huyndai")
                .driverName("Vũ Quốc Tuấn")
                .driverPhone("+84 912 345 678")
                .capacity(29)
                .assignedPassengers(2)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        seededVehicle = vehicleRepository.save(seededVehicle);

        seededGuide = ExcursionGuide.builder()
                .excursionId(seededExcursion.getId())
                .guideCode("GUD-01")
                .fullName("Bùi Thu Trang")
                .phoneNumber("+84 988 777 666")
                .role(GuideRole.LEAD)
                .languages("Tiếng Việt, English")
                .status(GuideStatus.ACTIVE)
                .rating(4.9)
                .active(true)
                .build();
        seededGuide = guideRepository.save(seededGuide);

        seededPassenger1 = ExcursionPassenger.builder()
                .excursionId(seededExcursion.getId())
                .passengerName("Phạm Văn Khang")
                .cabinNumber("State-401")
                .ticketCode("EXC-TKT-CATBA-01")
                .groupName("Đoàn 1")
                .busNumber("VEH-01")
                .emergencyPhone("+84 901 111 222")
                .status(ExcursionPassengerStatus.ON_TOUR)
                .specialRequirements("Ăn chay trường")
                .active(true)
                .build();
        seededPassenger1 = passengerRepository.save(seededPassenger1);

        seededPassenger2 = ExcursionPassenger.builder()
                .excursionId(seededExcursion.getId())
                .passengerName("Đỗ Mỹ Linh")
                .cabinNumber("State-402")
                .ticketCode("EXC-TKT-CATBA-02")
                .groupName("Đoàn 1")
                .busNumber("VEH-01")
                .emergencyPhone("+84 902 333 444")
                .status(ExcursionPassengerStatus.CHECKED_IN)
                .active(true)
                .build();
        seededPassenger2 = passengerRepository.save(seededPassenger2);
    }

    @Nested
    @DisplayName("GET /api/excursions/{id}/groups/manifest")
    class GetManifestTests {

        @Test
        @DisplayName("Lấy danh sách phân nhóm hành khách thành công (200 OK)")
        void getManifest_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/groups/manifest", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.excursionId", is(seededExcursion.getId().intValue())))
                    .andExpect(jsonPath("$.data.totalPassengers", is(2)))
                    .andExpect(jsonPath("$.data.totalGroups", is(1)))
                    .andExpect(jsonPath("$.data.groups", hasSize(1)))
                    .andExpect(jsonPath("$.data.groups[0].groupName", is("Đoàn 1")))
                    .andExpect(jsonPath("$.data.groups[0].busNumber", is("VEH-01")))
                    .andExpect(jsonPath("$.data.groups[0].vehicleInfo.id", is("VEH-01")))
                    .andExpect(jsonPath("$.data.groups[0].guideInfo.id", is("GUD-01")))
                    .andExpect(jsonPath("$.data.groups[0].totalPassengers", is(2)))
                    .andExpect(jsonPath("$.data.groups[0].onTourCount", is(1)))
                    .andExpect(jsonPath("$.data.groups[0].checkedInCount", is(1)));
        }

        @Test
        @DisplayName("Trả về 404 khi không tìm thấy tour bờ")
        void getManifest_NotFound() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/groups/manifest", 99999L)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/excursions/{id}/groups/auto-generate")
    class AutoGenerateTests {

        @Test
        @DisplayName("Tự động sinh và phân bổ nhóm hành khách thành công (200 OK)")
        void autoGenerateGroups_Success() throws Exception {
            // Thêm một khách chưa có nhóm
            ExcursionPassenger unassigned = ExcursionPassenger.builder()
                    .excursionId(seededExcursion.getId())
                    .passengerName("Lê Văn Mới")
                    .cabinNumber("State-403")
                    .ticketCode("EXC-TKT-CATBA-03")
                    .status(ExcursionPassengerStatus.REGISTERED)
                    .active(true)
                    .build();
            passengerRepository.save(unassigned);

            String requestBody = """
                    {
                        "targetGroupCount": 2,
                        "groupPrefix": "Tổ ",
                        "autoAssignVehicles": true
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/groups/auto-generate", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.totalPassengers", is(3)))
                    .andExpect(jsonPath("$.data.totalGroups", is(2)));
        }

        @Test
        @DisplayName("Tự động sinh nhóm khi không truyền body (sử dụng cấu hình mặc định)")
        void autoGenerateGroups_EmptyBody_Success() throws Exception {
            mockMvc.perform(post("/api/excursions/{id}/groups/auto-generate", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.totalPassengers", is(2)));
        }
    }

    @Nested
    @DisplayName("GET /api/excursions/{id}/groups/export/csv")
    class ExportCsvTests {

        @Test
        @DisplayName("Xuất danh sách phân nhóm hành khách ra CSV thành công (200 OK)")
        void exportCsv_All_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/groups/export/csv", seededExcursion.getId()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                    .andExpect(header().string("Content-Disposition", containsString("excursion_" + seededExcursion.getId() + "_passenger_groups.csv")))
                    .andExpect(result -> {
                        byte[] contentBytes = result.getResponse().getContentAsByteArray();
                        String content = new String(contentBytes, StandardCharsets.UTF_8);
                        assertThat(content.charAt(0)).isEqualTo('\ufeff');
                        assertThat(content).contains("STT,Đoàn / Nhóm,Xe vận chuyển,Mã vé,Họ và tên,Số cabin,Trạng thái");
                        assertThat(content).contains("Phạm Văn Khang");
                        assertThat(content).contains("Đỗ Mỹ Linh");
                    });
        }

        @Test
        @DisplayName("Xuất danh sách theo bộ lọc groupName cụ thể")
        void exportCsv_FilteredByGroup_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/groups/export/csv", seededExcursion.getId())
                            .param("groupName", "Đoàn 1"))
                    .andExpect(status().isOk())
                    .andExpect(result -> {
                        String content = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
                        assertThat(content).contains("Phạm Văn Khang");
                        assertThat(content).contains("Đỗ Mỹ Linh");
                    });
        }
    }
}

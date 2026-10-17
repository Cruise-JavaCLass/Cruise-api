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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bộ kiểm thử tích hợp API toàn diện cho Quản lý nhóm tour bờ (KAN-180 - Write API Tests for Group Management)
 * Kiểm thử chi tiết các quy trình API:
 * 1. POST /api/excursions/{id}/passengers - Đăng ký hành khách vào tour
 * 2. GET /api/excursions/{id}/passengers - Tra cứu danh sách hành khách theo tour
 * 3. PATCH /api/excursions/{id}/passengers/{passengerId}/group - Gán hành khách vào nhóm/xe cụ thể
 * 4. PATCH /api/excursions/{id}/passengers/bulk-group - Gán đoàn và phương tiện hàng loạt
 * 5. DELETE /api/excursions/{id}/passengers/{passengerId} - Rút/xóa hành khách khỏi tour và tự động cập nhật sức chứa
 * 6. POST /api/excursions/{id}/vehicles & GET/PATCH/DELETE - Quản lý xe chuyên chở của tour bờ
 * 7. POST /api/excursions/{id}/guides & GET/PATCH/DELETE - Quản lý hướng dẫn viên phân bổ theo tour bờ
 * 8. GET /api/excursions/{id}/groups/manifest - Báo cáo tổng hợp phân nhóm & điểm danh đoàn
 * 9. POST /api/excursions/{id}/groups/auto-generate - Tự động sinh nhóm và phân bổ hành khách
 * 10. GET /api/excursions/{id}/groups/export/csv - Xuất danh sách đoàn dạng CSV
 * 11. Edge cases, Validation và Business Rule Error Handling
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Excursion Group Management API Tests (KAN-180)")
class ExcursionGroupManagementApiTest {

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

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private ShoreExcursion seededExcursion;
    private ExcursionPassenger passenger1;
    private ExcursionPassenger passenger2;
    private ExcursionPassenger passenger3;
    private ExcursionVehicle vehicle1;
    private ExcursionVehicle vehicle2;
    private ExcursionGuide guide1;

    @BeforeEach
    void setUp() {
        // Khởi tạo Tour bờ chuẩn
        seededExcursion = ShoreExcursion.builder()
                .tourId(10L)
                .portId(20L)
                .dayNumber(3)
                .title("Tour Thám Hiểm Hang Đầu Gỗ & Hòn Đỉnh Hương")
                .portName("Cảng Quốc Tế Hạ Long")
                .meetingPoint("Cầu Cảng Số 1")
                .localProvider("Quảng Ninh Travel JSC")
                .providerContact("+84 203 388 9999")
                .excursionDate(LocalDate.now().plusDays(2))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 30))
                .maxCapacity(30)
                .currentParticipants(3)
                .price(BigDecimal.valueOf(800000))
                .currency("VND")
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();
        seededExcursion = excursionRepository.save(seededExcursion);

        // Khởi tạo 2 Phương tiện xe/tàu
        vehicle1 = ExcursionVehicle.builder()
                .excursionId(seededExcursion.getId())
                .vehicleCode("BUS-01")
                .licensePlate("14B-011.22")
                .vehicleType("Bus 29 Chỗ Universe")
                .driverName("Nguyễn Đức Thắng")
                .driverPhone("+84 912 345 678")
                .capacity(29)
                .assignedPassengers(2)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        vehicle1 = vehicleRepository.save(vehicle1);

        vehicle2 = ExcursionVehicle.builder()
                .excursionId(seededExcursion.getId())
                .vehicleCode("BUS-02")
                .licensePlate("14B-033.44")
                .vehicleType("Bus 16 Chỗ Ford Transit")
                .driverName("Trần Văn Bình")
                .driverPhone("+84 934 567 890")
                .capacity(16)
                .assignedPassengers(0)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        vehicle2 = vehicleRepository.save(vehicle2);

        // Khởi tạo Hướng dẫn viên
        guide1 = ExcursionGuide.builder()
                .excursionId(seededExcursion.getId())
                .guideCode("GUD-01")
                .fullName("Phạm Minh Hoàng")
                .phoneNumber("+84 987 654 321")
                .role(GuideRole.LEAD)
                .languages("Tiếng Việt, English")
                .status(GuideStatus.ACTIVE)
                .rating(4.8)
                .active(true)
                .build();
        guide1 = guideRepository.save(guide1);

        // Khởi tạo 3 Hành khách tham gia tour
        passenger1 = ExcursionPassenger.builder()
                .excursionId(seededExcursion.getId())
                .passengerName("Hoàng Kim Oanh")
                .cabinNumber("State-301")
                .ticketCode("TKT-EXC-001")
                .groupName("Đoàn 1")
                .busNumber("BUS-01")
                .emergencyPhone("+84 901 234 567")
                .status(ExcursionPassengerStatus.REGISTERED)
                .active(true)
                .build();
        passenger1 = passengerRepository.save(passenger1);

        passenger2 = ExcursionPassenger.builder()
                .excursionId(seededExcursion.getId())
                .passengerName("Lê Tuấn Kiệt")
                .cabinNumber("State-302")
                .ticketCode("TKT-EXC-002")
                .groupName("Đoàn 1")
                .busNumber("BUS-01")
                .emergencyPhone("+84 902 345 678")
                .status(ExcursionPassengerStatus.CHECKED_IN)
                .active(true)
                .build();
        passenger2 = passengerRepository.save(passenger2);

        passenger3 = ExcursionPassenger.builder()
                .excursionId(seededExcursion.getId())
                .passengerName("Vũ Mai Phương")
                .cabinNumber("State-303")
                .ticketCode("TKT-EXC-003")
                .emergencyPhone("+84 903 456 789")
                .status(ExcursionPassengerStatus.REGISTERED)
                .active(true)
                .build();
        passenger3 = passengerRepository.save(passenger3);
    }

    // =========================================================================
    // 1. Quản lý hành khách & Phân nhóm (Passenger & Group Assignment)
    // =========================================================================
    @Nested
    @DisplayName("1. Passenger Group Assignment Workflow")
    class PassengerAssignmentWorkflowTests {

        @Test
        @DisplayName("POST /api/excursions/{id}/passengers: Thêm hành khách vào tour thành công (201 Created)")
        void addPassengerToExcursion_Success() throws Exception {
            String requestJson = """
                    {
                        "passengerName": "Trần Hải Đăng",
                        "cabinNumber": "State-505",
                        "ticketCode": "TKT-EXC-004",
                        "groupName": "Đoàn 2",
                        "busNumber": "BUS-02",
                        "emergencyPhone": "+84 909 999 888",
                        "specialRequirements": "Cần hỗ trợ người cao tuổi"
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/passengers", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.currentParticipants", is(4)));

            List<ExcursionPassenger> passengers = passengerRepository.findByExcursionIdAndActiveTrue(seededExcursion.getId());
            assertThat(passengers).hasSize(4);
            assertThat(passengers).extracting(ExcursionPassenger::getPassengerName).contains("Trần Hải Đăng");
        }

        @Test
        @DisplayName("POST /api/excursions/{id}/passengers: Báo lỗi 400 khi mã vé bị trùng lặp")
        void addPassengerToExcursion_DuplicateTicket_BadRequest() throws Exception {
            String requestJson = """
                    {
                        "passengerName": "Khách Trùng Vé",
                        "cabinNumber": "State-999",
                        "ticketCode": "TKT-EXC-001"
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/passengers", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", containsString("TKT-EXC-001")));
        }

        @Test
        @DisplayName("GET /api/excursions/{id}/passengers: Lấy danh sách hành khách của tour bờ")
        void getExcursionPassengers_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/passengers", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data", hasSize(3)))
                    .andExpect(jsonPath("$.data[*].passengerName", hasItem("Hoàng Kim Oanh")))
                    .andExpect(jsonPath("$.data[*].groupName", hasItem("Đoàn 1")));
        }

        @Test
        @DisplayName("PATCH /api/excursions/{id}/passengers/{passengerId}/group: Gán nhóm và xe cho từng hành khách")
        void assignPassengerToGroup_Success() throws Exception {
            String requestJson = """
                    {
                        "groupName": "Đoàn VIP 1",
                        "busNumber": "BUS-02"
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/passengers/{passengerId}/group",
                            seededExcursion.getId(), passenger3.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));

            flushAndClear();
            ExcursionPassenger updated = passengerRepository.findById(passenger3.getId()).orElseThrow();
            assertThat(updated.getGroupName()).isEqualTo("Đoàn VIP 1");
            assertThat(updated.getBusNumber()).isEqualTo("BUS-02");
        }

        @Test
        @DisplayName("PATCH /api/excursions/{id}/passengers/bulk-group: Gán nhóm và xe hàng loạt cho nhiều hành khách")
        void bulkAssignPassengersToGroup_Success() throws Exception {
            String requestJson = String.format("""
                    {
                        "passengerIds": [%d, %d],
                        "groupName": "Đoàn 2 - Tour Chiều",
                        "busNumber": "BUS-02"
                    }
                    """, passenger1.getId(), passenger3.getId());

            mockMvc.perform(patch("/api/excursions/{id}/passengers/bulk-group", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));

            flushAndClear();
            ExcursionPassenger p1 = passengerRepository.findById(passenger1.getId()).orElseThrow();
            ExcursionPassenger p3 = passengerRepository.findById(passenger3.getId()).orElseThrow();
            assertThat(p1.getGroupName()).isEqualTo("Đoàn 2 - Tour Chiều");
            assertThat(p1.getBusNumber()).isEqualTo("BUS-02");
            assertThat(p3.getGroupName()).isEqualTo("Đoàn 2 - Tour Chiều");
            assertThat(p3.getBusNumber()).isEqualTo("BUS-02");
        }

        @Test
        @DisplayName("DELETE /api/excursions/{id}/passengers/{passengerId}: Xóa hành khách khỏi tour bờ và giảm số lượng người")
        void removePassengerFromExcursion_Success() throws Exception {
            mockMvc.perform(delete("/api/excursions/{id}/passengers/{passengerId}",
                            seededExcursion.getId(), passenger3.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.currentParticipants", is(2)));

            flushAndClear();
            ExcursionPassenger deleted = passengerRepository.findById(passenger3.getId()).orElseThrow();
            assertThat(deleted.getActive()).isFalse();
        }

        @Test
        @DisplayName("DELETE /api/excursions/{id}/passengers/{passengerId}: Trả về 404 khi xóa hành khách không tồn tại")
        void removePassenger_NotFound() throws Exception {
            mockMvc.perform(delete("/api/excursions/{id}/passengers/{passengerId}",
                            seededExcursion.getId(), 999999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)));
        }
    }

    // =========================================================================
    // 2. Quản lý Logistics (Phương tiện & Hướng dẫn viên)
    // =========================================================================
    @Nested
    @DisplayName("2. Vehicle and Guide Logistics Management")
    class LogisticsManagementTests {

        @Test
        @DisplayName("GET /api/excursions/{id}/vehicles: Lấy danh sách xe chuyên chở của tour")
        void getVehicles_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/vehicles", seededExcursion.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[*].id", containsInAnyOrder("BUS-01", "BUS-02")));
        }

        @Test
        @DisplayName("POST /api/excursions/{id}/vehicles: Thêm phương tiện vận chuyển mới")
        void addVehicle_Success() throws Exception {
            String requestJson = """
                    {
                        "licensePlate": "14B-055.66",
                        "vehicleType": "Bus 45 Chỗ Universe",
                        "capacity": 45,
                        "driverName": "Lý Văn Nam",
                        "driverPhone": "+84 944 333 222",
                        "status": "READY"
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/vehicles", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.vehicles", hasSize(3)));

            flushAndClear();
            List<ExcursionVehicle> vehicles = vehicleRepository.findByExcursionIdAndActiveTrue(seededExcursion.getId());
            assertThat(vehicles).hasSize(3);
            assertThat(vehicles).extracting(ExcursionVehicle::getLicensePlate).contains("14B-055.66");
        }

        @Test
        @DisplayName("PATCH /api/excursions/{id}/vehicles/{vehicleId}: Cập nhật tài xế & trạng thái xe")
        void updateVehicle_Success() throws Exception {
            String requestJson = """
                    {
                        "driverName": "Nguyễn Đức Thắng (Đổi ca)",
                        "driverPhone": "+84 912 999 888",
                        "status": "ON_TRIP"
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/vehicles/{vehicleId}", seededExcursion.getId(), "BUS-01")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));

            flushAndClear();
            ExcursionVehicle updated = vehicleRepository.findByVehicleCodeAndExcursionIdAndActiveTrue("BUS-01", seededExcursion.getId()).orElseThrow();
            assertThat(updated.getDriverName()).isEqualTo("Nguyễn Đức Thắng (Đổi ca)");
            assertThat(updated.getStatus()).isEqualTo(VehicleStatus.ON_TRIP);
        }

        @Test
        @DisplayName("DELETE /api/excursions/{id}/vehicles/{vehicleId}: Xóa xe thành công")
        void removeVehicle_Success() throws Exception {
            mockMvc.perform(delete("/api/excursions/{id}/vehicles/{vehicleId}", seededExcursion.getId(), "BUS-02"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));

            flushAndClear();
            assertThat(vehicleRepository.findByVehicleCodeAndExcursionIdAndActiveTrue("BUS-02", seededExcursion.getId()))
                    .isEmpty();
        }

        @Test
        @DisplayName("GET /api/excursions/{id}/guides: Lấy danh sách hướng dẫn viên của tour")
        void getGuides_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/guides", seededExcursion.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].id", is("GUD-01")))
                    .andExpect(jsonPath("$.data[0].fullName", is("Phạm Minh Hoàng")));
        }

        @Test
        @DisplayName("POST /api/excursions/{id}/guides: Thêm hướng dẫn viên mới")
        void addGuide_Success() throws Exception {
            String requestJson = """
                    {
                        "fullName": "Đỗ Thu Trang",
                        "phoneNumber": "+84 977 112 233",
                        "role": "ASSISTANT",
                        "languages": ["Tiếng Việt", "French"]
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/guides", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.guides", hasSize(2)));

            flushAndClear();
            List<ExcursionGuide> guides = guideRepository.findByExcursionIdAndActiveTrue(seededExcursion.getId());
            assertThat(guides).hasSize(2);
            assertThat(guides).extracting(ExcursionGuide::getFullName).contains("Đỗ Thu Trang");
        }

        @Test
        @DisplayName("PATCH /api/excursions/{id}/guides/{guideId}: Cập nhật thông tin hướng dẫn viên")
        void updateGuide_Success() throws Exception {
            String requestJson = """
                    {
                        "role": "LEAD",
                        "languages": ["Tiếng Việt", "English", "Chinese"],
                        "rating": 5.0
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/guides/{guideId}", seededExcursion.getId(), "GUD-01")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));

            flushAndClear();
            ExcursionGuide updated = guideRepository.findByGuideCodeAndExcursionIdAndActiveTrue("GUD-01", seededExcursion.getId()).orElseThrow();
            assertThat(updated.getLanguages()).contains("English");
            assertThat(updated.getRating()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("DELETE /api/excursions/{id}/guides/{guideId}: Xóa hướng dẫn viên")
        void removeGuide_Success() throws Exception {
            mockMvc.perform(delete("/api/excursions/{id}/guides/{guideId}", seededExcursion.getId(), "GUD-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));

            flushAndClear();
            assertThat(guideRepository.findByGuideCodeAndExcursionIdAndActiveTrue("GUD-01", seededExcursion.getId()))
                    .isEmpty();
        }
    }

    // =========================================================================
    // 3. Manifest Báo cáo, Tự động chia nhóm & Xuất CSV (Manifest, Auto-Generate, Export)
    // =========================================================================
    @Nested
    @DisplayName("3. Manifest, Auto-Generation and Export")
    class ManifestAndExportTests {

        @Test
        @DisplayName("GET /api/excursions/{id}/groups/manifest: Báo cáo danh sách đoàn, xe và hướng dẫn viên")
        void getManifest_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/groups/manifest", seededExcursion.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.excursionId", is(seededExcursion.getId().intValue())))
                    .andExpect(jsonPath("$.data.totalPassengers", is(3)))
                    .andExpect(jsonPath("$.data.totalGroups", is(1))) // Chỉ có 1 nhóm có tên chính thức là "Đoàn 1", nhóm chưa phân tên không tính vào totalGroups
                    .andExpect(jsonPath("$.data.groups", hasSize(2)));
        }

        @Test
        @DisplayName("POST /api/excursions/{id}/groups/auto-generate: Tự động chia đều hành khách vào các nhóm/xe")
        void autoGenerateGroups_Success() throws Exception {
            String requestJson = """
                    {
                        "targetGroupCount": 2,
                        "groupPrefix": "Tổ ",
                        "autoAssignVehicles": true
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/groups/auto-generate", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.totalPassengers", is(3)))
                    .andExpect(jsonPath("$.data.totalGroups", is(2)))
                    .andExpect(jsonPath("$.data.groups[0].groupName", startsWith("Tổ ")))
                    .andExpect(jsonPath("$.data.groups[0].busNumber", notNullValue()))
                    .andExpect(jsonPath("$.data.groups[*].passengers[?(@.ticketCode == 'TKT-EXC-003')].busNumber", hasItem(notNullValue())));
        }

        @Test
        @DisplayName("GET /api/excursions/{id}/groups/export/csv: Xuất toàn bộ danh sách phân nhóm ra file CSV chuẩn UTF-8 BOM")
        void exportCsv_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/groups/export/csv", seededExcursion.getId()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                    .andExpect(header().string("Content-Disposition", containsString("excursion_" + seededExcursion.getId() + "_passenger_groups.csv")))
                    .andExpect(result -> {
                        byte[] bytes = result.getResponse().getContentAsByteArray();
                        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        assertThat(content.charAt(0)).isEqualTo('\ufeff');
                        assertThat(content).contains("STT,Đoàn / Nhóm,Xe vận chuyển,Mã vé,Họ và tên,Số cabin,Trạng thái");
                        assertThat(content).contains("Hoàng Kim Oanh");
                        assertThat(content).contains("Lê Tuấn Kiệt");
                    });
        }
    }

    // =========================================================================
    // 4. Edge Cases & Exception Handling
    // =========================================================================
    @Nested
    @DisplayName("4. Edge Cases & Exception Handling")
    class EdgeCasesAndExceptionTests {

        @Test
        @DisplayName("Trả về 404 khi truy vấn tour không tồn tại")
        void excursionNotFound_Returns404() throws Exception {
            mockMvc.perform(get("/api/excursions/999999/groups/manifest"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)));
        }

        @Test
        @DisplayName("Trả về 400 khi gán nhóm cho hành khách với dữ liệu trống")
        void assignGroup_BlankData_ReturnsBadRequest() throws Exception {
            String invalidRequest = """
                    {
                        "groupName": ""
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/passengers/{passengerId}/group",
                            seededExcursion.getId(), passenger1.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)));
        }

        @Test
        @DisplayName("Trả về 400 khi thêm xe thiếu mã hoặc biển số")
        void addVehicle_MissingRequiredFields_ReturnsBadRequest() throws Exception {
            String invalidVehicle = """
                    {
                        "vehicleCode": "",
                        "capacity": -5
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/vehicles", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidVehicle))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)));
        }

        @Test
        @DisplayName("Trả về 400 khi thêm hướng dẫn viên thiếu tên hoặc số điện thoại")
        void addGuide_MissingRequiredFields_ReturnsBadRequest() throws Exception {
            String invalidGuide = """
                    {
                        "guideCode": "GUD-99",
                        "fullName": ""
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/guides", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidGuide))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)));
        }
    }
}

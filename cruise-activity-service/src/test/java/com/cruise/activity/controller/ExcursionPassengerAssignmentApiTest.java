package com.cruise.activity.controller;

import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.repository.ExcursionPassengerRepository;
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
 * Bộ kiểm thử tích hợp API cho việc Gán hành khách vào nhóm tour bờ (KAN-176)
 * Kiểm thử toàn diện các endpoint:
 * 1. POST /api/excursions/{id}/passengers - Thêm hành khách vào tour bờ
 * 2. GET /api/excursions/{id}/passengers - Lấy danh sách hành khách tour bờ
 * 3. PATCH /api/excursions/{id}/passengers/{passengerId}/group - Gán đoàn & xe cho từng khách
 * 4. PATCH /api/excursions/{id}/passengers/bulk-group - Gán đoàn & xe hàng loạt
 * 5. DELETE /api/excursions/{id}/passengers/{passengerId} - Xóa hành khách khỏi tour bờ
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Excursion Passenger Assignment API Integration Tests (KAN-176)")
class ExcursionPassengerAssignmentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShoreExcursionRepository excursionRepository;

    @Autowired
    private ExcursionPassengerRepository passengerRepository;

    private ShoreExcursion seededExcursion;
    private ExcursionPassenger seededPassenger1;
    private ExcursionPassenger seededPassenger2;

    @BeforeEach
    void setUp() {
        seededExcursion = ShoreExcursion.builder()
                .tourId(1L)
                .portId(2L)
                .dayNumber(1)
                .title("Tour Thám Hiểm Hang Đầu Gỗ")
                .portName("Cảng Quốc Tế Tuần Châu")
                .meetingPoint("Cầu cảng số 3")
                .localProvider("Halong Heritage Travel Co.")
                .providerContact("+84 203 384 6688")
                .excursionDate(LocalDate.now().plusDays(3))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 0))
                .maxCapacity(15)
                .currentParticipants(2)
                .price(BigDecimal.valueOf(750000))
                .childPrice(BigDecimal.valueOf(400000))
                .currency("VND")
                .isIncluded(false)
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();
        seededExcursion = excursionRepository.save(seededExcursion);

        seededPassenger1 = ExcursionPassenger.builder()
                .excursionId(seededExcursion.getId())
                .passengerName("Nguyễn Văn An")
                .cabinNumber("Suite-801")
                .ticketCode("EXC-TKT-" + seededExcursion.getId() + "-001")
                .groupName("Đoàn A - Khám phá")
                .busNumber("XE-01 (45 Chỗ)")
                .emergencyPhone("+84 912 345 678")
                .status(ExcursionPassengerStatus.REGISTERED)
                .active(true)
                .build();
        seededPassenger1 = passengerRepository.save(seededPassenger1);

        seededPassenger2 = ExcursionPassenger.builder()
                .excursionId(seededExcursion.getId())
                .passengerName("Trần Thị Mai")
                .cabinNumber("Balcony-504")
                .ticketCode("EXC-TKT-" + seededExcursion.getId() + "-002")
                .groupName("Đoàn A - Khám phá")
                .busNumber("XE-01 (45 Chỗ)")
                .emergencyPhone("+84 988 765 432")
                .status(ExcursionPassengerStatus.CHECKED_IN)
                .active(true)
                .build();
        seededPassenger2 = passengerRepository.save(seededPassenger2);
    }

    @Nested
    @DisplayName("1. POST /api/excursions/{id}/passengers - Thêm hành khách vào tour bờ")
    class AddPassengerApiTests {

        @Test
        @DisplayName("Thêm hành khách thành công, trả về 201 Created và cập nhật số người tham gia")
        void addPassenger_Success() throws Exception {
            String json = """
                    {
                        "passengerName": "Lê Hoàng Long",
                        "cabinNumber": "Ocean-312",
                        "groupName": "Đoàn B - Ẩm thực",
                        "busNumber": "XE-02 (29 Chỗ)",
                        "emergencyPhone": "+84 903 112 233",
                        "specialRequirements": "Dị ứng hải sản"
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/passengers", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.currentParticipants").value(3))
                    .andExpect(jsonPath("$.data.passengers", hasSize(3)));

            List<ExcursionPassenger> list = passengerRepository.findByExcursionIdAndActiveTrue(seededExcursion.getId());
            assertThat(list).hasSize(3);
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi tên hành khách hoặc số cabin để trống")
        void addPassenger_ValidationFailure() throws Exception {
            String invalidJson = """
                    {
                        "passengerName": "",
                        "cabinNumber": ""
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/passengers", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi tour đã đầy sức chứa (capacity exceeded)")
        void addPassenger_ExceedCapacity_Fails() throws Exception {
            seededExcursion.setCurrentParticipants(15);
            seededExcursion.setMaxCapacity(15);
            excursionRepository.save(seededExcursion);

            String json = """
                    {
                        "passengerName": "Khách Vượt Quá",
                        "cabinNumber": "Suite-999"
                    }
                    """;

            mockMvc.perform(post("/api/excursions/{id}/passengers", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Tour đã đạt sức chứa tối đa")));
        }
    }

    @Nested
    @DisplayName("2. GET /api/excursions/{id}/passengers - Lấy danh sách hành khách tour bờ")
    class GetPassengersApiTests {

        @Test
        @DisplayName("Lấy danh sách hành khách theo tour bờ thành công (200 OK)")
        void getPassengers_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/passengers", seededExcursion.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].passengerName").value("Nguyễn Văn An"))
                    .andExpect(jsonPath("$.data[1].passengerName").value("Trần Thị Mai"));
        }

        @Test
        @DisplayName("Trả về 404 Not Found khi excursion ID không tồn tại")
        void getPassengers_NotFound() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/passengers", 99999L)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("3. PATCH /api/excursions/{id}/passengers/{passengerId}/group - Gán đoàn & xe cho từng khách")
    class AssignSinglePassengerGroupApiTests {

        @Test
        @DisplayName("Gán đoàn và xe thành công (200 OK)")
        void assignGroup_Success() throws Exception {
            String patchJson = """
                    {
                        "groupName": "Đoàn VIP - Cao cấp",
                        "busNumber": "BUS-VIP-01 (16 Chỗ)"
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/passengers/{passengerId}/group",
                            seededExcursion.getId(), seededPassenger1.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            ExcursionPassenger updated = passengerRepository.findById(seededPassenger1.getId()).orElseThrow();
            assertThat(updated.getGroupName()).isEqualTo("Đoàn VIP - Cao cấp");
            assertThat(updated.getBusNumber()).isEqualTo("BUS-VIP-01 (16 Chỗ)");
        }

        @Test
        @DisplayName("Trả về 404 Not Found khi ID hành khách không thuộc tour bờ")
        void assignGroup_PassengerNotFound() throws Exception {
            String patchJson = """
                    {
                        "groupName": "Đoàn C"
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/passengers/{passengerId}/group",
                            seededExcursion.getId(), 88888L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("4. PATCH /api/excursions/{id}/passengers/bulk-group - Gán đoàn & xe hàng loạt")
    class BulkAssignPassengersGroupApiTests {

        @Test
        @DisplayName("Gán đoàn và xe hàng loạt thành công cho nhiều hành khách (200 OK)")
        void bulkAssignGroup_Success() throws Exception {
            String bulkJson = String.format("""
                    {
                        "passengerIds": [%d, %d],
                        "groupName": "Đoàn Thuyền Trung Chuyển",
                        "busNumber": "Tàu trung chuyển TENDER-01"
                    }
                    """, seededPassenger1.getId(), seededPassenger2.getId());

            mockMvc.perform(patch("/api/excursions/{id}/passengers/bulk-group", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bulkJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            ExcursionPassenger p1 = passengerRepository.findById(seededPassenger1.getId()).orElseThrow();
            ExcursionPassenger p2 = passengerRepository.findById(seededPassenger2.getId()).orElseThrow();
            assertThat(p1.getGroupName()).isEqualTo("Đoàn Thuyền Trung Chuyển");
            assertThat(p1.getBusNumber()).isEqualTo("Tàu trung chuyển TENDER-01");
            assertThat(p2.getGroupName()).isEqualTo("Đoàn Thuyền Trung Chuyển");
            assertThat(p2.getBusNumber()).isEqualTo("Tàu trung chuyển TENDER-01");
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi danh sách passengerIds rỗng")
        void bulkAssignGroup_EmptyIds_Fails() throws Exception {
            String emptyJson = """
                    {
                        "passengerIds": [],
                        "groupName": "Đoàn C"
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/passengers/bulk-group", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(emptyJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("5. DELETE /api/excursions/{id}/passengers/{passengerId} - Xóa hành khách")
    class RemovePassengerApiTests {

        @Test
        @DisplayName("Xóa hành khách thành công, giảm currentParticipants và chuyển active=false")
        void removePassenger_Success() throws Exception {
            mockMvc.perform(delete("/api/excursions/{id}/passengers/{passengerId}",
                            seededExcursion.getId(), seededPassenger1.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.currentParticipants").value(1));

            ExcursionPassenger p = passengerRepository.findById(seededPassenger1.getId()).orElseThrow();
            assertThat(p.getActive()).isFalse();

            ShoreExcursion exc = excursionRepository.findById(seededExcursion.getId()).orElseThrow();
            assertThat(exc.getCurrentParticipants()).isEqualTo(1);
        }

        @Test
        @DisplayName("Trả về 404 Not Found khi xóa hành khách không tồn tại")
        void removePassenger_NotFound() throws Exception {
            mockMvc.perform(delete("/api/excursions/{id}/passengers/{passengerId}",
                            seededExcursion.getId(), 77777L)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}

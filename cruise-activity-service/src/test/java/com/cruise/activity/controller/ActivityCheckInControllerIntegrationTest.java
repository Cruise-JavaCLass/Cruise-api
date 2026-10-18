package com.cruise.activity.controller;

import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.repository.ActivityAttendanceRepository;
import com.cruise.activity.repository.ActivityCheckInLogRepository;
import com.cruise.activity.repository.OnboardActivityRepository;
import com.cruise.activity.repository.PassengerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Activity Check-in & Attendance Controller Integration Tests (KAN-223)")
class ActivityCheckInControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OnboardActivityRepository activityRepository;

    @Autowired
    private PassengerRepository passengerRepository;

    @Autowired
    private ActivityAttendanceRepository attendanceRepository;

    @Autowired
    private ActivityCheckInLogRepository checkInLogRepository;

    private OnboardActivity activity;
    private Passenger passenger1;
    private Passenger passenger2;

    @BeforeEach
    void setUp() {
        checkInLogRepository.deleteAll();
        attendanceRepository.deleteAll();
        activityRepository.deleteAll();
        passengerRepository.deleteAll();

        activity = activityRepository.save(OnboardActivity.builder()
                .tourId(1L)
                .dayNumber(1)
                .title("Gala Dinner & Live Acoustic Music")
                .description("Dạ tiệc chào mừng và biểu diễn nhạc acoustic tại Venetian Ballroom")
                .category("Dining")
                .location("Venetian Ballroom (Deck 5)")
                .activityDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(19, 30))
                .endTime(LocalTime.of(22, 0))
                .maxCapacity(100)
                .currentParticipants(0)
                .price(BigDecimal.valueOf(150000))
                .isIncluded(false)
                .status(ActivityStatus.SCHEDULED)
                .active(true)
                .build());

        passenger1 = passengerRepository.save(Passenger.builder()
                .tourId(1L)
                .fullName("Hoàng Đức Hải")
                .email("hai.hoang@example.com")
                .phoneNumber("0988776655")
                .cabinNumber("Suite-1102")
                .rfidTag("RFID-CARD-1102")
                .qrCode("QR-PASS-HAI")
                .active(true)
                .build());

        passenger2 = passengerRepository.save(Passenger.builder()
                .tourId(1L)
                .fullName("Trần Thị Mai")
                .email("mai.tran@example.com")
                .phoneNumber("0977665544")
                .cabinNumber("Balcony-804")
                .rfidTag("RFID-CARD-804")
                .qrCode("QR-PASS-MAI")
                .active(true)
                .build());

        activityRepository.flush();
        passengerRepository.flush();
    }

    @Test
    @DisplayName("GET /api/activities/{id}/passengers/{passengerId}/qr-pass - Sinh vé QR Pass thành công")
    void testGenerateQrPassApiSuccess() throws Exception {
        mockMvc.perform(get("/api/activities/{id}/passengers/{passengerId}/qr-pass", activity.getId(), passenger1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activityId").value(activity.getId()))
                .andExpect(jsonPath("$.data.passengerId").value(passenger1.getId()))
                .andExpect(jsonPath("$.data.passengerName").value("Hoàng Đức Hải"))
                .andExpect(jsonPath("$.data.cabinNumber").value("Suite-1102"))
                .andExpect(jsonPath("$.data.deck").value("Deck 5"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/activities/{id}/check-in - Check-in qua QR Token và chống quét trùng lặp (409 Conflict)")
    void testCheckInWithQrTokenAndDuplicatePrevention() throws Exception {
        // 1. Lấy QR Pass
        MvcResult passResult = mockMvc.perform(get("/api/activities/{id}/passengers/{passengerId}/qr-pass", activity.getId(), passenger1.getId()))
                .andExpect(status().isOk())
                .andReturn();

        String passResponseStr = passResult.getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(passResponseStr);
        String qrToken = m.find() ? m.group(1) : "";

        // 2. Check-in lần 1 -> 200 OK
        String checkInJson1 = String.format("""
                {
                    "qrToken": "%s",
                    "staffId": "STAFF-01",
                    "staffName": "Lê Văn Soát Vé",
                    "deviceId": "GATE-SCANNER-01"
                }
                """, qrToken);

        mockMvc.perform(post("/api/activities/{id}/check-in", activity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInJson1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.data.passengerName").value("Hoàng Đức Hải"))
                .andExpect(jsonPath("$.data.method").value("QR_CODE"));

        // 3. Quét lại lần 2 -> 409 Conflict
        mockMvc.perform(post("/api/activities/{id}/check-in", activity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInJson1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("đã check-in lúc")));
    }

    @Test
    @DisplayName("POST /api/activities/{id}/check-in - Check-in bằng thẻ RFID thành công")
    void testCheckInWithRfidApiSuccess() throws Exception {
        String rfidJson = String.format("""
                {
                    "rfidTag": "%s",
                    "staffId": "STAFF-02",
                    "staffName": "Trần Văn Bảo Vệ"
                }
                """, passenger2.getRfidTag());

        mockMvc.perform(post("/api/activities/{id}/check-in", activity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rfidJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.data.passengerName").value("Trần Thị Mai"))
                .andExpect(jsonPath("$.data.method").value("RFID"));
    }

    @Test
    @DisplayName("POST /api/activities/{id}/check-in/walk-in - Tiếp nhận khách vãng lai và tính phí")
    void testWalkInCheckInApiSuccess() throws Exception {
        String walkInJson = String.format("""
                {
                    "passengerId": %d,
                    "staffId": "STAFF-WALKIN",
                    "note": "Khách đến đột xuất"
                }
                """, passenger1.getId());

        mockMvc.perform(post("/api/activities/{id}/check-in/walk-in", activity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(walkInJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isWalkIn").value(true))
                .andExpect(jsonPath("$.data.chargedAmount").value(150000.0))
                .andExpect(jsonPath("$.data.transactionId").value("PENDING_FOLIO_BILLING"))
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"));
    }

    @Test
    @DisplayName("GET /api/activities/{id}/attendance/summary - Thống kê realtime số liệu sự kiện")
    void testGetAttendanceSummaryApi() throws Exception {
        // Thực hiện check in cho passenger 1
        String checkInJson = String.format("""
                {
                    "passengerId": %d,
                    "staffId": "STAFF-01"
                }
                """, passenger1.getId());

        mockMvc.perform(post("/api/activities/{id}/check-in", activity.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkInJson));

        mockMvc.perform(get("/api/activities/{id}/attendance/summary", activity.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activityId").value(activity.getId()))
                .andExpect(jsonPath("$.data.maxCapacity").value(100))
                .andExpect(jsonPath("$.data.checkedInCount").value(1))
                .andExpect(jsonPath("$.data.remainingCapacity").value(99))
                .andExpect(jsonPath("$.data.attendanceRate").value(1.0));
    }

    @Test
    @DisplayName("GET /api/activities/{id}/attendance/roster - Lấy danh sách điểm danh phân trang")
    void testGetActivityRosterApi() throws Exception {
        // Check in
        String checkInJson = String.format("""
                {
                    "passengerId": %d,
                    "staffId": "STAFF-01"
                }
                """, passenger1.getId());

        mockMvc.perform(post("/api/activities/{id}/check-in", activity.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkInJson));

        mockMvc.perform(get("/api/activities/{id}/attendance/roster", activity.getId())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].passengerName").value("Hoàng Đức Hải"))
                .andExpect(jsonPath("$.data.content[0].status").value("CHECKED_IN"));
    }

    @Test
    @DisplayName("POST /api/activities/{id}/check-in/offline-sync - Đồng bộ điểm danh ngoại tuyến hàng loạt")
    void testOfflineBatchSyncApi() throws Exception {
        String batchJson = String.format("""
                {
                    "checkIns": [
                        {
                            "passengerId": %d,
                            "staffId": "DEVICE-OFFLINE-1",
                            "offlineQueueId": "Q-001"
                        },
                        {
                            "passengerId": %d,
                            "staffId": "DEVICE-OFFLINE-1",
                            "offlineQueueId": "Q-002"
                        }
                    ]
                }
                """, passenger1.getId(), passenger2.getId());

        mockMvc.perform(post("/api/activities/{id}/check-in/offline-sync", activity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processedCount").value(2))
                .andExpect(jsonPath("$.data.duplicateCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        checkInLogRepository.deleteAll();
        attendanceRepository.deleteAll();
        activityRepository.deleteAll();
        passengerRepository.deleteAll();
    }
}

package com.cruise.itinerary;

import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.entity.Port;
import com.cruise.itinerary.enums.ChangeType;
import com.cruise.itinerary.enums.ShipStatus;
import com.cruise.itinerary.enums.TourStatus;
import com.cruise.itinerary.repository.CruiseShipRepository;
import com.cruise.itinerary.repository.CruiseTourRepository;
import com.cruise.itinerary.repository.DailyItineraryRepository;
import com.cruise.itinerary.repository.ItineraryChangeHistoryRepository;
import com.cruise.itinerary.repository.PortRepository;
import com.jayway.jsonpath.JsonPath;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-End API Integration Tests for Itinerary Updates and Audit Notification Triggers (KAN-128)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Itinerary Updates & Audit History Integration Tests (KAN-128)")
class DailyItineraryUpdateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CruiseTourRepository tourRepository;

    @Autowired
    private CruiseShipRepository shipRepository;

    @Autowired
    private PortRepository portRepository;

    @Autowired
    private DailyItineraryRepository dailyItineraryRepository;

    @Autowired
    private ItineraryChangeHistoryRepository historyRepository;

    private CruiseTour testTour;
    private Port tuanChauPort;
    private Port tienSaPort;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        dailyItineraryRepository.deleteAll();
        tourRepository.deleteAll();
        shipRepository.deleteAll();
        portRepository.deleteAll();

        // 1. Chuẩn bị tàu
        CruiseShip testShip = shipRepository.save(CruiseShip.builder()
                .name("Halong Grand Harmony")
                .code("SHIP-HARMONY")
                .operator("Paradise Cruises")
                .capacity(2000)
                .crewCapacity(600)
                .totalCabins(900)
                .totalDecks(12)
                .manufacturingYear(2023)
                .lengthMeters(300.0)
                .beamMeters(35.0)
                .draftMeters(8.0)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build());

        // 2. Chuẩn bị các cảng dừng
        tuanChauPort = portRepository.save(Port.builder()
                .name("Cảng Tuần Châu")
                .code("VN-TCH")
                .city("Hạ Long")
                .country("Việt Nam")
                .active(true)
                .build());

        tienSaPort = portRepository.save(Port.builder()
                .name("Cảng Tiên Sa")
                .code("VN-TIS")
                .city("Đà Nẵng")
                .country("Việt Nam")
                .active(true)
                .build());

        // 3. Chuẩn bị Tour du thuyền 4 ngày 3 đêm
        testTour = tourRepository.save(CruiseTour.builder()
                .name("Hải Trình Kỳ Quan Vịnh Bắc Bộ 4N3Đ")
                .code("TOUR-HL-001")
                .ship(testShip)
                .durationDays(4)
                .durationNights(3)
                .departurePort("Cảng Tuần Châu")
                .destinationPort("Cảng Tuần Châu")
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 4))
                .maxCapacity(1500)
                .basePrice(new BigDecimal("15500000.00"))
                .currency("VND")
                .status(TourStatus.PUBLISHED)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Tích hợp hoàn chỉnh: Create -> Update Itinerary -> Ghi nhận Change History & Trigger Notification")
    void shouldCreateAndUpdateItineraryAndAuditHistory() throws Exception {
        Long tourId = testTour.getId();
        Long portId = tuanChauPort.getId();

        // 1. Tạo mới lịch trình Ngày 1
        String createPayload = String.format("""
                {
                    "dayNumber": 1,
                    "itineraryDate": "2026-11-01",
                    "title": "Check-in & Khởi hành từ Cảng Tuần Châu",
                    "description": "Đón tiếp hành khách lên tàu",
                    "portId": %d,
                    "portName": "%s",
                    "arrivalTime": "08:00",
                    "departureTime": "12:00",
                    "allAboardTime": "11:30",
                    "isSeaDay": false
                }
                """, portId, tuanChauPort.getName());

        MvcResult createResult = mockMvc.perform(post("/api/itinerary/tours/" + tourId + "/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dayNumber").value(1))
                .andExpect(jsonPath("$.data.title").value("Check-in & Khởi hành từ Cảng Tuần Châu"))
                .andReturn();

        Number rawItineraryId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");
        Long itineraryId = rawItineraryId.longValue();

        // 2. Cập nhật thông tin lịch trình Ngày 1 (UPDATE)
        Long newPortId = tienSaPort.getId();
        String updatePayload = String.format("""
                {
                    "dayNumber": 1,
                    "itineraryDate": "2026-11-01",
                    "title": "Cập nhật: Check-in sớm & Tiệc Gala Chào Mừng",
                    "description": "Mở cổng check-in từ 07:00 sáng",
                    "portId": %d,
                    "portName": "%s",
                    "arrivalTime": "07:00",
                    "departureTime": "13:00",
                    "allAboardTime": "12:30",
                    "isSeaDay": false
                }
                """, newPortId, tienSaPort.getName());

        mockMvc.perform(put("/api/itinerary/tours/" + tourId + "/daily/" + itineraryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Cập nhật: Check-in sớm & Tiệc Gala Chào Mừng"))
                .andExpect(jsonPath("$.data.arrivalTime").value("07:00"))
                .andExpect(jsonPath("$.data.departureTime").value("13:00"));

        // 3. Kiểm tra lịch sử thay đổi (Audit History API)
        mockMvc.perform(get("/api/itinerary/tours/" + tourId + "/daily/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].changeType").value("UPDATE"))
                .andExpect(jsonPath("$.data[0].previousState").value("Day 1: Check-in & Khởi hành từ Cảng Tuần Châu"))
                .andExpect(jsonPath("$.data[0].newState").value("Day 1: Cập nhật: Check-in sớm & Tiệc Gala Chào Mừng"))
                .andExpect(jsonPath("$.data[1].changeType").value("CREATE"));

        // 4. Kiểm tra danh sách thông báo thay đổi (Notifications API)
        mockMvc.perform(get("/api/itinerary/tours/" + tourId + "/daily/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].urgency").value("URGENT"))
                .andExpect(jsonPath("$.data[0].changeType").value("UPDATE"))
                .andExpect(jsonPath("$.data[0].tourId").value(tourId));
    }

    @Test
    @DisplayName("Tích hợp Reorder lịch trình: Đổi thứ tự ngày -> Lưu thay đổi & Kích hoạt thông báo")
    void shouldReorderDailyItinerariesAndTriggerNotification() throws Exception {
        Long tourId = testTour.getId();

        // Tạo 2 ngày lịch trình
        String day1Payload = """
                {
                    "dayNumber": 1,
                    "itineraryDate": "2026-11-01",
                    "title": "Ngày 1: Vịnh Hạ Long",
                    "isSeaDay": false
                }
                """;
        String day2Payload = """
                {
                    "dayNumber": 2,
                    "itineraryDate": "2026-11-02",
                    "title": "Ngày 2: Quần đảo Cát Bà",
                    "isSeaDay": true
                }
                """;

        MvcResult r1 = mockMvc.perform(post("/api/itinerary/tours/" + tourId + "/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(day1Payload))
                .andExpect(status().isCreated())
                .andReturn();
        Number id1 = JsonPath.read(r1.getResponse().getContentAsString(), "$.data.id");

        MvcResult r2 = mockMvc.perform(post("/api/itinerary/tours/" + tourId + "/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(day2Payload))
                .andExpect(status().isCreated())
                .andReturn();
        Number id2 = JsonPath.read(r2.getResponse().getContentAsString(), "$.data.id");

        // Gửi yêu cầu đổi thứ tự (Ngày 1 đổi thành thứ tự 2, Ngày 2 đổi thành thứ tự 1)
        String reorderPayload = String.format("""
                {
                    "items": [
                        { "id": %d, "newOrder": 2 },
                        { "id": %d, "newOrder": 1 }
                    ]
                }
                """, id1.longValue(), id2.longValue());

        mockMvc.perform(patch("/api/itinerary/tours/" + tourId + "/daily/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reorderPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(id2.longValue()))
                .andExpect(jsonPath("$.data[0].dayNumber").value(1))
                .andExpect(jsonPath("$.data[1].id").value(id1.longValue()))
                .andExpect(jsonPath("$.data[1].dayNumber").value(2));

        // Kiểm tra audit history có ghi nhận REORDER
        mockMvc.perform(get("/api/itinerary/tours/" + tourId + "/daily/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].changeType").value("REORDER"));
    }

    @Test
    @DisplayName("Tích hợp Xóa lịch trình: Soft delete ngày lịch trình -> Audit ghi nhận DELETE & Urgency CRITICAL")
    void shouldDeleteDailyItineraryAndAuditCriticalChange() throws Exception {
        Long tourId = testTour.getId();

        String dayPayload = """
                {
                    "dayNumber": 1,
                    "itineraryDate": "2026-11-01",
                    "title": "Ngày thử nghiệm xóa",
                    "isSeaDay": false
                }
                """;

        MvcResult r = mockMvc.perform(post("/api/itinerary/tours/" + tourId + "/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dayPayload))
                .andExpect(status().isCreated())
                .andReturn();
        Number rawId = JsonPath.read(r.getResponse().getContentAsString(), "$.data.id");
        Long itineraryId = rawId.longValue();

        // Xóa ngày lịch trình
        mockMvc.perform(delete("/api/itinerary/tours/" + tourId + "/daily/" + itineraryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Kiểm tra danh sách hiển thị active không còn ngày này
        mockMvc.perform(get("/api/itinerary/tours/" + tourId + "/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // Kiểm tra audit history ghi nhận DELETE
        mockMvc.perform(get("/api/itinerary/tours/" + tourId + "/daily/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].changeType").value("DELETE"));

        // Kiểm tra notification thông báo mức CRITICAL
        mockMvc.perform(get("/api/itinerary/tours/" + tourId + "/daily/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].urgency").value("CRITICAL"))
                .andExpect(jsonPath("$.data[0].changeType").value("DELETE"));
    }
}

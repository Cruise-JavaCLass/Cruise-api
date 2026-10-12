package com.cruise.itinerary;

import com.cruise.itinerary.dto.DailyItineraryRequest;
import com.cruise.itinerary.dto.UpdateAffectedScheduleRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Tests for Daily Itinerary Updates and Change History (KAN-128, KAN-127)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Daily Itinerary Updates & Change History Integration Tests (KAN-128)")
class DailyItineraryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CruiseTourRepository tourRepository;

    @Autowired
    private CruiseShipRepository shipRepository;

    @Autowired
    private PortRepository portRepository;

    @Autowired
    private DailyItineraryRepository itineraryRepository;

    @Autowired
    private ItineraryChangeHistoryRepository historyRepository;

    private CruiseTour testTour;
    private Port testPort;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        itineraryRepository.deleteAll();
        tourRepository.deleteAll();
        shipRepository.deleteAll();
        portRepository.deleteAll();

        CruiseShip ship = shipRepository.save(CruiseShip.builder()
                .name("Halong Grand Pearl")
                .code("SHIP-PEARL")
                .operator("Paradise Cruises")
                .capacity(1500)
                .crewCapacity(400)
                .totalCabins(500)
                .totalDecks(8)
                .manufacturingYear(2023)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build());

        testPort = portRepository.save(Port.builder()
                .name("Cảng Quốc Tế Tuần Châu")
                .code("PORT-TC")
                .country("Việt Nam")
                .city("Hạ Long")
                .active(true)
                .build());

        testTour = tourRepository.save(CruiseTour.builder()
                .name("Hải Trình Hạ Long 3N2Đ")
                .code("TOUR-HL-3D2N")
                .ship(ship)
                .departurePort("Cảng Quốc Tế Tuần Châu")
                .destinationPort("Cảng Quốc Tế Tuần Châu")
                .durationDays(3)
                .durationNights(2)
                .maxCapacity(1500)
                .currency("VND")
                .basePrice(BigDecimal.valueOf(5000000))
                .status(TourStatus.DRAFT)
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 3))
                .active(true)
                .build());
    }

    @Test
    @DisplayName("E2E Workflow: Tạo -> Cập nhật thông thường -> Cập nhật bị ảnh hưởng (bão) -> Kiểm tra lịch sử lưu vết")
    void testItineraryUpdateAndHistoryTrackingWorkflow() throws Exception {
        Long tourId = testTour.getId();
        Long portId = testPort.getId();

        // 1. Tạo mới lịch trình ngày 1
        String createJson = String.format("""
                {
                    "dayNumber": 1,
                    "itineraryDate": "2026-11-01",
                    "title": "Khởi hành từ Cảng Tuần Châu",
                    "portId": %d,
                    "portName": "%s",
                    "arrivalTime": "08:00",
                    "departureTime": "17:00",
                    "allAboardTime": "16:30",
                    "isSeaDay": false,
                    "weatherForecast": "Trời nắng đẹp, biển lặng"
                }
                """, portId, testPort.getName());

        MvcResult createResult = mockMvc.perform(post("/api/itinerary/tours/{tourId}/daily", tourId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dayNumber").value(1))
                .andReturn();

        Integer itineraryIdInt = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");
        Long itineraryId = itineraryIdInt.longValue();

        // 2. Cập nhật thông thường (PUT)
        String updateJson = String.format("""
                {
                    "dayNumber": 1,
                    "itineraryDate": "2026-11-01",
                    "title": "Khởi hành từ Tuần Châu và tiệc chào mừng",
                    "portId": %d,
                    "portName": "%s",
                    "arrivalTime": "08:30",
                    "departureTime": "17:00",
                    "allAboardTime": "16:45",
                    "isSeaDay": false,
                    "weatherForecast": "Trời nhiều mây"
                }
                """, portId, testPort.getName());

        mockMvc.perform(put("/api/itinerary/tours/{tourId}/daily/{id}", tourId, itineraryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Khởi hành từ Tuần Châu và tiệc chào mừng"));

        // 3. Cập nhật lịch trình bị ảnh hưởng do thời tiết (PATCH /affected)
        String affectedJson = """
                {
                    "changeType": "WEATHER_UPDATE",
                    "reason": "Bão số 3 áp sát Vịnh Bắc Bộ, dời giờ xuất bến",
                    "changedBy": "CAPTAIN_HUY",
                    "departureTime": "19:00",
                    "allAboardTime": "18:30",
                    "weatherForecast": "Gió giật cấp 7, có mưa giông rải rác"
                }
                """;

        mockMvc.perform(patch("/api/itinerary/tours/{tourId}/daily/{id}/affected", tourId, itineraryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectedJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isAffected").value(true))
                .andExpect(jsonPath("$.data.changeReason").value("Bão số 3 áp sát Vịnh Bắc Bộ, dời giờ xuất bến"))
                .andExpect(jsonPath("$.data.changeType").value("WEATHER_UPDATE"));

        // 4. Lấy lịch sử thay đổi của Tour (GET /daily/history)
        mockMvc.perform(get("/api/itinerary/tours/{tourId}/daily/history", tourId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3)) // CREATE + UPDATE + WEATHER_UPDATE
                .andExpect(jsonPath("$.data[0].changeType").value("WEATHER_UPDATE"))
                .andExpect(jsonPath("$.data[0].reason").value("Bão số 3 áp sát Vịnh Bắc Bộ, dời giờ xuất bến"))
                .andExpect(jsonPath("$.data[0].changedBy").value("CAPTAIN_HUY"));

        // 5. Lấy lịch sử thay đổi theo từng ngày (GET /daily/{id}/history)
        mockMvc.perform(get("/api/itinerary/tours/{tourId}/daily/{id}/history", tourId, itineraryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].changeType").value("WEATHER_UPDATE"));
    }

    @Test
    @DisplayName("Tích hợp reorder và xóa lịch trình ghi nhận lịch sử đầy đủ")
    void testReorderAndDeleteHistoryTracking() throws Exception {
        Long tourId = testTour.getId();

        // Tạo Ngày 1
        mockMvc.perform(post("/api/itinerary/tours/{tourId}/daily", tourId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "dayNumber": 1,
                            "itineraryDate": "2026-11-01",
                            "title": "Ngày 1",
                            "isSeaDay": true
                        }
                        """)).andExpect(status().isCreated());

        // Tạo Ngày 2
        MvcResult resDay2 = mockMvc.perform(post("/api/itinerary/tours/{tourId}/daily", tourId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "dayNumber": 2,
                            "itineraryDate": "2026-11-02",
                            "title": "Ngày 2",
                            "isSeaDay": true
                        }
                        """)).andExpect(status().isCreated()).andReturn();

        Integer day2Id = JsonPath.read(resDay2.getResponse().getContentAsString(), "$.data.id");

        // Xóa Ngày 2 (DELETE)
        mockMvc.perform(delete("/api/itinerary/tours/{tourId}/daily/{id}", tourId, day2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Kiểm tra lịch sử có ghi nhận loại DELETE
        mockMvc.perform(get("/api/itinerary/tours/{tourId}/daily/history", tourId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].changeType").value("DELETE"))
                .andExpect(jsonPath("$.data[0].reason").value("Xóa lịch trình ngày 2"));
    }
}

package com.cruise.itinerary;

import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.enums.ShipStatus;
import com.cruise.itinerary.repository.CruiseShipRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("CruiseShip End-to-End Integration Tests")
class CruiseShipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CruiseShipRepository shipRepository;

    @BeforeEach
    void setUp() {
        shipRepository.deleteAll();
    }

    @Test
    @DisplayName("Complete Lifecycle: Create -> Get -> Search -> Update -> Patch Status -> Soft Delete -> Hard Delete")
    void shouldHandleCompleteShipLifecycle() throws Exception {
        // 1. Tạo mới tàu du lịch
        String createPayload = """
                {
                    "name": "Emerald Symphony of Halong",
                    "code": "SHIP-EMERALD",
                    "operator": "Emerald Ocean Cruises",
                    "capacity": 2800,
                    "crewCapacity": 850,
                    "totalCabins": 1300,
                    "totalDecks": 15,
                    "manufacturingYear": 2024,
                    "lengthMeters": 330.0,
                    "beamMeters": 40.0,
                    "draftMeters": 8.9,
                    "status": "ACTIVE",
                    "description": "Siêu du thuyền 5 sao đẳng cấp quốc tế",
                    "amenities": "Helipad, Casino, Sky Deck, 5-star Dining",
                    "imageUrl": "https://cruise.com/emerald.jpg"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/itinerary/ships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("SHIP-EMERALD"))
                .andExpect(jsonPath("$.data.name").value("Emerald Symphony of Halong"))
                .andReturn();

        Integer createdIdInt = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");
        Long createdId = createdIdInt.longValue();

        // 2. Lấy chi tiết tàu theo ID
        mockMvc.perform(get("/api/itinerary/ships/" + createdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(createdId))
                .andExpect(jsonPath("$.data.capacity").value(2800));

        // 3. Lấy chi tiết tàu theo Code
        mockMvc.perform(get("/api/itinerary/ships/code/SHIP-EMERALD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("SHIP-EMERALD"));

        // 4. Tìm kiếm tàu trong danh sách
        mockMvc.perform(get("/api/itinerary/ships")
                        .param("keyword", "Emerald")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // 5. Cập nhật thông tin tàu
        String updatePayload = """
                {
                    "name": "Emerald Symphony of Halong VIP",
                    "code": "SHIP-EMERALD",
                    "operator": "Emerald Ocean Cruises Group",
                    "capacity": 3000,
                    "crewCapacity": 900,
                    "totalCabins": 1400,
                    "totalDecks": 16,
                    "manufacturingYear": 2024,
                    "lengthMeters": 335.0,
                    "beamMeters": 41.0,
                    "draftMeters": 9.0,
                    "status": "DOCKED",
                    "description": "Nâng cấp cabin VIP",
                    "amenities": "Helipad, Casino, Sky Deck, VIP Lounge",
                    "imageUrl": "https://cruise.com/emerald-vip.jpg",
                    "active": true
                }
                """;

        mockMvc.perform(put("/api/itinerary/ships/" + createdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Emerald Symphony of Halong VIP"))
                .andExpect(jsonPath("$.data.capacity").value(3000))
                .andExpect(jsonPath("$.data.status").value("DOCKED"));

        // 6. Cập nhật trạng thái tàu thành ON_VOYAGE
        String statusPayload = """
                {
                    "status": "ON_VOYAGE"
                }
                """;

        mockMvc.perform(patch("/api/itinerary/ships/" + createdId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ON_VOYAGE"));

        // 7. Soft delete tàu
        mockMvc.perform(delete("/api/itinerary/ships/" + createdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        CruiseShip softDeleted = shipRepository.findById(createdId).orElseThrow();
        assertThat(softDeleted.getActive()).isFalse();
        assertThat(softDeleted.getStatus()).isEqualTo(ShipStatus.INACTIVE);

        // 8. Hard delete tàu
        mockMvc.perform(delete("/api/itinerary/ships/" + createdId).param("hard", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(shipRepository.findById(createdId)).isEmpty();

        // 9. Xác minh truy vấn ID đã xóa trả về 404
        mockMvc.perform(get("/api/itinerary/ships/" + createdId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

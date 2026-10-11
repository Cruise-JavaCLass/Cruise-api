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

    @Test
    @DisplayName("Create Ship: POST /api/itinerary/ships persists ship in database with valid data")
    void shouldCreateShipAndPersistInDatabase() throws Exception {
        String payload = """
                {
                    "name": "Diamond Princess of Danang",
                    "code": "SHIP-DIAMOND",
                    "operator": "Danang Cruise Lines",
                    "capacity": 2200,
                    "crewCapacity": 700,
                    "totalCabins": 1050,
                    "totalDecks": 14,
                    "manufacturingYear": 2023,
                    "lengthMeters": 290.0,
                    "beamMeters": 37.5,
                    "draftMeters": 8.2,
                    "status": "ACTIVE",
                    "description": "Du thuyền sang trọng vịnh Đà Nẵng",
                    "amenities": "Infinity Pool, Sky Bar",
                    "imageUrl": "https://cruise.com/diamond.jpg"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/itinerary/ships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Diamond Princess of Danang"))
                .andExpect(jsonPath("$.data.code").value("SHIP-DIAMOND"))
                .andReturn();

        Integer idInt = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        Long id = idInt.longValue();

        CruiseShip saved = shipRepository.findById(id).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Diamond Princess of Danang");
        assertThat(saved.getCode()).isEqualTo("SHIP-DIAMOND");
        assertThat(saved.getOperator()).isEqualTo("Danang Cruise Lines");
        assertThat(saved.getCapacity()).isEqualTo(2200);
        assertThat(saved.getCrewCapacity()).isEqualTo(700);
        assertThat(saved.getTotalCabins()).isEqualTo(1050);
        assertThat(saved.getTotalDecks()).isEqualTo(14);
        assertThat(saved.getManufacturingYear()).isEqualTo(2023);
        assertThat(saved.getStatus()).isEqualTo(ShipStatus.ACTIVE);
        assertThat(saved.getActive()).isTrue();
    }

    @Test
    @DisplayName("Create Ship: POST /api/itinerary/ships fails with 409 Conflict when duplicate code or name exists")
    void shouldFailToCreateShipWithDuplicateCodeOrName() throws Exception {
        CruiseShip existing = CruiseShip.builder()
                .name("Existing Ship")
                .code("SHIP-EXISTING")
                .operator("Existing Operator")
                .capacity(1000)
                .crewCapacity(300)
                .totalCabins(500)
                .totalDecks(10)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build();
        shipRepository.save(existing);

        // Trùng mã code
        String dupCodePayload = """
                {
                    "name": "Different Name",
                    "code": "SHIP-EXISTING",
                    "operator": "Some Operator",
                    "capacity": 1500,
                    "crewCapacity": 400,
                    "totalCabins": 700,
                    "totalDecks": 11
                }
                """;

        mockMvc.perform(post("/api/itinerary/ships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dupCodePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mã tàu 'SHIP-EXISTING' đã tồn tại trong hệ thống"));

        // Trùng tên name
        String dupNamePayload = """
                {
                    "name": "Existing Ship",
                    "code": "SHIP-NEW-001",
                    "operator": "Some Operator",
                    "capacity": 1500,
                    "crewCapacity": 400,
                    "totalCabins": 700,
                    "totalDecks": 11
                }
                """;

        mockMvc.perform(post("/api/itinerary/ships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dupNamePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tên tàu 'Existing Ship' đã tồn tại trong hệ thống"));
    }

    @Test
    @DisplayName("Create Ship: POST /api/itinerary/ships fails with 400 Bad Request when validation constraints are violated")
    void shouldFailToCreateShipWithInvalidData() throws Exception {
        String invalidPayload = """
                {
                    "name": "",
                    "code": "S",
                    "operator": "",
                    "capacity": 0,
                    "crewCapacity": -1,
                    "totalCabins": 0,
                    "totalDecks": 0
                }
                """;

        mockMvc.perform(post("/api/itinerary/ships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Read Ship: GET /api/itinerary/ships/code/{code} and by ID return 404 when not found")
    void shouldReturn404WhenShipNotFound() throws Exception {
        mockMvc.perform(get("/api/itinerary/ships/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy tàu du lịch với ID: 999999"));

        mockMvc.perform(get("/api/itinerary/ships/code/NON-EXISTENT-CODE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy tàu du lịch với mã: NON-EXISTENT-CODE"));
    }

    @Test
    @DisplayName("Read Ships: GET /api/itinerary/ships filters by keyword, status, and supports pagination")
    void shouldFilterAndPaginateShips() throws Exception {
        CruiseShip ship1 = CruiseShip.builder()
                .name("Halong Wonder")
                .code("SHIP-WONDER")
                .operator("Wonder Cruises")
                .capacity(1200)
                .crewCapacity(400)
                .totalCabins(600)
                .totalDecks(10)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build();

        CruiseShip ship2 = CruiseShip.builder()
                .name("Halong Breeze")
                .code("SHIP-BREEZE")
                .operator("Breeze Cruises")
                .capacity(1500)
                .crewCapacity(500)
                .totalCabins(750)
                .totalDecks(11)
                .status(ShipStatus.UNDER_MAINTENANCE)
                .active(true)
                .build();

        CruiseShip ship3 = CruiseShip.builder()
                .name("Saigon Pearl")
                .code("SHIP-PEARL")
                .operator("Pearl Cruises")
                .capacity(800)
                .crewCapacity(250)
                .totalCabins(400)
                .totalDecks(8)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build();

        shipRepository.saveAll(java.util.List.of(ship1, ship2, ship3));

        // Lọc theo keyword "Halong"
        mockMvc.perform(get("/api/itinerary/ships")
                        .param("keyword", "Halong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // Lọc theo status "UNDER_MAINTENANCE"
        mockMvc.perform(get("/api/itinerary/ships")
                        .param("status", "UNDER_MAINTENANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Halong Breeze"));

        // Phân trang page=0, size=2
        mockMvc.perform(get("/api/itinerary/ships")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("Read Active Ships: GET /api/itinerary/ships/active returns only active ships")
    void shouldGetOnlyActiveShipsForDropdown() throws Exception {
        CruiseShip activeShip = CruiseShip.builder()
                .name("Active Ship")
                .code("SHIP-ACT")
                .operator("Operator")
                .capacity(1000)
                .crewCapacity(300)
                .totalCabins(500)
                .totalDecks(9)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build();

        CruiseShip inactiveShip = CruiseShip.builder()
                .name("Inactive Ship")
                .code("SHIP-INACT")
                .operator("Operator")
                .capacity(1000)
                .crewCapacity(300)
                .totalCabins(500)
                .totalDecks(9)
                .status(ShipStatus.INACTIVE)
                .active(false)
                .build();

        shipRepository.saveAll(java.util.List.of(activeShip, inactiveShip));

        mockMvc.perform(get("/api/itinerary/ships/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("SHIP-ACT"));
    }

    @Test
    @DisplayName("Update Ship: PUT /api/itinerary/ships/{id} updates fields and checks duplicate conflict")
    void shouldUpdateShipDetailsAndHandleConflicts() throws Exception {
        CruiseShip shipA = CruiseShip.builder()
                .name("Ship Alpha")
                .code("SHIP-ALPHA")
                .operator("Alpha Line")
                .capacity(1000)
                .crewCapacity(300)
                .totalCabins(500)
                .totalDecks(10)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build();

        CruiseShip shipB = CruiseShip.builder()
                .name("Ship Beta")
                .code("SHIP-BETA")
                .operator("Beta Line")
                .capacity(1200)
                .crewCapacity(350)
                .totalCabins(600)
                .totalDecks(11)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build();

        shipRepository.saveAll(java.util.List.of(shipA, shipB));

        // 1. Cập nhật thành công shipA
        String updatePayload = """
                {
                    "name": "Ship Alpha Renamed",
                    "code": "SHIP-ALPHA-NEW",
                    "operator": "Alpha Line Premium",
                    "capacity": 1500,
                    "crewCapacity": 450,
                    "totalCabins": 750,
                    "totalDecks": 12,
                    "status": "DOCKED",
                    "active": true
                }
                """;

        mockMvc.perform(put("/api/itinerary/ships/" + shipA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Ship Alpha Renamed"))
                .andExpect(jsonPath("$.data.capacity").value(1500));

        // 2. Thử cập nhật shipB trùng mã code với shipA mới
        String conflictCodePayload = """
                {
                    "name": "Ship Beta Other",
                    "code": "SHIP-ALPHA-NEW",
                    "operator": "Beta Line",
                    "capacity": 1200,
                    "crewCapacity": 350,
                    "totalCabins": 600,
                    "totalDecks": 11
                }
                """;

        mockMvc.perform(put("/api/itinerary/ships/" + shipB.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictCodePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mã tàu 'SHIP-ALPHA-NEW' đã được sử dụng bởi tàu khác"));

        // 3. Thử cập nhật với ID không tồn tại
        mockMvc.perform(put("/api/itinerary/ships/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Update Status: PATCH /api/itinerary/ships/{id}/status changes status and returns 404 for unknown ID")
    void shouldUpdateShipStatusSuccessfully() throws Exception {
        CruiseShip ship = CruiseShip.builder()
                .name("Voyager Express")
                .code("SHIP-VOYAGER")
                .operator("Voyager Line")
                .capacity(1600)
                .crewCapacity(500)
                .totalCabins(800)
                .totalDecks(12)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build();
        shipRepository.save(ship);

        String statusPayload = """
                {
                    "status": "UNDER_MAINTENANCE"
                }
                """;

        mockMvc.perform(patch("/api/itinerary/ships/" + ship.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UNDER_MAINTENANCE"));

        CruiseShip updated = shipRepository.findById(ship.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ShipStatus.UNDER_MAINTENANCE);

        // ID không tồn tại
        mockMvc.perform(patch("/api/itinerary/ships/999999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Delete Ship: DELETE /api/itinerary/ships/{id} returns 404 for non-existent ID")
    void shouldReturn404WhenDeletingNonExistentShip() throws Exception {
        mockMvc.perform(delete("/api/itinerary/ships/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy tàu du lịch với ID: 999999"));
    }
}

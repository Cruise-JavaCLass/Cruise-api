package com.cruise.itinerary.controller;

import com.cruise.itinerary.dto.*;
import com.cruise.itinerary.enums.ShipStatus;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.exception.GlobalExceptionHandler;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.service.CruiseShipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CruiseShipController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("CruiseShipController MockMvc Web Layer Tests")
class CruiseShipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CruiseShipService shipService;

    private CruiseShipResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = CruiseShipResponse.builder()
                .id(1L)
                .name("Halong Grand Harmony")
                .code("SHIP-001")
                .operator("Paradise Vietnam Cruises")
                .capacity(2400)
                .crewCapacity(750)
                .totalCabins(1100)
                .totalDecks(14)
                .manufacturingYear(2023)
                .lengthMeters(315.5)
                .beamMeters(38.0)
                .draftMeters(8.5)
                .status(ShipStatus.ACTIVE)
                .description("Du thuyền cao cấp 5 sao")
                .amenities("Sky Pool, Luxury Spa, Grand Ballroom")
                .imageUrl("https://cruise.com/ships/ship-001.jpg")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/itinerary/ships creates ship and returns 201 Created")
    void shouldCreateShipSuccessfully() throws Exception {
        when(shipService.createShip(any(CreateCruiseShipRequest.class))).thenReturn(sampleResponse);

        String jsonPayload = """
                {
                    "name": "Halong Grand Harmony",
                    "code": "SHIP-001",
                    "operator": "Paradise Vietnam Cruises",
                    "capacity": 2400,
                    "crewCapacity": 750,
                    "totalCabins": 1100,
                    "totalDecks": 14,
                    "manufacturingYear": 2023,
                    "lengthMeters": 315.5,
                    "beamMeters": 38.0,
                    "draftMeters": 8.5,
                    "status": "ACTIVE",
                    "description": "Du thuyền cao cấp 5 sao",
                    "amenities": "Sky Pool, Luxury Spa, Grand Ballroom",
                    "imageUrl": "https://cruise.com/ships/ship-001.jpg"
                }
                """;

        mockMvc.perform(post("/api/itinerary/ships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tạo tàu du lịch thành công"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Halong Grand Harmony"))
                .andExpect(jsonPath("$.data.code").value("SHIP-001"))
                .andExpect(jsonPath("$.data.capacity").value(2400));
    }

    @Test
    @DisplayName("POST /api/itinerary/ships returns 400 Bad Request when validation fails")
    void shouldReturnBadRequestWhenValidationFails() throws Exception {
        String invalidPayload = """
                {
                    "name": "",
                    "code": "",
                    "operator": "",
                    "capacity": -10,
                    "crewCapacity": 0,
                    "totalCabins": -5,
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
    @DisplayName("POST /api/itinerary/ships returns 409 Conflict when code or name already exists")
    void shouldReturnConflictWhenDuplicateCodeOrName() throws Exception {
        when(shipService.createShip(any(CreateCruiseShipRequest.class)))
                .thenThrow(new DuplicateResourceException("Mã tàu 'SHIP-001' đã tồn tại trong hệ thống"));

        String jsonPayload = """
                {
                    "name": "Halong Grand Harmony",
                    "code": "SHIP-001",
                    "operator": "Paradise Vietnam Cruises",
                    "capacity": 2400,
                    "crewCapacity": 750,
                    "totalCabins": 1100,
                    "totalDecks": 14
                }
                """;

        mockMvc.perform(post("/api/itinerary/ships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mã tàu 'SHIP-001' đã tồn tại trong hệ thống"));
    }

    @Test
    @DisplayName("GET /api/itinerary/ships returns paginated ship list")
    void shouldGetAllShipsWithPagination() throws Exception {
        when(shipService.getAllShips(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/itinerary/ships")
                        .param("page", "0")
                        .param("size", "10")
                        .param("keyword", "Halong")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Halong Grand Harmony"))
                .andExpect(jsonPath("$.data.content[0].code").value("SHIP-001"));
    }

    @Test
    @DisplayName("GET /api/itinerary/ships/active returns list of active ships")
    void shouldGetActiveShips() throws Exception {
        when(shipService.getAllActiveShips()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/itinerary/ships/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Halong Grand Harmony"));
    }

    @Test
    @DisplayName("GET /api/itinerary/ships/{id} returns ship details when found")
    void shouldGetShipByIdSuccessfully() throws Exception {
        when(shipService.getShipById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/itinerary/ships/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Halong Grand Harmony"));
    }

    @Test
    @DisplayName("GET /api/itinerary/ships/{id} returns 404 Not Found when ID does not exist")
    void shouldReturnNotFoundWhenShipIdDoesNotExist() throws Exception {
        when(shipService.getShipById(999L))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy tàu du lịch với ID: 999"));

        mockMvc.perform(get("/api/itinerary/ships/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy tàu du lịch với ID: 999"));
    }

    @Test
    @DisplayName("GET /api/itinerary/ships/code/{code} returns ship details when found")
    void shouldGetShipByCodeSuccessfully() throws Exception {
        when(shipService.getShipByCode("SHIP-001")).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/itinerary/ships/code/SHIP-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("SHIP-001"));
    }

    @Test
    @DisplayName("PUT /api/itinerary/ships/{id} updates ship details and returns 200 OK")
    void shouldUpdateShipSuccessfully() throws Exception {
        when(shipService.updateShip(eq(1L), any(UpdateCruiseShipRequest.class))).thenReturn(sampleResponse);

        String jsonPayload = """
                {
                    "name": "Halong Grand Harmony Luxury",
                    "code": "SHIP-001",
                    "operator": "Paradise Vietnam Cruises",
                    "capacity": 2500,
                    "crewCapacity": 800,
                    "totalCabins": 1150,
                    "totalDecks": 15
                }
                """;

        mockMvc.perform(put("/api/itinerary/ships/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật thông tin tàu du lịch thành công"));
    }

    @Test
    @DisplayName("PATCH /api/itinerary/ships/{id}/status updates ship status and returns 200 OK")
    void shouldUpdateShipStatusSuccessfully() throws Exception {
        CruiseShipResponse updatedStatus = CruiseShipResponse.builder()
                .id(1L)
                .name("Halong Grand Harmony")
                .code("SHIP-001")
                .status(ShipStatus.ON_VOYAGE)
                .build();

        when(shipService.updateShipStatus(eq(1L), any(UpdateShipStatusRequest.class))).thenReturn(updatedStatus);

        String jsonPayload = """
                {
                    "status": "ON_VOYAGE"
                }
                """;

        mockMvc.perform(patch("/api/itinerary/ships/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ON_VOYAGE"));
    }

    @Test
    @DisplayName("DELETE /api/itinerary/ships/{id} soft deletes ship by default")
    void shouldSoftDeleteShip() throws Exception {
        doNothing().when(shipService).deleteShip(1L, false);

        mockMvc.perform(delete("/api/itinerary/ships/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã hủy kích hoạt tàu du lịch thành công"));

        verify(shipService).deleteShip(1L, false);
    }

    @Test
    @DisplayName("DELETE /api/itinerary/ships/{id}?hard=true hard deletes ship")
    void shouldHardDeleteShip() throws Exception {
        doNothing().when(shipService).deleteShip(1L, true);

        mockMvc.perform(delete("/api/itinerary/ships/1").param("hard", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã xóa vĩnh viễn tàu du lịch khỏi hệ thống"));

        verify(shipService).deleteShip(1L, true);
    }

    @Test
    @DisplayName("GET /api/itinerary/ships/code/{code} returns 404 when code does not exist")
    void shouldReturnNotFoundWhenShipCodeDoesNotExist() throws Exception {
        when(shipService.getShipByCode("NON-EXISTENT"))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy tàu du lịch với mã: NON-EXISTENT"));

        mockMvc.perform(get("/api/itinerary/ships/code/NON-EXISTENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy tàu du lịch với mã: NON-EXISTENT"));
    }

    @Test
    @DisplayName("GET /api/itinerary/ships supports ascending sort")
    void shouldGetAllShipsWithAscendingSort() throws Exception {
        when(shipService.getAllShips(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/itinerary/ships")
                        .param("direction", "asc")
                        .param("sortBy", "name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(shipService).getAllShips(isNull(), isNull(), eq(true), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/itinerary/ships with activeOnly=false retrieves all ships including inactive")
    void shouldGetAllShipsWithActiveOnlyFalse() throws Exception {
        when(shipService.getAllShips(any(), any(), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/itinerary/ships")
                        .param("activeOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(shipService).getAllShips(isNull(), isNull(), eq(false), any(Pageable.class));
    }

    @Test
    @DisplayName("PUT /api/itinerary/ships/{id} returns 404 Not Found when ID does not exist")
    void shouldReturnNotFoundWhenUpdatingNonExistentShip() throws Exception {
        when(shipService.updateShip(eq(999L), any(UpdateCruiseShipRequest.class)))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy tàu du lịch với ID: 999"));

        String jsonPayload = """
                {
                    "name": "Halong Grand Harmony Luxury",
                    "code": "SHIP-001",
                    "operator": "Paradise Vietnam Cruises",
                    "capacity": 2500,
                    "crewCapacity": 800,
                    "totalCabins": 1150,
                    "totalDecks": 15
                }
                """;

        mockMvc.perform(put("/api/itinerary/ships/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy tàu du lịch với ID: 999"));
    }

    @Test
    @DisplayName("PUT /api/itinerary/ships/{id} returns 409 Conflict when duplicate name or code occurs")
    void shouldReturnConflictWhenUpdatingShipWithDuplicateCodeOrName() throws Exception {
        when(shipService.updateShip(eq(1L), any(UpdateCruiseShipRequest.class)))
                .thenThrow(new DuplicateResourceException("Mã tàu 'SHIP-002' đã được sử dụng bởi tàu khác"));

        String jsonPayload = """
                {
                    "name": "Halong Grand Harmony Luxury",
                    "code": "SHIP-002",
                    "operator": "Paradise Vietnam Cruises",
                    "capacity": 2500,
                    "crewCapacity": 800,
                    "totalCabins": 1150,
                    "totalDecks": 15
                }
                """;

        mockMvc.perform(put("/api/itinerary/ships/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mã tàu 'SHIP-002' đã được sử dụng bởi tàu khác"));
    }

    @Test
    @DisplayName("PUT /api/itinerary/ships/{id} returns 400 Bad Request when validation fails")
    void shouldReturnBadRequestWhenUpdatingShipWithInvalidPayload() throws Exception {
        String invalidPayload = """
                {
                    "name": "",
                    "code": "",
                    "capacity": -100
                }
                """;

        mockMvc.perform(put("/api/itinerary/ships/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/itinerary/ships/{id}/status returns 404 Not Found when ID does not exist")
    void shouldReturnNotFoundWhenUpdatingStatusOfNonExistentShip() throws Exception {
        when(shipService.updateShipStatus(eq(999L), any(UpdateShipStatusRequest.class)))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy tàu du lịch với ID: 999"));

        String jsonPayload = """
                {
                    "status": "UNDER_MAINTENANCE"
                }
                """;

        mockMvc.perform(patch("/api/itinerary/ships/999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy tàu du lịch với ID: 999"));
    }

    @Test
    @DisplayName("PATCH /api/itinerary/ships/{id}/status returns 400 Bad Request when status is null")
    void shouldReturnBadRequestWhenUpdatingStatusWithNullStatus() throws Exception {
        String invalidPayload = """
                {
                    "status": null
                }
                """;

        mockMvc.perform(patch("/api/itinerary/ships/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/itinerary/ships/{id} returns 404 Not Found when ID does not exist")
    void shouldReturnNotFoundWhenDeletingNonExistentShip() throws Exception {
        doThrow(new ResourceNotFoundException("Không tìm thấy tàu du lịch với ID: 999"))
                .when(shipService).deleteShip(999L, false);

        mockMvc.perform(delete("/api/itinerary/ships/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy tàu du lịch với ID: 999"));
    }
}

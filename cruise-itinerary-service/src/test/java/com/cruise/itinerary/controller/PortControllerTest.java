package com.cruise.itinerary.controller;

import com.cruise.itinerary.dto.PortRequest;
import com.cruise.itinerary.dto.PortResponse;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.exception.GlobalExceptionHandler;
import com.cruise.itinerary.service.PortService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("PortController MockMvc Tests (KAN-105)")
class PortControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortService portService;

    @Test
    @DisplayName("POST /api/itinerary/ports thành công trả về 201 Created")
    void createPortSuccess() throws Exception {
        PortResponse resp = PortResponse.builder()
                .id(1L)
                .name("Cảng Tuần Châu")
                .code("VN-TCH")
                .city("Hạ Long")
                .country("Việt Nam")
                .active(true)
                .build();

        when(portService.createPort(any(PortRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/itinerary/ports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cảng Tuần Châu",
                                  "code": "VN-TCH",
                                  "city": "Hạ Long"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("VN-TCH"));
    }

    @Test
    @DisplayName("POST /api/itinerary/ports thất bại khi mã cảng bị trùng trả về 409 Conflict")
    void createPortDuplicateCode() throws Exception {
        when(portService.createPort(any(PortRequest.class)))
                .thenThrow(new DuplicateResourceException("Mã cảng đã tồn tại"));

        mockMvc.perform(post("/api/itinerary/ports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cảng Tuần Châu",
                                  "code": "VN-TCH",
                                  "city": "Hạ Long"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/itinerary/ports trả về danh sách phân trang")
    void getAllPortsSuccess() throws Exception {
        PortResponse resp = PortResponse.builder().id(1L).name("Cảng Tuần Châu").code("VN-TCH").city("Hạ Long").build();
        when(portService.getAllPorts(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp)));

        mockMvc.perform(get("/api/itinerary/ports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].code").value("VN-TCH"));
    }

    @Test
    @DisplayName("GET /api/itinerary/ports/active trả về danh sách cảng active")
    void getActivePortsSuccess() throws Exception {
        PortResponse resp = PortResponse.builder().id(1L).name("Cảng Tuần Châu").code("VN-TCH").city("Hạ Long").build();
        when(portService.getActivePorts()).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/itinerary/ports/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("VN-TCH"));
    }

    @Test
    @DisplayName("DELETE /api/itinerary/ports/{id} thành công")
    void deletePortSuccess() throws Exception {
        doNothing().when(portService).deletePort(1L);

        mockMvc.perform(delete("/api/itinerary/ports/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

package com.cruise.activity.controller;

import com.cruise.activity.dto.PassengerResponse;
import com.cruise.activity.dto.RegisterPassengerRequest;
import com.cruise.activity.enums.CheckInStatus;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.service.PassengerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PassengerController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("PassengerController MockMvc Tests (KAN-146)")
class PassengerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PassengerService passengerService;

    @Test
    @DisplayName("POST /api/activities/passengers/register - Đăng ký hành khách thành công")
    void registerPassengerSuccess() throws Exception {
        PassengerResponse response = PassengerResponse.builder()
                .id(1L)
                .tourId(10L)
                .fullName("Trần Thị Thu Thảo")
                .cabinNumber("718")
                .rfidTag("RFID-99413")
                .tourPackage("All-Inclusive VIP")
                .checkInStatus(CheckInStatus.BOARDED)
                .build();

        when(passengerService.registerPassenger(any(RegisterPassengerRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/activities/passengers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "tourId": 10,
                                    "fullName": "Trần Thị Thu Thảo",
                                    "cabinNumber": "718",
                                    "rfidTag": "RFID-99413",
                                    "tourPackage": "All-Inclusive VIP",
                                    "checkInStatus": "BOARDED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Trần Thị Thu Thảo"))
                .andExpect(jsonPath("$.data.cabinNumber").value("718"));
    }

    @Test
    @DisplayName("GET /api/activities/passengers/tour/{tourId} - Lấy danh sách hành khách theo tour")
    void getRegisteredPassengersByTourSuccess() throws Exception {
        PassengerResponse response = PassengerResponse.builder()
                .id(1L)
                .tourId(10L)
                .fullName("Lê Hoàng Nam")
                .cabinNumber("505")
                .rfidTag("RFID-88102")
                .build();

        when(passengerService.getRegisteredPassengersByTourAndStatus(10L, null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/activities/passengers/tour/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].fullName").value("Lê Hoàng Nam"))
                .andExpect(jsonPath("$.data[0].cabinNumber").value("505"));
    }

    @Test
    @DisplayName("GET /api/activities/passengers/tour/{tourId}?status=CONFIRMED - Lọc danh sách theo status")
    void getRegisteredPassengersByTourWithStatusSuccess() throws Exception {
        PassengerResponse response = PassengerResponse.builder()
                .id(1L)
                .tourId(10L)
                .fullName("Lê Hoàng Nam")
                .registrationStatus(com.cruise.activity.enums.RegistrationStatus.CONFIRMED)
                .build();

        when(passengerService.getRegisteredPassengersByTourAndStatus(10L, com.cruise.activity.enums.RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/activities/passengers/tour/10?status=CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].registrationStatus").value("CONFIRMED"));
    }

    @Test
    @DisplayName("GET /api/activities/passengers/tour/{tourId}/paged - Lấy danh sách phân trang và tìm kiếm")
    void getRegisteredPassengersByTourPagedSuccess() throws Exception {
        PassengerResponse response = PassengerResponse.builder()
                .id(1L)
                .tourId(10L)
                .fullName("Phạm Hồng Ánh")
                .cabinNumber("812")
                .build();

        when(passengerService.getRegisteredPassengersByTourPaged(eq(10L), eq("812"), eq(com.cruise.activity.enums.RegistrationStatus.CONFIRMED), any()))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/activities/passengers/tour/10/paged?keyword=812&status=CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Phạm Hồng Ánh"));
    }

    @Test
    @DisplayName("GET /api/activities/passengers/rfid/{rfidTag} - Tra cứu hành khách theo RFID")
    void getPassengerByRfidTagSuccess() throws Exception {
        PassengerResponse response = PassengerResponse.builder()
                .id(2L)
                .tourId(10L)
                .fullName("Đỗ Quốc Bảo")
                .cabinNumber("304")
                .rfidTag("RFID-66230")
                .build();

        when(passengerService.getPassengerByRfidTag("RFID-66230")).thenReturn(response);

        mockMvc.perform(get("/api/activities/passengers/rfid/RFID-66230"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Đỗ Quốc Bảo"));
    }

    @Test
    @DisplayName("PATCH /api/activities/passengers/{id}/check-in - Cập nhật trạng thái check-in")
    void updateCheckInStatusSuccess() throws Exception {
        PassengerResponse response = PassengerResponse.builder()
                .id(2L)
                .tourId(10L)
                .fullName("Đỗ Quốc Bảo")
                .checkInStatus(CheckInStatus.BOARDED)
                .build();

        when(passengerService.updateCheckInStatus(2L, CheckInStatus.BOARDED)).thenReturn(response);

        mockMvc.perform(patch("/api/activities/passengers/2/check-in?status=BOARDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.checkInStatus").value("BOARDED"));
    }

    @Test
    @DisplayName("PATCH /api/activities/passengers/{id}/registration-status - Cập nhật trạng thái đăng ký")
    void updateRegistrationStatusSuccess() throws Exception {
        PassengerResponse response = PassengerResponse.builder()
                .id(2L)
                .tourId(10L)
                .fullName("Đỗ Quốc Bảo")
                .registrationStatus(com.cruise.activity.enums.RegistrationStatus.CANCELLED)
                .build();

        when(passengerService.updateRegistrationStatus(2L, com.cruise.activity.enums.RegistrationStatus.CANCELLED)).thenReturn(response);

        mockMvc.perform(patch("/api/activities/passengers/2/registration-status?status=CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.registrationStatus").value("CANCELLED"));
    }

    @Test
    @DisplayName("GET /api/activities/passengers/tour/{tourId}/export/csv - Xuất file CSV danh sách hành khách")
    void exportRegisteredPassengersCsvSuccess() throws Exception {
        String csvContent = "\ufeffID,Tour ID,Họ và Tên,Số Cabin\n1,10,Nguyễn Văn Minh,402\n";
        byte[] csvBytes = csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(passengerService.exportRegisteredPassengersCsv(10L, null)).thenReturn(csvBytes);

        mockMvc.perform(get("/api/activities/passengers/tour/10/export/csv"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"passengers_tour_10.csv\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv; charset=UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(csvBytes));
    }

    @Test
    @DisplayName("GET /api/activities/passengers/tour/{tourId}/export/csv?status=CONFIRMED - Xuất file CSV lọc theo status")
    void exportRegisteredPassengersCsvWithStatusSuccess() throws Exception {
        String csvContent = "\ufeffID,Tour ID,Họ và Tên,Số Cabin\n1,10,Nguyễn Văn Minh,402\n";
        byte[] csvBytes = csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(passengerService.exportRegisteredPassengersCsv(10L, com.cruise.activity.enums.RegistrationStatus.CONFIRMED)).thenReturn(csvBytes);

        mockMvc.perform(get("/api/activities/passengers/tour/10/export/csv?status=CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv; charset=UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(csvBytes));
    }
}

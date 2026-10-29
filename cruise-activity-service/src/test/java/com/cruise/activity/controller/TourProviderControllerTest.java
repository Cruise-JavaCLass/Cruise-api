package com.cruise.activity.controller;

import com.cruise.activity.dto.TourProviderResponse;
import com.cruise.activity.enums.TourProviderStatus;
import com.cruise.activity.exception.DuplicateTourProviderException;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.service.TourProviderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TourProviderController.class)
@Import(GlobalExceptionHandler.class)
class TourProviderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TourProviderService tourProviderService;

    @Test
    void createTourProviderRejectsUnauthorizedRole() throws Exception {
        mockMvc.perform(post("/api/v1/tour-providers")
                        .header("X-Caller-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Yêu cầu quyền SHORE_EXCURSION_MANAGER"));

        verify(tourProviderService, never()).createTourProvider(any());
    }

    @Test
    void createTourProviderReturnsCreatedForAuthorizedRoleWithJsonContract() throws Exception {
        TourProviderResponse response = sampleProviderResponse();
        when(tourProviderService.createTourProvider(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/tour-providers")
                        .header("X-Caller-Role", "SHORE_EXCURSION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tạo nhà cung cấp thành công"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.id").value(response.getId().toString()))
                .andExpect(jsonPath("$.data.code").value("TP-TEST"))
                .andExpect(jsonPath("$.data.name").value("Provider One"))
                .andExpect(jsonPath("$.data.taxCode").value("TAX-123"))
                .andExpect(jsonPath("$.data.licenseNumber").value("LIC-123"))
                .andExpect(jsonPath("$.data.contactPerson").value("Contact One"))
                .andExpect(jsonPath("$.data.email").value("contact@example.com"))
                .andExpect(jsonPath("$.data.phone").value("+84123456789"))
                .andExpect(jsonPath("$.data.emergencyHotline").value("+84 123 456 789"))
                .andExpect(jsonPath("$.data.portId").value("VN-HALONG"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
    }

    @Test
    void createTourProviderReturnsBadRequestForInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/tour-providers")
                        .header("X-Caller-Role", "SHORE_EXCURSION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Provider One",
                                  "email": "not-an-email",
                                  "emergencyHotline": "+84 123 456 789",
                                  "portId": "VN-HALONG"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(tourProviderService, never()).createTourProvider(any());
    }

    @Test
    void createTourProviderReturnsBadRequestForBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/tour-providers")
                        .header("X-Caller-Role", "SHORE_EXCURSION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "emergencyHotline": "+84 123 456 789",
                                  "portId": "VN-HALONG"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(tourProviderService, never()).createTourProvider(any());
    }

    @Test
    void updateTourProviderReturnsBadRequestForInvalidEmail() throws Exception {
        mockMvc.perform(put("/api/v1/tour-providers/00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Provider One",
                                  "email": "not-an-email",
                                  "emergencyHotline": "+84 123 456 789",
                                  "portId": "VN-HALONG"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(tourProviderService, never()).updateTourProvider(any(), any());
    }

    @Test
    void updateTourProviderReturnsBadRequestForInvalidHotline() throws Exception {
        mockMvc.perform(put("/api/v1/tour-providers/00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Provider One",
                                  "emergencyHotline": "hotline",
                                  "portId": "VN-HALONG"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(tourProviderService, never()).updateTourProvider(any(), any());
    }

    @Test
    void createTourProviderReturnsConflictForDuplicateIdentifier() throws Exception {
        when(tourProviderService.createTourProvider(any()))
                .thenThrow(new DuplicateTourProviderException("Mã số thuế đã được sử dụng"));

        mockMvc.perform(post("/api/v1/tour-providers")
                        .header("X-Caller-Role", "SHORE_EXCURSION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Provider One",
                                  "taxCode": "TAX-123",
                                  "emergencyHotline": "+84 123 456 789",
                                  "portId": "VN-HALONG"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void getTourProvidersFiltersByPortAndAppliesPagination() throws Exception {
        PageRequest pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        TourProviderResponse response = sampleProviderResponse();
        when(tourProviderService.getTourProviders(pageable, "VN-HALONG"))
                .thenReturn(new PageImpl<>(List.of(response), pageable, 5));

        mockMvc.perform(get("/api/v1/tour-providers")
                        .param("page", "1")
                        .param("size", "2")
                        .param("portId", "VN-HALONG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].portId").value("VN-HALONG"))
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        verify(tourProviderService).getTourProviders(pageable, "VN-HALONG");
    }

    private String validCreatePayload() {
        return """
                {
                  "name": "Provider One",
                  "taxCode": "TAX-123",
                  "licenseNumber": "LIC-123",
                  "contactPerson": "Contact One",
                  "email": "contact@example.com",
                  "phone": "+84123456789",
                  "emergencyHotline": "+84 123 456 789",
                  "portId": "VN-HALONG"
                }
                """;
    }

    private TourProviderResponse sampleProviderResponse() {
        Instant timestamp = Instant.parse("2026-10-29T09:00:00Z");
        return TourProviderResponse.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .code("TP-TEST")
                .name("Provider One")
                .taxCode("TAX-123")
                .licenseNumber("LIC-123")
                .contactPerson("Contact One")
                .email("contact@example.com")
                .phone("+84123456789")
                .emergencyHotline("+84 123 456 789")
                .portId("VN-HALONG")
                .status(TourProviderStatus.ACTIVE)
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .build();
    }
}

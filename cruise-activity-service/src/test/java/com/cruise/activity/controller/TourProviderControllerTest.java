package com.cruise.activity.controller;

import com.cruise.activity.exception.DuplicateTourProviderException;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.service.TourProviderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TourProviderController.class)
@Import(GlobalExceptionHandler.class)
class TourProviderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TourProviderService tourProviderService;

    @Test
    void createTourProviderReturnsBadRequestForInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/tour-providers")
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
}

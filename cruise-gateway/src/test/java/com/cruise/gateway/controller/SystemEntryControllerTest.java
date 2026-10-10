package com.cruise.gateway.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemEntryController.class)
@DisplayName("SystemEntryController MockMvc Tests")
class SystemEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/ping returns pong message")
    void testPingEndpoint() throws Exception {
        mockMvc.perform(get("/api/ping").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("pong"));
    }

    @Test
    @DisplayName("GET /api/info returns project and story metadata")
    void testSystemInfoEndpoint() throws Exception {
        mockMvc.perform(get("/api/info").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectName").value("Cruise Activity and Service Management System"))
                .andExpect(jsonPath("$.data.jiraStory").value("KAN-37"))
                .andExpect(jsonPath("$.data.jiraTask").value("KAN-39"));
    }

    @Test
    @DisplayName("GET /api/health returns UP status and health details")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.memory").exists());
    }

    @Test
    @DisplayName("GET /api/services returns registered services topology")
    void testServicesEndpoint() throws Exception {
        mockMvc.perform(get("/api/services").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cruise-gateway").value("http://localhost:8080"))
                .andExpect(jsonPath("$.data.cruise-auth-service").value("http://localhost:8081"));
    }
}

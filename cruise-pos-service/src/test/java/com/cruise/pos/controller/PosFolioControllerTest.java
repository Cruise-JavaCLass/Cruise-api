package com.cruise.pos.controller;

import com.cruise.pos.dto.FolioAccountResponse;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.service.PosFolioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PosFolioController.class)
@Import(com.cruise.pos.config.PosConfig.class)
@DisplayName("PosFolioController WebMvc Tests")
class PosFolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PosFolioService folioService;

    @MockitoBean
    private com.cruise.pos.service.FolioSettlementService folioSettlementService;

    @Test
    @DisplayName("GET /api/pos/folios/{cabinNumber} returns folio account")
    void testGetFolio() throws Exception {
        FolioAccountResponse response = FolioAccountResponse.builder()
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Nguyễn Văn An")
                .creditLimit(BigDecimal.valueOf(2000.00))
                .currentBalance(BigDecimal.ZERO)
                .availableLimit(BigDecimal.valueOf(2000.00))
                .status(FolioStatus.ACTIVE)
                .build();

        when(folioService.getFolioByCabin("CABIN-7012")).thenReturn(response);

        mockMvc.perform(get("/api/pos/folios/CABIN-7012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cabinNumber").value("CABIN-7012"))
                .andExpect(jsonPath("$.data.primaryGuestName").value("Nguyễn Văn An"));
    }
}

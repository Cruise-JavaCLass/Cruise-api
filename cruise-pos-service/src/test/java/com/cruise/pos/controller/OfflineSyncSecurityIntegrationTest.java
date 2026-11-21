package com.cruise.pos.controller;

import com.cruise.common.enums.Role;
import com.cruise.common.security.AccessTokenVerifier;
import com.cruise.common.security.VerifiedCaller;
import com.cruise.pos.dto.OfflineSyncDtos.ResolutionAction;
import com.cruise.pos.dto.OfflineSyncDtos.ResolveRequest;
import com.cruise.pos.service.OfflineSyncProcessingService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Includes both the shared authorization filter and the enabled POS SecurityFilterChain. */
@SpringBootTest(properties = "pos.security.enabled=true")
@AutoConfigureMockMvc
class OfflineSyncSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean AccessTokenVerifier verifier;
    @MockitoBean OfflineSyncProcessingService service;

    @Test
    void enabledPosSecurityPreservesVerifiedUsernameForAuditInsteadOfPrincipalDescription() throws Exception {
        String username = "finance.reviewer.with.a.long.but.valid.username";
        when(verifier.verify("valid-finance"))
                .thenReturn(Optional.of(new VerifiedCaller(5L, username, Role.FINANCE_FRONT_DESK)));

        mvc.perform(post("/api/v1/pos/sync/conflicts/7/resolve")
                        .header("Authorization", "Bearer valid-finance")
                        .header("X-Caller-Role", "SYSTEM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"reason\":\"Confirmed offline purchase\",\"actor\":\"forged.user\"}"))
                .andExpect(status().isOk());

        verify(service).resolve(7L,
                new ResolveRequest(ResolutionAction.APPROVE, "Confirmed offline purchase"), username);
    }

    @Test
    void enabledPosSecurityStillDeniesCashierConflictResolution() throws Exception {
        when(verifier.verify("valid-cashier"))
                .thenReturn(Optional.of(new VerifiedCaller(6L, "cashier", Role.POS_STAFF)));

        mvc.perform(post("/api/v1/pos/sync/conflicts/7/resolve")
                        .header("Authorization", "Bearer valid-cashier")
                        .header("X-Caller-Role", "FINANCE_FRONT_DESK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"reason\":\"Purchase\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }
}

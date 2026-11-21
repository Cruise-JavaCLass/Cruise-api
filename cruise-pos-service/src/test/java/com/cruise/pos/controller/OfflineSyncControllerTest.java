package com.cruise.pos.controller;
import com.cruise.pos.dto.OfflineSyncDtos.*;
import com.cruise.pos.exception.GlobalExceptionHandler;
import com.cruise.pos.service.OfflineSyncProcessingService;
import com.cruise.common.enums.Role;
import com.cruise.common.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OfflineSyncControllerTest {
    private OfflineSyncProcessingService service;
    private MockMvc mvc;
    @BeforeEach void setup() {
        service=mock(OfflineSyncProcessingService.class);
        AccessTokenVerifier verifier=token->{try{return Optional.of(new VerifiedCaller(1L,"finance.user",Role.valueOf(token)));}catch(IllegalArgumentException error){return Optional.empty();}};
        mvc=MockMvcBuilders.standaloneSetup(new OfflineSyncController(service)).setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new AuthorizationFilter(verifier,new ServiceAccessPolicy())).build();
    }
    @ParameterizedTest @EnumSource(Role.class)
    void conflictDetailsAreRestrictedToFinanceOperationsAndAdmin(Role role) throws Exception {
        boolean allowed=Set.of(Role.FINANCE_FRONT_DESK,Role.OPERATIONS_MANAGER,Role.SYSTEM_ADMIN).contains(role);
        mvc.perform(get("/api/v1/pos/sync/conflicts").header("Authorization","Bearer "+role))
            .andExpect(status().is(allowed?200:403));
        if(allowed) verify(service).getConflicts("PENDING",0,20);else verifyNoInteractions(service);
    }
    @Test void cashierCannotResolveConflictUsingSpoofedRoleOrBodyActor() throws Exception {
        mvc.perform(post("/api/v1/pos/sync/conflicts/1/resolve").header("Authorization","Bearer POS_STAFF")
            .header("X-Caller-Role","FINANCE_FRONT_DESK").contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"APPROVE\",\"reason\":\"confirmed\",\"actor\":\"finance\"}"))
            .andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void resolutionUsesVerifiedPrincipalAndRequiresReason() throws Exception {
        mvc.perform(post("/api/v1/pos/sync/conflicts/1/resolve").header("Authorization","Bearer FINANCE_FRONT_DESK")
            .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\",\"reason\":\"not authorized\"}"))
            .andExpect(status().isOk());
        verify(service).resolve(1L,new ResolveRequest(ResolutionAction.REJECT,"not authorized"),"finance.user");
        mvc.perform(post("/api/v1/pos/sync/conflicts/1/resolve").header("Authorization","Bearer FINANCE_FRONT_DESK")
            .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"APPROVE\",\"reason\":\"\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void batchRequiresHeadersAndNonemptyRecordsAndAnonymousRequestsAreBlocked() throws Exception {
        mvc.perform(post("/api/v1/pos/sync/batch").contentType(MediaType.APPLICATION_JSON).content("{\"transactions\":[]}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/pos/sync/batch").header("Authorization","Bearer POS_STAFF").contentType(MediaType.APPLICATION_JSON).content("{\"transactions\":[]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/pos/sync/batch").header("Authorization","Bearer POS_STAFF").header("X-Terminal-Id","TERM-1")
            .header("X-Batch-Id","BATCH-1").contentType(MediaType.APPLICATION_JSON).content("{\"transactions\":[]}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void heartbeatAndStatusPassTerminalScopeToService() throws Exception {
        mvc.perform(post("/api/v1/pos/sync/heartbeat").header("Authorization","Bearer POS_STAFF").header("X-Terminal-Id","TERM-1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"firmwareVersion\":\"1.0\"}")).andExpect(status().isOk());
        verify(service).heartbeat("TERM-1",new HeartbeatRequest("1.0"));
        mvc.perform(get("/api/v1/pos/sync/batch/7/status").header("Authorization","Bearer POS_STAFF").header("X-Terminal-Id","TERM-1"))
            .andExpect(status().isOk());verify(service).getStatus(7L,"TERM-1");
    }
}
